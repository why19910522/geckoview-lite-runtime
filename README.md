# GeckoView Lite Runtime

基于 Gecko/GeckoView 源码定制的 Android 浏览器运行时方案：裁剪可选功能，并提供随应用打包和可选的动态加载两种接入方式。

**当前阶段：SDK 35 静态构建基线已验证。** 已从固定源码生成 arm64 GeckoView AAR，并完成 targetSdk 30 独立宿主 APK 打包；设备运行、裁剪收益和动态加载尚未验证。项目不是 Mozilla 官方产品。

## 设计原则

- 静态接入可以独立使用，不需要启用动态加载。
- 已验证构建参数 `minSdk=26`、宿主 `targetSdk=30`、`compileSdk=35`；根据实际错误移除或回移高版本平台集成，运行兼容性待验证。
- 动态接入复用同一套内核构建，通过外置原生库与 Gecko 资源减少基础 APK 占用。
- 项目不保存具体分发渠道、CDN 或内核下载链接，也不内置下载服务；包来源由接入方提供。
- Java/JNI、Gecko 资源和子进程必须遵守同一版本契约，不承诺任意 Gecko 版本直接替换。
- 以实测决定裁剪项，保留浏览器安全边界，不预先承诺内核体积和内存数值。

## 文档

| 文档 | 内容 |
| --- | --- |
| [静态构建记录](docs/builds/2026-10-23-sdk35-static.md) | 实际补丁、工具链、AAR/APK 摘要、成功闭包与未通过范围 |
| [构建配置](build-config/README.md) | SDK 35 专用环境、mozconfig 与源码补丁入口 |
| [静态验证宿主](smoke-host/README.md) | 消费本地 AAR 的独立 SDK 35 / target 30 Android 应用 |
| [总体方案](docs/superpowers/specs/2026-10-22-geckoview-lite-runtime-design.md) | 目标、裁剪配置、架构、源码改造、可选动态加载及阶段门槛 |
| [源码调研](docs/research/upstream-audit.md) | 固定上游修订、已核查事实、精确源码入口与未验证项 |
| [验证方案](docs/validation-plan.md) | 兼容性、体积、内存、加载、恢复与 Android 平台测试 |
| [SDK 35 适配](docs/compatibility/sdk35-backport.md) | 编译边界、源码回移、依赖处理与功能删除规则 |
| [决策记录](docs/decisions.md) | 已确认要求、方案默认选择及后续需要确认的产品参数 |
| [调研版本记录](upstream-research.json) | 调研使用的上游仓库和不可变修订 |

仓库保存方案、可重放源码补丁、构建配置和静态验证宿主；完整上游源码、构建产物及签名私钥留在 ignored 本机目录。本次保留 Crash reporter 与 WebRTC，关闭原生 HLS/Media3；上游聚合示例构建未通过，产品 native/AAR/独立宿主已通过，详见构建记录。
