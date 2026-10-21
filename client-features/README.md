# 客户端功能模组工作区（Client Feature Workspace）

本目录用于规划、归档与孵化 **客户端专属（client-side）功能模组**。它当前是一个纯文档 + 模板工作区：只有骨架、模板和 README，不接入构建，也不会打进 jar。

孵化完成的模组需要按本 README 末尾的接入步骤，移入 `src/client/java/com/example/client/` 后才能编译、运行和发布。

## 设计前提

本仓库是一个 Fabric mod 工程（Minecraft 1.21.11 / Java 21 / Fabric Loom 1.18），`build.gradle` 中启用了 `splitEnvironmentSourceSets()`：

- `src/main` —— 服务端与双端公共代码
- `src/client` —— 客户端代码，最终与 `src/main` 一并打进同一个 jar

因此客户端功能不需要、也不应该做成独立 mod jar。正确做法是在 `src/client` 下按包拆分功能，由客户端的单一入口 `CarpetMinoAdditionClient` 统一初始化。本工作区就是这套拆分方案的"收纳架"。

## 目录结构

```
client-features/
├── README.md                  ← 本文件：结构说明与规范
├── _template/                 ← 新功能模组的起点，复制而非直接改
│   ├── FeatureEntrypoint.java    入口：onInitializeClient() 中一行注册
│   ├── FeatureKeybind.java       输入样例：keybind 注册 + 按下回调
│   ├── FeatureRenderer.java      渲染样例：在 HUD 上画一个角标
│   └── mixin/
│       └── FeatureClientMixin.java  Mixin 样例：挂到 Minecraft 客户端主循环
├── planned/                   ← 已规划、尚未开工的功能模组（一个功能一个 .md）
├── active/                    ← 正在开发的功能模组（一个功能一个 .md 或子目录）
├── done/                      ← 已完成并接入主源码的功能模组（归档说明）
└── deprecated/                ← 已废弃/被替代的功能模组（保留决策记录）
```

`_template`、`planned`、`active`、`done`、`deprecated` 目前只包含 README 或说明文件，没有 `.java` 参与构建——它们只是占位与说明。真正接入构建的代码必须位于 `src/client` 内。

## 功能模组内部结构约定

孵化后，每个客户端功能在 `src/client/java/com/example/client/` 下占一个独立子包：

```
src/client/java/com/example/client/
├── CarpetMinoAdditionClient.java          ← 客户端唯一入口，只负责调用各功能的 init()
└── feature/
    └── <feature_name>/            ← 一个功能一个包，全部小写 snake_case
        ├── <FeatureName>.java         ← 入口类，实现内部接口 Feature
        ├── <FeatureName>Keybind.java  ← 可选：输入处理
        ├── <FeatureName>Renderer.java ← 可选：渲染 / HUD
        └── mixin/
            └── <FeatureName>Mixin.java ← 可选：该类专属 Mixin
```

要点：

- 一个功能一个包，包内自包含；功能之间不直接引用对方内部类，需要共享能力时下沉到 `client/util/` 或 `client/api/`。
- 包名小写 snake_case，类名大驼峰；`feature` 包下不再分层（不要 `feature/x/mixin/` 与 `feature/mixin/` 混用）。
- 每个功能在 `CarpetMinoAdditionClient` 中显式注册，而不是依赖 classpath 扫描或静态初始化副作用——顺序可控，问题可定位。
- 日志统一走 `CarpetMinoAddition.LOGGER`，tag 用 `[<feature_name>]` 前缀，便于在日志里按功能过滤。

## 从工作区孵化一个功能（接入步骤）

1. 在 `planned/` 或 `active/` 写清楚功能说明：目标、交互方式、涉及哪些 Minecraft 类、风险点。
2. 复制 `_template/` 下四个文件，改名为 `<FeatureName>` 形式，放进 `src/client/java/com/example/client/feature/<feature_name>/`（mixin 文件放进其下的 `mixin/`）。
3. 在功能入口类中实现初始化逻辑，把模板里的 TODO 换成真实内容。
4. 打开 `src/client/resources/carpet-mino-addition.client.mixins.json`，把新 Mixin 加进 `client` 数组。**不要新建客户端 mixins json**，集中在一个配置文件里，出问题好排查。
5. 打开 `src/client/java/com/example/client/CarpetMinoAdditionClient.java`，在 `onInitializeClient()` 里加一行注册。
6. 如需 keybind，在 `src/client/resources/...` 的资源侧注册按键（参考 `_template/FeatureKeybind.java` 的 TODO 注释），并在功能说明里记录默认键位。
7. 验证通过后，把该功能的说明文件从 `active/` 移到 `done/`，并在文件头部补一行"已接入"与日期。

## 规范速查

| 项目 | 约定 |
| --- | --- |
| 包路径 | `carpet.mino.client.feature.<feature_name>` |
| 包名 | 小写 snake_case，例：`waypoint_hud`、`zoom_overlay` |
| 类名 | 大驼峰，例：`WaypointHud`、`ZoomOverlay` |
| Mixin 类 | `<FeatureName>Mixin`，放在该功能的 `mixin` 子包内 |
| Mixin 配置 | 统一登记到 `src/client/resources/carpet-mino-addition.client.mixins.json` |
| 入口注册 | `CarpetMinoAdditionClient.onInitializeClient()` 中显式调用 |
| 日志 tag | `[<feature_name>]` |
| 状态目录 | planned → active → done（deprecated 单独放） |

## 反模式（不要这样做）

- 在工作区目录里直接写 `.java` 并期望它被编译——本目录不在任何 source set 中。
- 一个功能散落在多个顶层包（输入在 `client/key`、渲染在 `client/render`），后期无法单独摘除。
- 为每个功能新建一个 `*.client.mixins.json`——配置文件碎片化。
- 在 Mixin 里写业务逻辑：Mixin 只做钩子转发，逻辑放在功能类里。
- 让功能之间互相 new 对方的内部类——耦合后无法单独禁用某个功能。
