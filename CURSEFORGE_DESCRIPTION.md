# QShop Confluence Currency Bridge

Connect a QShop currency to the coin system in [Confluence: Otherworld](https://www.curseforge.com/minecraft/mc-mods/confluence). QShop shops, commands, quests, and scripts can all use the same balance, while players carry that money as Confluence coins.

## Features

- **One shared balance:** QShop transactions use the configured Confluence-linked currency. Confluence coins carried by the player are reflected in that balance.
- **Safe overflow:** Coins that do not fit in the inventory or Confluence coin slots remain in the QShop wallet and are converted to physical coins when space is available.
- **Inventory synchronization:** Picking up, dropping, using, or moving coins through containers updates the linked balance.
- **Offline balance support:** QShop wallet changes are saved and reconciled when the player returns.
- **Sell Box integration (optional):** Show linked-currency prices and sale messages using Confluence denominations and colors. Stack prices are calculated for the full item stack. Players can also sell Sell Box-priced items to Confluence NPCs.
- **Piggy Bank support:** Choose whether the Confluence Piggy Bank contributes to the linked balance (enabled by default).

## Requirements

- **Minecraft 1.20.1 + Forge** or **Minecraft 1.21.1 + NeoForge**
- **QShop 1.8.0 or newer** — required
- **Confluence: Otherworld 1.2.0 or newer** — required for the currency bridge. The addon can load without Confluence, but its bridge features remain inactive.
- **MesdagPortLib 1.2.7 or newer** — required by Confluence 1.2.8 (it bundles TerraCurio 1.3.2).
- **QShop Sell Box 1.5.0 or newer** — optional; install it with Confluence for Sell Box price formatting, sale messages, and selling Sell Box-priced items to Confluence NPCs.

The shared QShop-to-Confluence balance, physical coin synchronization, Piggy Bank support, and offline reconciliation require both QShop and Confluence. Without Confluence, QShop continues to work normally and this addon does not convert QShop balances to Confluence coins.

## Setup

Set `bridge.currencyId` in `config/qshop-confluence-common.toml` to the QShop currency ID used by your shops and Sell Box price rules. The default is `coins`. The bridge creates the currency entry automatically if it does not already exist.

`bridge.includePiggyBank` controls whether Piggy Bank money is included (default: `true`). `sellbox.confluencePriceFormat` controls Confluence-style Sell Box price display (default: `true`).

Operators can use `/qshopconfluence status` to check the bridge and their balance, or `/qshopconfluence set <amount>` to set their linked balance.

## Balance notes

- The configured QShop currency is the shared balance. Confluence coins are its physical, carryable portion; value that cannot fit remains in the QShop wallet.
- Confluence coins still follow Confluence's death-drop behavior. By default, the bridge skips QShop's separate death-currency deduction for the linked currency to avoid charging twice.
- On death, only the coins the player is physically carrying are dropped. The part of the balance that does not fit the coin slots stays in the QShop wallet and is turned back into physical coins once the player is alive again with free coin slots; a dead or downed player is never handed new coins.
- QShop supports decimal prices. Any amount smaller than one copper coin remains as a fractional balance in the QShop wallet.

License: **ARR — All Rights Reserved**.

---

# QShop Confluence Currency Bridge（简体中文）

将一个 QShop 货币绑定到 [Confluence: Otherworld](https://www.curseforge.com/minecraft/mc-mods/confluence) 的钱币系统。QShop 商店、指令、任务和脚本可以共用同一余额，玩家则以 Confluence 钱币的形式携带其中一部分。

## 功能

- **共用余额：** QShop 交易使用已绑定的货币；玩家身上的 Confluence 钱币也计入该余额。
- **安全处理溢出：** 背包或 Confluence 钱币栏装不下的钱保留在 QShop 钱包中，有空间时再转换为实体钱币。
- **同步钱币变动：** 拾取、丢弃、使用钱币或通过容器移动钱币时，绑定余额会同步更新。
- **支持离线余额：** QShop 钱包变化会保存下来，并在玩家重新加入时对账同步。
- **出售箱联动（可选）：** 绑定货币的价格和出售消息使用 Confluence 的面额与颜色；价格按整组物品计算。玩家也可以向 Confluence NPC 出售由出售箱定价的物品。
- **支持存钱罐：** 可设置是否将 Confluence 存钱罐余额计入绑定余额（默认开启）。

## 依赖

- **Minecraft 1.20.1 + Forge** 或 **Minecraft 1.21.1 + NeoForge**
- **QShop 1.8.0 或更新版本** — 必需
- **Confluence: Otherworld 1.2.0 或更新版本** — 货币桥接功能必需。未安装时模组仍可加载，但桥接功能不会启用。
- **MesdagPortLib 1.2.7 或更新版本** — Confluence 1.2.8（内嵌 TerraCurio 1.3.2）需要它。
- **QShop Sell Box 1.5.0 或更新版本** — 可选；安装 Confluence 和 Sell Box 后，才能使用出售箱价格格式、出售提示格式，以及向 Confluence NPC 出售出售箱定价物品的功能。

QShop 与 Confluence 都安装时，才能使用共用余额、实体钱币同步、存钱罐支持和离线余额对账。缺少 Confluence 时，QShop 仍可正常运行，但本模组不会把 QShop 余额转换为 Confluence 钱币。

## 配置

在 `config/qshop-confluence-common.toml` 中，将 `bridge.currencyId` 设为商店和出售箱价格规则使用的 QShop 货币 ID。默认值是 `coins`；如果货币条目不存在，桥接会自动创建。

`bridge.includePiggyBank` 控制是否计入存钱罐余额（默认：`true`）。`sellbox.confluencePriceFormat` 控制出售箱是否使用 Confluence 的价格格式（默认：`true`）。

管理员可在游戏中使用 `/qshopconfluence status` 查看桥接状态和自己的余额，或使用 `/qshopconfluence set <amount>` 设置自己的绑定余额。

## 余额说明

- 已绑定的 QShop 货币是共用余额；Confluence 钱币是其中可随身携带的部分，放不进背包或钱币栏的金额保留在 QShop 钱包中。
- Confluence 钱币仍按 Confluence 的规则掉落。默认情况下，桥接会跳过 QShop 对绑定货币的死亡扣除，避免同一笔钱被重复扣除。
- 死亡时只掉落玩家身上那部分实体钱币。装不进钱币栏的余额保留在 QShop 钱包中，玩家恢复存活且有空闲钱币栏后会重新转换成实体钱币；处于死亡或倒地状态的玩家不会被发放新钱币。
- QShop 价格可以包含小数；不足一枚铜币的部分会作为小数余额保存在 QShop 钱包中。

许可证：**ARR（保留所有权利）**。
