# QShop Confluence Currency Bridge

## English

Connect one QShop currency ID to the coin system in **Confluence: Otherworld**. QShop purchases, sales, commands, quests, and scripts all use the same balance.

### Features

- Keep the coins a player can carry as physical Confluence coin items. Any amount that does not fit in the inventory or Confluence coin slots stays safely in the QShop wallet and is returned as physical coins when space becomes available.
- Automatically synchronize coins that players pick up, drop, use, or move through containers.
- Keep QShop offline transactions in the player's saved wallet data and reconcile the balance when they join again.
- Display linked-currency Sell Box prices and sale messages with Confluence's denomination names, colors, and wording, including the **“Sell:”** prefix. Prices account for the item's stack size.
- Let players sell Sell Box-priced items to Confluence NPCs when the price uses the linked QShop currency.
- Optionally include money stored in a Confluence Piggy Bank in the linked balance.

### Requirements

- **QShop** 1.8.0 or newer (required)
- **Confluence: Otherworld** 1.2.0 or newer
- **QShop Sell Box** 1.5.0 or newer (optional; required for Sell Box formatting and NPC sales)
- Minecraft **1.20.1 Forge** or **1.21.1 NeoForge**

Set `bridge.currencyId` in `config/qshop-confluence-common.toml` to the QShop currency ID used by your shops and Sell Box price rules. The default is `coins`.

License: **ARR — All Rights Reserved**.

## 简体中文

将一个 QShop 货币 ID 连接到 **Confluence: Otherworld** 的钱币系统。QShop 商店、出售箱、指令、任务和脚本共用同一余额。

### 功能

- 玩家能携带的部分以 Confluence 钱币物品显示；背包和钱币栏装不下的金额保存在 QShop 钱包，有空间时再兑换为钱币。
- 自动同步玩家拾取、丢弃、使用或在容器中移动的钱币。
- QShop 离线交易保存在玩家钱包数据中，玩家重新加入时同步余额。
- 出售箱价格和出售消息使用 Confluence 的面额名称、颜色与格式，包含“卖出：”前缀，并按物品堆叠数量计算总价。
- 玩家可以向 Confluence NPC 出售由出售箱定价、且货币为绑定 ID 的物品。
- 可配置是否把 Confluence 存钱罐中的余额计入绑定货币。

### 依赖

- **QShop** 1.8.0 或更新（必需）
- **Confluence: Otherworld** 1.2.0 或更新
- **QShop Sell Box** 1.5.0 或更新（可选；出售箱格式和 NPC 出售功能需要）
- Minecraft **1.20.1 Forge** 或 **1.21.1 NeoForge**

在 `config/qshop-confluence-common.toml` 中把 `bridge.currencyId` 设为商店和出售箱价格规则使用的 QShop 货币 ID，默认值为 `coins`。

许可证：**ARR（保留所有权利）**。
