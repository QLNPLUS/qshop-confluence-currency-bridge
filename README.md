# QShop Confluence Currency Bridge

把一个 [QShop](https://github.com/QLNPLUS/Q-shop) 货币 ID 连接到 [Confluence: Otherworld](https://www.curseforge.com/minecraft/mc-mods/confluence) 的钱币系统。

## 功能

- **统一余额**：QShop 交易、指令、任务和脚本读写同一个余额。玩家携带的钱币计入余额，存钱罐是否计入可配置。
- **溢出保存在 QShop**：背包和 Confluence 钱币栏装不下时，剩余金额保存在 QShop 钱包；有空间后会重新以钱币形式放回玩家身上。
- **手动移动自动同步**：玩家拾取、丢弃、使用或在容器中移动钱币时同步余额，并用低频检查兜底。
- **离线余额同步**：QShop 的离线交易保存在玩家的钱包数据中；玩家再次加入时，与身上的 Confluence 钱币对账。
- **出售箱格式**：绑定货币的价格及出售提示使用 Confluence 的面额、颜色和文案格式，包含“卖出：”前缀，并按物品堆叠数量计算总价。
- **NPC 出售**：玩家可向 Confluence NPC 出售由 QShop Sell Box 定价、且货币为绑定 ID 的物品。

本模组不修改 QShop 源码，通过 QShop 钱包入口接入余额读写。

## 版本和依赖

| Minecraft | 加载器 | QShop | Confluence: Otherworld | QShop Sell Box |
|---|---|---|---|---|
| 1.20.1 | Forge | 1.8.0 或更新 | 1.2.0 或更新 | 1.5.0 或更新，可选 |
| 1.21.1 | NeoForge | 1.8.0 或更新 | 1.2.0 或更新 | 1.5.0 或更新，可选 |

QShop 是必需依赖。Confluence 缺失时桥接功能不启用；Sell Box 缺失时出售箱价格显示和 NPC 定价出售功能不启用。

## 配置和使用

在 `config/qshop-confluence-common.toml` 中设置 `bridge.currencyId`，使它与商店商品、出售箱价格规则使用的 QShop 货币 ID 一致。默认 ID 是 `coins`。`bridge.includePiggyBank` 控制是否把存钱罐余额纳入总余额；`sellbox.confluencePriceFormat` 控制出售箱的 Confluence 价格格式。

如果货币表中没有该 ID，默认配置会自动创建条目。可使用 `/qshopconfluence status` 查看绑定状态和余额；管理员可用 `/qshopconfluence set <数量>` 设置余额。

## 说明

- 绑定货币的总额由 QShop 钱包保存，Confluence 钱币是可携带部分；请勿把同一个货币 ID 同时当作独立的普通 QShop 货币使用。
- Confluence 钱币会参与自身死亡掉落。为了避免 QShop 再次扣款，桥接货币默认忽略 QShop 的死亡扣币设置。
- QShop 价格允许小数，Confluence 钱币以整铜币表示；不足一铜币的余量保留在 QShop 钱包中。
- NeoForge 1.21.1 与 Forge 1.20.1 分别维护在同名版本分支中。

## 许可证

ARR（All Rights Reserved，保留所有权利）。
