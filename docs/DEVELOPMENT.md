# 开发说明

面向改代码的人。普通使用看 [README](../README.md) 就行。

## 目录结构

```
src/main/java/carpet/mino/
├─ CarpetMinoAddition.java      主入口：向 Carpet 注册扩展
├─ MinoExtension.java           CarpetExtension 实现（规则注册 / 命令 / 翻译 / 生命周期）
├─ settings/
│   ├─ MinoSettings.java        规则定义（@Rule 字段）
│   ├─ MobDropSpec.java         倍率规则的解析 / 渲染（不可变值对象）
│   ├─ MobDropMultipliers.java  运行期查询入口（带解析缓存）
│   └─ validator/               各规则的取值校验器
├─ command/MinoCommand.java     /mino 命令树
└─ mixin/
    ├─ IronGolemMixin.java                 非玩家建造的铁傀儡不攻击玩家
    ├─ MendingMaxMixin.java                 经验修补自动修满全部装备
    ├─ LivingEntityMixin.java              生物掉落倍率 + 堆叠合并
    ├─ SnowGolemMixin.java                 雪傀儡不因温暖群系或淋雨受伤
    ├─ TradePoolMixin.java                 往交易池塞自定义交易（冰、附魔金苹果）
    ├─ TripWireBlockMixin.java             刷线机（改写绊线钩的线路状态推算）
    ├─ WanderingTraderDespawnMixin.java    流浪商人消失延迟
    └─ VillagerTradesMixin.java            村民交易 Pro（钻石装备 / 弓弩的满级附魔）

src/main/resources/
├─ fabric.mod.json
├─ carpet-mino-addition.mixins.json
├─ icon.png
├─ assets/carpet-mino-addition/          图标、语言文件
└─ data/carpet-mino-addition/villager_trade/
    ├─ wandering_trader/emerald_ice.json
    └─ farmer/5/emerald_enchanted_golden_apple.json
```

模组纯服务端（`fabric.mod.json` 的 `environment` 是 `server`），没有 `src/client`，
也没启用 `splitEnvironmentSourceSets()`。

## Carpet 扩展的接入点

Carpet Extension 不自己造命令树，规则统一挂在 `/carpet` 下：

1. **注册**：`CarpetMinoAddition.onInitialize()` 里 `CarpetServer.manageExtension(EXTENSION)`。
2. **规则**：在 `MinoSettings` 里写 `public static` 字段 + `@Rule` 注解，字段名即规则名；
   注册动作放在 `MinoExtension.onGameStarted()` 里调用 `CarpetServer.settingsManager.parseSettingsClass(...)`。

   > **注册时机不能挪到静态块。** `parseSettingsClass` 内部会调 `Translations.updateLanguage()`，
   > 它遍历 `CarpetServer.extensions` 收集翻译；静态块执行时本扩展还没进那个列表，
   > 收集不到自己的翻译键，Carpet 直接抛 `NullPointerException: No language key provided`。
   > `onGameStarted()` 是 Carpet 遍历扩展列表之后才回调的，时机正确。

3. **命令**：`MinoExtension.registerCommands(dispatcher, context)` 里注册本扩展命令（如 `/mino ...`）。
4. **生命周期**：`onGameStarted` / `onServerLoaded` / `onServerLoadedWorlds` / `onTick` /
   `onServerClosed` / `onReload` 按需覆盖。
5. **翻译**：规则名与描述走 `assets/carpet-mino-addition/lang/zh_cn.json`、`en_us.json`，
   键用**绝对形式** `carpet.rule.<规则名>.name` / `.desc`、`carpet.category.<类别>`。
   相对形式（`rule.xxx`）也会被 Carpet 改写成功，但和绝对键混用后难以判断最终命名空间，故统一用绝对键。

### 校验器必须放行默认值

Carpet 启动时会用默认值重置一遍规则。如果 `Validator` 拒绝了规则的默认值，
服务器会直接起不来：

```
java.lang.IllegalStateException: Rule couldn't be set to default value!
```

加新规则时先确认校验器接受 `@Rule` 字段的初始值。

## 本机专属配置

仓库里的 `gradle.properties` 只放可移植的配置。跟机器绑定的项**不要提交**，
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
