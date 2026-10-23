# GeckoView Lite Runtime

基于 Gecko/GeckoView 源码定制的 Android 浏览器运行时方案：裁剪可选功能，并提供随应用打包和可选的动态加载两种接入方式。

**当前阶段：方案设计，尚未实现或验证内核构建、裁剪收益及动态加载。** 项目不是 Mozilla 官方产品。

## 设计原则

- 静态接入可以独立使用，不需要启用动态加载。
- 构建适配目标为 `minSdk=26`、宿主 `targetSdk=30`、`compileSdk=35`；允许裁剪依赖更高平台 API 的可选功能，尚未完成构建验证。
- 动态接入复用同一套内核构建，通过外置原生库与 Gecko 资源减少基础 APK 占用。
- 项目不保存具体分发渠道、CDN 或内核下载链接，也不内置下载服务；包来源由接入方提供。
- Java/JNI、Gecko 资源和子进程必须遵守同一版本契约，不承诺任意 Gecko 版本直接替换。
- 以实测决定裁剪项，保留浏览器安全边界，不预先承诺内核体积和内存数值。

## 文档

| 文档 | 内容 |
| --- | --- |
| [总体方案](docs/superpowers/specs/2026-10-22-geckoview-lite-runtime-design.md) | 目标、裁剪配置、架构、源码改造、可选动态加载及阶段门槛 |
| [源码调研](docs/research/upstream-audit.md) | 固定上游修订、已核查事实、精确源码入口与未验证项 |
| [验证方案](docs/validation-plan.md) | 兼容性、体积、内存、加载、恢复与 Android 平台测试 |
| [SDK 35 适配](docs/compatibility/sdk35-backport.md) | 编译边界、源码回移、依赖处理与功能删除规则 |
| [决策记录](docs/decisions.md) | 已确认要求、方案默认选择及后续需要确认的产品参数 |
| [调研版本记录](upstream-research.json) | 调研使用的上游仓库和不可变修订 |

当前仓库只保存设计资料。后续实施拟保留独立上游源码工作区和可重放补丁，不将完整 Firefox 历史、构建产物或签名私钥提交到本仓库。总体方案经确认后，再拆分可执行的实现任务。
