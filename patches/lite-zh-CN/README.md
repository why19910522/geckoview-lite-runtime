# Lite / 无 reporter / zh-CN 产品补丁

输入为固定 Gecko 修订，先应用 `patches/sdk35/series`，再按本目录 `series` 应用，命令使用 `patch -E -p1 --fuzz=0 -i <补丁路径>`；`-E` 确保 BSD patch 删除 AIDL 文件而不留下空文件。使用 `build-config/mozconfig-sdk35-lite-zh-CN-arm64`，不能与启用 Crash reporter 的基线配置混用；Java 源集有显式保护，误启用会构建失败。重建旧基线时使用独立源码工作区，仅应用 SDK35 补丁。

- `0001`：从 Java 源集排除 CrashReporter、CrashHandler、CrashPullController、CrashHelper，并删除 ICrashHelper.aidl 输入（AGP 未遵守原 AIDL exclude 配置）；去掉启动、上报和 CrashPull 桥接入口及 Manifest 服务。非空 crash handler 设置明确抛出 `UnsupportedOperationException`；未捕获异常转交当前线程的 Android 默认处理器，不安装替代上报器。
- `0002`：去掉 ChildCrashHandler JS 模块、启动注册和 CrashPull 事件；reporter 专用 Fluent 资源仅在 MOZ_CRASHREPORTER 时打包。原生 reporter、CrashSubmit/CrashManager 等其余模块由上游 `--disable-crashreporter` 条件编译排除。

- `0003`：为非 en-US 的直接源码构建传递实际 l10n base，使用配置的 UI locale 生成清单，去掉清单与 installer 对 en-US 的强制追加；默认 en-US 输入行为保持。

`CrashMonitor` 的通用启动/异常退出状态监测和必要 native 空实现不属于报告采集/上传后端，保留其既有依赖。此变体移除了 reporter API，接入方不能继续引用上述类及 runtime 的 CrashPull 委派方法。

当前固定修订的 `--enable-geckoview-lite` 主要调整 Maven 能力声明与 artifactId；它没有在 native 构建中完全去掉 Glean。本配置开启该开关，但不把它报告成 Glean 已完全移除。

中文翻译输入见 `localization/zh-CN-source.json`。仅保留简体中文 UI 本地化包，不裁剪网页 `Intl`、字符集或任意语言网页内容支持。最终是否符合要求，以实际 AAR/JAR/omni/Manifest 检查和独立宿主消费为准。

本目录补丁修改的上游 MPL 2.0 文件继续遵守原许可证；完整源由固定上游与两组补丁重建。
