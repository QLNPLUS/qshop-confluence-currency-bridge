# AGENTS.md — QShop Confluence Currency Bridge

## Branch Matrix

| Branch | Worktree | Loader | Minecraft | JDK | Gradle / plugin |
|---|---|---|---|---|---|
| `forge-1.20.1` (primary) | `D:\projects\confluence_currency_combat` | Forge | 1.20.1 | 17 | Gradle 8.1.1 / ForgeGradle 6.0.54 |
| `neoforge-1.21.1` | `D:\projects\confluence_currency_combat\neoforge-1.21.1` | NeoForge | 1.21.1 | 21 | Gradle 8.12 / ModDevGradle 2.0.141 |

The primary branch is `forge-1.20.1`. Develop and verify there first, then propagate
loader-independent changes with `git cherry-pick -x`. Never hand-copy a change to the other
branch: the conflict is the finding.

Release tags use `v<mod-version>-<loader>-<minecraft-version>`, for example
`v1.0.0-forge-1.20.1` and `v1.0.0-neoforge-1.21.1`.

Known platform gaps (re-check, do not copy blindly):

- Config API: Forge `net.minecraftforge.common.ForgeConfigSpec` vs NeoForge
  `net.neoforged.neoforge.common.ModConfigSpec`; NeoForge registers config through
  `ModContainer#registerConfig`.
- Event bus: `MinecraftForge.EVENT_BUS` vs `NeoForge.EVENT_BUS`, and the event package roots
  differ (`net.minecraftforge.event.*` vs `net.neoforged.neoforge.event.*`).
- `SavedData`: Forge `save(CompoundTag)` vs NeoForge `save(CompoundTag, HolderLookup.Provider)`.
- Mixin refmap: Forge 1.20.1 production runs SRG names, so the two `Component#translatable`
  redirects need refmap entries (`patchRefmap` Gradle task). NeoForge 1.21.1 runs Mojang
  official names, so the NeoForge branch needs no refmap and no `patchRefmap`.
- QShop wallet storage: Forge uses a capability (`WalletCapability.Provider`), NeoForge uses a
  player attachment. `WalletCapability.get(Player)` exists on both and is the only entry point
  the bridge relies on.

## Sibling Dependencies

This mod is an addon: it has no upstream repository of its own for QShop, so it compiles
against the sibling projects' build outputs.

| Project | Property | Default path |
|---|---|---|
| QShop | `qshop_jar` | `../q_shop/<loader>/build/libs/qshop-<loader>-<mc>-<version>.jar` |
| QShop Sell Box | `sellbox_jar` | `../q_shop_sellbox/<loader>/build/libs/...` |

`build.gradle` stages those jars into `libs/` (flatDir) because ForgeGradle's `fg.deobf()`
cannot deobfuscate a `files(...)` dependency. Build the sibling projects before this one.
Confluence itself comes from CurseForge Maven (`curse.maven:confluence-1209464:<fileId>`).
The current pin is Confluence 1.2.8 (fileId `9099721`). Its bundled TerraCurio 1.3.2 requires
MesdagPortLib >= 1.2.7, so the dev runtime uses `portlib_version` from `gradle.properties`.

PortLib has two mutually exclusive builds for 1.20.1: the CurseForge/backport files use the
`org.mesdag.portlib` package (this is what Confluence 1.2.8 for 1.20.1 references — 0 references
to `com.mesdag`), while `com.mesdag.portlib:portlib_forge` on `maven.confluence.ink` uses the
renamed `com.mesdag.portlib` package. Mixing them produces
`NoClassDefFoundError com/mesdag/portlib/curios/TerraCurioConfig` at mod scan time. Check the
package prefix of both jars before blaming a version bump.

## Verification

- `gradlew build` — compiles and produces the release JAR.
- `gradlew runGameTestServer -Pwith_confluence_runtime=true --offline` — runs the functional
  GameTests in `src/main/java/com/qshop/confluence/gametest/` (they are excluded from the release
  jar). Add `-Dnet.minecraftforge.gradle.check.certs=false` when ForgeGradle's certificate check
  fails, and grep the log for `GameTest` / `required tests` to read the results.
- `gradlew runServer -Pwith_confluence_runtime=true` — dev server with QShop, Sell Box,
  Confluence, Curios, GeckoLib and MesdagPortLib on the classpath. `tools/fetch-dev-runtime.ps1`
  downloads the one prerequisite that has no Maven coordinates.
- Release-JAR checks that a compile cannot prove: `MixinConfigs` in `META-INF/MANIFEST.MF`,
  `pack.mcmeta` at the archive root, and non-empty refmap entries for the two
  `Component#translatable` redirects (Forge branch only).

Death ordering that the tests pin down: Confluence 1.2.8 registers its `LivingDeathEvent` coin drop
through PortLib at `NORMAL`, but **1.2.11 registers `dropMoneyOnDeath` at `LOWEST`** (same priority the
bridge used to reconcile at, so listener order decided whether the drop was seen). The bridge reconciles
at `MONITOR` — the last bus priority — so it always runs after any drop, for either version. A death
cancelled at `HIGHEST` (PlayerRevive's bleeding state) drops nothing and must not move the wallet total.
QShop applies its own death retention inside `PlayerEvent.Clone`, never on `LivingDeathEvent`, so a
cancelled death never respawns and never retains. See `BridgeDownedDeathGameTests`.

Running GameTests against a live instance's own jars (the only way to catch version drift like the
`LOWEST` registration above):
`./gradlew runGameTestServer -Pwith_confluence_runtime=true -Pconfluence_jar=local:confluence:<ver>
-Pportlib_version=<ver> "-Pqshop_jar=<path>/qshop-forge-1.20.1-<ver>.jar" "-Psellbox_jar=<path>/..."`
after copying Confluence and PortLib into `libs/confluence-<ver>.jar` / `libs/mesdagportlib-<ver>.jar`
(flatDir). Restore `libs/qshop-1.0.jar` and `libs/qshop_sellbox-1.0.jar` afterwards, or the next default
build silently keeps using the instance's versions.

Ledger integrity that the death sync depends on: the bridge stores its cash/piggy mirrors and its
revision under internal QShop keys (`BridgeWalletData`). QShop's death retention iterates **every**
key of the wallet and rescales it, so `CurrencyServiceMixin#qshop_confluence$skipDeathRetention` must
skip both the bound currency id **and** `BridgeWalletData.isInternalKey(...)` — a scaled mirror makes
the next read treat the difference as picked-up cash, credit it again (often doubling the total) and
then materialise the phantom back into the inventory, i.e. the drop is duplicated. `BridgeWalletData`
VERSION re-anchors the mirrors on load when a wallet was written by an older revision, and the
rebalance of a dying player is suppressed (`rebalanceCash` returns while `isDeadOrDying`). Bump VERSION
whenever a ledger semantic changes; a same-named jar silently keeps damaged wallets damaged.

Physical coins live **only** in Confluence's coin slots (`ExtraInventory#getCoins`, `SIZE_COINS` = 4).
Never write bridge coins into the ordinary inventory: inventory items are dropped by vanilla death loot
regardless of Confluence's `playerDropsMoney`, which is exactly how a death used to drop money that the
shop still counted. Overflow stays in the wallet as reserve. `ConfluenceSupport.playerDropsMoney()` reads
that setting (default `true`), and `BridgeEvents#onPlayerDrops` puts any coin stack it can see in the drops
back on the player, so `false` really means "nothing on the ground".
