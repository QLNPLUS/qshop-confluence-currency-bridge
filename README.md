# QShop Confluence Currency Bridge

把 [QShop](https://github.com/) 的一个货币 id 绑定到 [Confluence: Otherworld](https://www.curseforge.com/minecraft/mc-mods/confluence)
的钱币系统上。绑定之后：

- **实时读取**：QShop 读该货币余额时，直接返回玩家身上 Confluence 的钱
  （背包钱币 + 钱币栏 + 存钱罐，存钱罐可用配置关掉）；
- **实时写回**：QShop 交易扣款/进账直接增删玩家身上的钱币，不和 QShop 钱包做镜像，
  所以不存在"两边对不上"的中间态；
- **价格格式**：出售箱显示该货币的价格时改用 Confluence 面额文本
  （`1234567` → `1 铂金币 23 金币 45 银币 67 铜币`），出售/补发的提示消息同样处理。

本模组**不修改 QShop 源码**，通过 Mixin 接管 QShop 唯一的钱包入口
`WalletCapability.get(Player)`，因此交易、指令、FTB 任务、KubeJS、商店界面同步
这些路径全部自动生效。

## 环境要求

| 组件 | 要求 | 说明 |
|---|---|---|
| Minecraft / 加载器 | Forge 1.20.1 或 NeoForge 1.21.1 | 两个分支各自独立 |
| QShop | ≥ 1.8.0 | 硬依赖 |
| Confluence: Otherworld | ≥ 1.2.0 | 缺失时模组仍能加载，但绑定不生效 |
| QShop Sell Box | ≥ 1.5.0 | 可选；缺失时只有价格格式功能关闭 |

## 配置

`config/qshop-confluence-common.toml`：

| 键 | 默认 | 说明 |
|---|---|---|
| `bridge.currencyId` | `coins` | 绑定到 Confluence 钱币的 QShop 货币 id |
| `bridge.includePiggyBank` | `true` | 余额是否包含存钱罐里的钱 |
| `bridge.offlinePayout` | `true` | 玩家离线时记账，登录时以钱币补发 |
| `bridge.skipDeathRetention` | `true` | 忽略 QShop 死亡扣款对该货币的作用 |
| `bridge.autoCreateCurrency` | `true` | 货币表里没有该 id 时自动创建条目 |
| `bridge.autoCreateCurrencyName` | `钱币` | 自动创建时使用的显示名 |
| `sellbox.confluencePriceFormat` | `true` | 出售箱用 Confluence 面额显示价格 |

## 使用

1. 把 `currencyId` 设成你想绑定的货币 id（默认 `coins`）。
2. 在商店编辑界面里，把商品的价格货币选成同一个 id。
3. 出售箱的 `defaultCurrency` / 价格规则的货币字段填同一个 id。

自检：`/qshopconfluence status` 会同时打印 Confluence 身上的钱和 QShop 读到的余额，
两者一致即绑定生效。`/qshopconfluence set <数量>`（需要 OP）可以直接设定余额。

## 已知边界

- **Confluence 的钱是物品，不是钱包数据。** 玩家离线时没有实体可以增删钱币，
  所以离线入账（例如出售箱的离线收益）会先记在服务器账面里，登录时再以钱币补发；
  离线期间 `CurrencyService.getBalance(server, uuid, id)` 读到的也只是这份账面。
- **死亡扣款默认被忽略。** Confluence 自己会让玩家死亡掉落钱币，
  如果 QShop 的 `death.loseCurrencyOnDeath` 同时生效就会扣两次，
  所以绑定货币默认跳过 QShop 的死亡留存规则（`skipDeathRetention=false` 可改回）。
- **价格是 double，Confluence 只能整铜币。** 整数部分落到钱币上，
  `[0,1)` 的小数余量存在 QShop 钱包里继续累积，不会因为向下取整而丢失。
- 只有**一个**货币 id 能被绑定；绑定货币 id 不要同时再当普通 QShop 钱包货币用。

## 构建

```powershell
# 先构建兄弟项目（neoforge 1.21.1 分支）
cd ..\..\q_shop\neoforge-1.21.1          ; .\gradlew.bat build
cd ..\..\q_shop_sellbox\neoforge-1.21.1  ; .\gradlew.bat build

# 再构建本模组（当前目录就是 neoforge-1.21.1 工作树）
.\gradlew.bat build
```

冒烟测试（带 Confluence 全套前置的开发服务器）：

```powershell
.\gradlew.bat runServer -Pwith_confluence_runtime=true
```

NeoForge 1.21.1 的 Confluence 不需要 MesdagPortLib，所以本分支没有下载脚本；
开发服务器默认端口写在 `run/server.properties` 里（25577），避免和别的开发服务器抢端口。

## 许可

ARR（保留所有权利）。
