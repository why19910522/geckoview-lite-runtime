# GeckoView Lite Runtime

基于 Gecko/GeckoView 源码定制的 Android 浏览器运行时方案：裁剪可选功能，并提供随应用打包和可选的动态加载两种接入方式。

**当前阶段：静态 Release 与可选外置加载原型均已生成。** 保持 compileSdk35 / targetSdk30 / minSdk26、Lite、仅 zh-CN、C/C++ `-Os + ThinLTO`，保留 DevTools，移除 reporter、Glean SDK 后端、PDF.js 和 Web Speech。完整 AAR 为70.95 MiB；外置 bridge AAR 为1.32 MiB，签名内核包69.64 MiB。双模式在一台 Android16真机通过默认、isolated、app zygote 的页面、真实远程调试和 PDF 外部响应检查。外置基础探针 APK 为4.12 MiB，另需安装内核；共享容器增加内存压力。当前 external 是实验原型，需要预留契约内的资源 FD 槽，尚未完成多设备/16KB设备验证及自动更新回退。项目不是 Mozilla 官方产品。

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
| [外置内核 M2 原型](docs/builds/2026-10-26-external-core-m2.md) | 同次构建拆包、验签安装、FD/isolated/zygote、双模式设备证据与边界 |
| [PDF.js / Speech 裁剪与 C++ 优化](docs/builds/2026-10-26-pdf-speech-cxx-size.md) | 当前 AAR、同源码体积对照、DevTools 真机证据、裁剪边界和验证限制 |
| [无 Glean 中文 Release AAR](docs/builds/2026-10-26-no-glean-release-zh-CN.md) | 当前变体、SDK 后端移除、Release 来源、产物摘要和实际验证范围 |
| [Lite 中文 AAR](docs/builds/2026-10-23-lite-zh-CN.md) | 上一版 Debug 变体、reporter 移除、中文打包和体积对比 |
| [静态构建记录](docs/builds/2026-10-23-sdk35-static.md) | 实际补丁、工具链、AAR/APK 摘要、成功闭包与未通过范围 |
| [构建配置](build-config/README.md) | SDK 35 专用环境、mozconfig 与源码补丁入口 |
| [静态验证宿主](smoke-host/README.md) | 消费本地 AAR 的独立 SDK 35 / target 30 Android 应用 |
| [总体方案](docs/superpowers/specs/2026-10-22-geckoview-lite-runtime-design.md) | 目标、裁剪配置、架构、源码改造、可选动态加载及阶段门槛 |
| [源码调研](docs/research/upstream-audit.md) | 固定上游修订、已核查事实、精确源码入口与未验证项 |
| [验证方案](docs/validation-plan.md) | 兼容性、体积、内存、加载、恢复与 Android 平台测试 |
| [SDK 35 适配](docs/compatibility/sdk35-backport.md) | 编译边界、源码回移、依赖处理与功能删除规则 |
| [决策记录](docs/decisions.md) | 已确认要求、方案默认选择及后续需要确认的产品参数 |
| [调研版本记录](upstream-research.json) | 调研使用的上游仓库和不可变修订 |

仓库保存方案、可重放源码补丁、构建配置、拆包工具、可选本地安装器和双模式验证宿主；完整上游源码、构建产物及签名私钥留在 ignored 本机目录。当前变体保留无操作指标兼容接口、旧 Telemetry、WebRTC、JXL、断词词典和 PDF 打印/生成；HLS/Media3 继续关闭。PDF 查看需由宿主接管外部响应。旧基线记录保留；上游聚合示例、全站性能和多设备兼容性不属于已通过结论。
