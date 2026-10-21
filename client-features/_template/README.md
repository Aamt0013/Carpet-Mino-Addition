# _template/

复制本目录的内容作为新客户端功能模组的起点。

**用法**

1. 把整个 `_template` 目录复制一份，改名为 `<feature_name>`（小写 snake_case）。
2. 全局把 `Feature` 替换为你的功能名（大驼峰），把 `feature_name` 替换为包名。
3. 按 README 的接入步骤，把文件放到 `src/client/java/com/example/client/feature/<feature_name>/` 下。
4. 逐个处理文件里的 `TODO(接入)` 注释。

**不要直接修改本目录**——保持模板干净，下一个功能还要用它。

## 包含文件

| 文件 | 作用 | 是否必需 |
| --- | --- | --- |
| `FeatureEntrypoint.java` | 功能入口，注册到 `CarpetMinoAdditionClient` | 必需 |
| `FeatureKeybind.java` | 输入处理样例（keybind 注册与回调） | 可选 |
| `FeatureRenderer.java` | 渲染 / HUD 样例 | 可选 |
| `mixin/FeatureClientMixin.java` | 客户端 Mixin 样例（钩子转发到入口类） | 可选 |
