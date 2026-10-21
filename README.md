# Carpet Mino Addition

一个基于 **Carpet Extension** 体系的 Minecraft 模组（Minecraft 26.2 / Fabric），
提供几条实用的生存向规则：生物掉落倍率、流浪商人卖冰、村民交易增强、流浪商人常驻。

## 版本矩阵

| 组件 | 版本 | 来源 / 说明 |
| --- | --- | --- |
| Minecraft | 26.2 | `gradle.properties: minecraft_version` |
| Fabric Loader | 0.19.5 | 前置平台 |
| Fabric API | 0.161.0+26.2 | 必需：语言文件依赖它的资源加载 |
| Carpet | 26.2+v260616 | 前置模组，`https://masa.dy.fi/maven` |
| Fabric Loom | 1.18.4 | **`net.fabricmc.fabric-loom`**（不带 `-remap`，见下） |
| Gradle | 9.7.1 | Wrapper 自动下载 |
| JDK | 25 | Gradle 守护进程与编译目标都是 25 |

## 规则一览

三条规则都在 `Mino` 分类下，用 `/carpet <规则名> ...` 设置。

| 规则名 | 默认 | 作用 |
| --- | --- | --- |
| `mobDropMultiplier` | `""` | 生物掉落倍率，语法见下 |
| `wanderingTraderSellsIce` | `false` | 流浪商人卖冰。加入浮冰所在候选池，约 12.5% 概率出现，1 绿宝石换 8 个 |
| `villagerTradePro` | `false` | 村民交易增强，详见下表 |
| `wanderingTraderDespawnSeconds` | `0` | 流浪商人停留秒数。`0` 原版 40 分钟，`-1` 永不消失，`1-604800` 指定秒数 |

`mobDropMultiplier` 的写法：

| 写法 | 含义 |
| --- | --- |
| `3` 或 `all=3` | 全局 3 倍 |
| `cow=10` | 只有牛 10 倍，其余原版 |
| `3,cow=10` | 全局 3 倍，牛单独 10 倍 |
| 留空 | 全部原版 |

倍率范围 1-1999。生物 id 可省略 `minecraft:` 前缀，模组生物要写全命名空间。
也可以不用记语法，用命令设置：

```mcfunction
/mino drop <生物> <倍率>
/mino drop all <倍率>
/mino drop list
/mino drop clear
```

`villagerTradePro` 覆盖的职业与概率（单件概率 / 每次召唤实际出货率）：

| 职业 | 等级 | 物品 | 单件概率 | 平均召唤次数 |
| --- | --- | --- | --- | --- |
| 工具商 | 4/5 | 钻石斧、铲、镐 | 15% | 6.7 |
| 武器匠 | 4/5 | 钻石斧、剑 | 10% | 10.0 |
| 盔甲商 | 4/5 | 钻石护腿、靴、盔、胸甲 | 8% | 6.5 |
| 制箭师 | 4 | 弓 | 12% | 8.3 |
| 制箭师 | 5 | 弩 | 12% | 12.5（该级 3 候选抽 2 条，弩只有 2/3 概率被抽中） |
| 农民 | 5 | 附魔金苹果 | 10%（再乘随机条件） | 约 6.7% 出货，64 绿宝石 1 个 |

满级附魔是 2-3 条互不冲突的组合，按 75% / 25% 加权（倾向 3 条）。
具体池子见 `VillagerTradesMixin` 的类注释。

> **注意**：26.2 的 `updateTrades` 只生成**当前等级**那一组交易，不累加低等级。
> 所以 `/summon ... level:5` 的村民只有 5 级商品；自然升到满级的村民才有全部等级的交易。

## 目录结构

```
Carpet-Mino-Addition-26.2/
├─ build.gradle              构建脚本：Loom、Carpet 仓库、依赖
├─ settings.gradle           工程名 carpet-mino-addition
├─ gradle.properties         版本号、JDK 路径、代理、依赖版本（改版本只改这里）
├─ src/main/java/carpet/mino/
│   ├─ CarpetMinoAddition.java      主入口：向 Carpet 注册扩展
│   ├─ MinoExtension.java           CarpetExtension 实现（规则注册 / 命令 / 翻译 / 生命周期）
│   ├─ settings/
│   │   ├─ MinoSettings.java        规则定义（@Rule 字段）
│   │   ├─ MobDropSpec.java         倍率规则的解析 / 渲染（不可变值对象）
│   │   ├─ MobDropMultipliers.java  运行期查询入口（带解析缓存）
│   │   ├─ validator/MobDropMultiplierValidator.java   掉落倍率校验器
│   │   └─ validator/WanderingTraderDespawnValidator.java  商人延迟校验器
│   ├─ command/MinoCommand.java     /mino 命令树
│   └─ mixin/
│       ├─ LivingEntityMixin.java     生物掉落倍率 + 堆叠合并
│       ├─ TradePoolMixin.java        往交易池塞自定义交易（冰、附魔金苹果）
│       ├─ WanderingTraderDespawnMixin.java  流浪商人消失延迟
│       └─ VillagerTradesMixin.java   村民交易 Pro（钻石装备 / 弓弩的满级附魔）
├─ src/main/resources/
│   ├─ fabric.mod.json              mod 元数据（id: carpet-mino-addition）
│   ├─ carpet-mino-addition.mixins.json
│   ├─ icon.png                     jar 根目录图标
│   ├─ assets/carpet-mino-addition/ 图标、语言文件
│   └─ data/carpet-mino-addition/villager_trade/
│       ├─ wandering_trader/emerald_ice.json              流浪商人的冰
│       └─ farmer/5/emerald_enchanted_golden_apple.json   农民的附魔金苹果
├─ src/client/java/carpet/mino/client/
│   ├─ CarpetMinoAdditionClient.java    客户端唯一入口
│   ├─ feature/no_hunger_drain/         飞行不掉饱食度
│   └─ mixin/no_hunger_drain/PlayerMixin.java
└─ src/client/resources/carpet-mino-addition.client.mixins.json
```

