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

| Project | Property | Default path (this worktree) |
|---|---|---|
| QShop | `qshop_jar` | `../../q_shop/neoforge-1.21.1/build/libs/qshop-neoforge-1.21.1-<version>.jar` |
| QShop Sell Box | `sellbox_jar` | `../../q_shop_sellbox/neoforge-1.21.1/build/libs/...` |

This worktree sits one level below the project root, so the sibling paths use `../../`
(the `forge-1.20.1` worktree, which owns `.git`, uses `../`).

NeoForge 1.21.1 mod jars already use Mojang official names, so they are consumed with plain
`files(...)` — no `fg.deobf`, no flatDir staging, no `libs/` copy step. The Forge branch needs
all three because ForgeGradle cannot deobfuscate a `files(...)` dependency.
Confluence itself comes from CurseForge Maven (`curse.maven:confluence-1209464:8940190`).

## Verification

- `gradlew build` — compiles and produces the release JAR.
- `gradlew runServer -Pwith_confluence_runtime=true` — dev server with QShop, Sell Box,
  Confluence, Curios and GeckoLib on the classpath. Confluence's NeoForge build does **not**
  need MesdagPortLib, so this branch has no `tools/fetch-dev-runtime.ps1`.
- Release-JAR checks that a compile cannot prove: `MixinConfigs` in `META-INF/MANIFEST.MF`,
  `pack.mcmeta` at the archive root, and `META-INF/neoforge.mods.toml` as the only mod metadata
  file at the archive root.
- No refmap on this branch: NeoForge 1.21.1 runs official names, so the two
  `Component#translatable` redirects use `remap = false` and the mixin config declares no refmap.
  The `forge-1.20.1` branch needs `patchRefmap` for exactly those two entries.