## Carpet 扩展的接入点

Carpet Extension 不自己造命令树，规则统一挂在 `/carpet` 下：

1. **注册**：`CarpetMinoAddition.onInitialize()` 里 `CarpetServer.manageExtension(EXTENSION)`。
2. **规则**：在 `MinoSettings` 里写 `public static` 字段 + `@Rule` 注解，字段名即规则名；
   注册动作放在 `MinoExtension.onGameStarted()` 里调用 `CarpetServer.settingsManager.parseSettingsClass(...)`。
   修改后通过 `/carpet <规则名> ...` 生效。

   > **注册时机不能挪到静态块。** `parseSettingsClass` 内部会调 `Translations.updateLanguage()`，
   > 它遍历 `CarpetServer.extensions` 收集翻译；静态块执行时本扩展还没进那个列表，
   > 收集不到自己的翻译键，Carpet 直接抛
   > `NullPointerException: No language key provided`。`onGameStarted()` 是 Carpet 遍历
   > 扩展列表之后才回调的，时机正确。
3. **命令**：`MinoExtension.registerCommands(dispatcher, context)` 里注册本扩展命令（如 `/mino ...`）。
4. **生命周期**：`onGameStarted` / `onServerLoaded` / `onServerLoadedWorlds` / `onTick` /
   `onServerClosed` / `onReload` 按需覆盖。
5. **翻译**：规则名与描述走 `assets/carpet-mino-addition/lang/zh_cn.json`、`en_us.json`，
   键用**绝对形式** `carpet.rule.<规则名>.name` / `.desc`、`carpet.category.<类别>`。
   用相对形式（`rule.xxx`）也会被 Carpet 改写成功，但和绝对键混用后难以判断最终命名空间，故统一用绝对键。

## 构建

```powershell
.\gradlew.bat build        # 编译 + 打包 → build\libs\carpet-mino-addition-<version>.jar
.\gradlew.bat runClient    # 启动带本模组的开发客户端（需要同时装 Carpet 与 Fabric API）
.\gradlew.bat runServer
```

产物是普通 Fabric 模组 jar，丢进 `mods/` 即可；运行环境必须同时有 **Carpet**、**Fabric API**
（`fabric.mod.json` 的 `depends` 已声明）。

### 本机专属配置

仓库里的 `gradle.properties` 只放可移植的配置。下面这些跟机器绑定，**不要提交**，
放到 `%USERPROFILE%\.gradle\gradle.properties`（Gradle 会自动读取，项目内同名项优先）：

```properties
# 指定 JDK 位置。Gradle toolchain 自动探测扫不到自定义安装路径时才需要。
# Fabric Loom 1.18+ 要求守护进程跑在 Java 25+。
org.gradle.java.home=<JDK 25 的路径>
org.gradle.java.installations.paths=<JDK 21 的路径>,<JDK 25 的路径>

# 本机代理（可选）。Fabric maven 直连可能被限速，走代理后正常。
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=<代理端口>
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=<代理端口>
systemProp.http.nonProxyHosts=localhost|127.0.0.1
```

代理关掉时把 `systemProp.*` 那几行注释掉即可直连，只是慢。

## 26.x 的构建差异

26.x 起 Mojang 不再发布混淆映射，游戏 jar 本身就是官方名
（`minecraft-merged.jar` 里直接是 `net/minecraft/world/item/ItemStack.class`）。
因此构建配置和 1.21.x 有几处必须的差别：

| 项目 | 1.21.x | 26.x |
| --- | --- | --- |
| Loom 插件 | `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` |
| `mappings` 依赖 | `loom.officialMojangMappings()` | **不声明** |
| 依赖配置 | `modImplementation` | `implementation` |
| access widener | 需要 | 不需要 |
| `compatibilityLevel` | `JAVA_21` | `JAVA_25` |
| 编译目标 | Java 21 | Java 25 |

踩过的坑：

- 用 `-remap` 插件会报 `Failed to find official mojang mappings for 26.2`
- 声明 `loom.layered { }` 空层会报 `NullPointerException: srcNamespace is null`
- 完全不写 `mappings` 会报 `Configuration 'mappings' has no dependencies`
- 正确做法就是照 [fabric-example-mod 的 26.2 分支](https://github.com/FabricMC/fabric-example-mod/tree/26.2) 配

## 换 Minecraft 版本

1. 打开 <https://fabricmc.net/develop> 查目标版本的 loader / fabric-api；
   Carpet 去 <https://masa.dy.fi/maven/carpet/fabric-carpet/> 找对应版本号。
2. 改 `gradle.properties` 的 `minecraft_version`、`loader_version`、`fabric_api_version`、`carpet_version`。
3. 同步 `fabric.mod.json` 的 `depends`（`minecraft`、`fabricloader`、`java`、`carpet`）。
4. **跨大版本时还要改构建配置**：从 1.21.x 升到 26.x 要按上面「26.x 的构建差异」换插件、
   去掉 `mappings`、改依赖配置与 Java 版本。
5. `.\gradlew.bat --refresh-dependencies build` 验证。

## License

MIT，见 [LICENSE](LICENSE)。
