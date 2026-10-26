# 无 Glean / 无 Crash reporter / 中文 Release AAR

日期：2026-10-26。已从固定 Gecko 源码构建 **Release AAR**，完成包检查及独立 Release 宿主编译消费；保持 compileSdk35、targetSdk30、minSdk26、arm64-v8a、Lite 开启和仅 zh-CN UI 语言包。设备运行尚未验证。

## 产物

| 项目 | 结果 |
| --- | --- |
| 文件 | `geckoview-lite-no-glean-sdk35-zh-CN-arm64-release.aar` |
| 大小 | 85509542 字节 / **81.55 MiB** |
| SHA256 | `57bad3cd750abf7a16d60aaa7793e8a9a6142cb34cc5c0e0dd6f0c70d2dc2e6c` |
| 相较上一版 Lite 中文 Debug AAR | 减少 14295720 字节 / **14.32%** |
| 本地 Maven 坐标 | `org.mozilla.geckoview:geckoview-default:160.0.20261026105744-SNAPSHOT` |
| Gradle 组件 | Release API/runtime publication，`BuildConfig.DEBUG=false` |
| native | non-debug、optimized、`--enable-release`、developer options 关闭 |

产物目录：`artifacts/sdk35-lite-no-glean-release-zh-CN/`，本地 Maven 位于 `upstream/obj-sdk35-lite-no-glean-release-zh-CN-arm64/gradle/maven`。旧 AAR 保留。产物、源码和日志仅保存在 ignored 本机目录，未上传分发渠道。体积差值同时包含 Glean/Trace 移除、Release/native 构建参数等变化，不作为 Glean 单项收益。

**Release 表示实际构建 variant 和 native 优化配置，不代表上游稳定版。** 源码仍是固定 Firefox160.0a1 修订，本地坐标沿用上游 SNAPSHOT 命名；没有改成 Mozilla 官方发行或伪造 release 版本号。AAR 本身无需 APK 签名；验证宿主输出为 unsigned Release APK，没有使用用户签名私钥。

## 源码与裁剪范围

- 沿用 Gecko `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`，先应用六个 SDK35 补丁、三个 Lite/中文/reporter 补丁，再应用 [无 Glean Release 产品补丁](../../patches/no-glean-release/README.md)。10 个补丁从原始归档零 fuzz 重放，43 个路径（含删除）与实际源码一致。
- [mozconfig](../../build-config/mozconfig-sdk35-lite-no-glean-release-zh-CN-arm64) 使用独立 objdir、`--disable-glean --disable-crashreporter --enable-geckoview-lite --disable-debug --enable-optimize --enable-release`；HLS 仍关闭，WebRTC 保留，无 Artifact Build。
- 中文来源继续固定为 Mozilla firefox-l10n `352d5c0f72bddbb50d1869178eaa85b9c83d095e`，zh-CN 树 `10833dd1a5bcaa629a5c117c55131b0b27c32929`；[来源记录](../../localization/zh-CN-source.json)。没有修改翻译输入来绕过打包。
- 工具链沿用 NDK r30-beta2、Mozilla Clang22.1.8、Rust1.98.1、Java17、Gradle9.8.1、AGP9.4.1；完整版本及摘要见[机器记录](2026-10-26-no-glean-release-zh-CN.json)。SDK 根目录只安装 API35 平台，Build Tools37 与 compileSdk 是不同参数。

**移除的运行后端：** Glean Rust SDK/glean-core/FFI、FOG 控制和 JOG 后端不再进入 Android gkrust 链接；没有 SDK 初始化、数据库、任务队列、SDK 采集或上传器。Nightly 默认启用的 Gecko Trace C++ 路径关闭，其 Rust collector 从链接依赖中移除。Glean 向旧 Telemetry 的 GIFFT 镜像映射清空；FOG 不安装 idle observer/内存 reporter，初始化不注册生命周期任务。Glean 的 CPU/GPU 功耗指标采集在预处理后为空函数。

`about:glean` 注册、页面和专用 Fluent 资源已移除。关闭 `MOZ_GLEAN_ANDROID` 原本会反而启用桌面诊断页面，因此额外要求 `MOZ_GLEAN` 开启才允许注册/打包。打包器通过 FileFinder ignore 过滤该 Fluent 文件；此固定 packager 的 manifest removal 指令不受支持，未使用跳过错误方式处理。

**保留的兼容接口：** 既有引擎 C++/JS 指标绑定和 schema 声明保留，用 inline C++ 空 FFI 及零状态 Rust 声明适配。`firefox-on-glean` crate 名称保留，但实现不依赖 Glean SDK；不能凭名字或整个工作空间 Cargo.lock 中仍有其他产品的 SDK 包就认定本 AAR 带 SDK。空 getter 初始化输出并返回无值，上报/动态注册返回 unavailable。Java `sendGleanBrokenSiteReport` 返回异常结果，JS 入口明确 onError，不虚报发送成功。构建期 Python glean_parser 只用于 schema 代码生成，不进入 AAR。

旧 Telemetry、其他非 Glean instrumentation、网页功能及通用 CrashMonitor 仍保留；本轮没有声称所有遥测或所有 `Glean` 字符串都消失。此前 reporter 类/服务、2 个原生库、上报 JS/专用资源继续保持移除。UI 本地化包仅中文，网页 Intl/Unicode 和任意语言内容支持保持；不承诺固定诊断文本全部汉化。

## 构建与验证

在固定工具环境的源码根目录执行（Python3.12.14 / uv0.13.0）：

```sh
python mach configure
python mach build pre-export
python mach build export
python mach build binaries
python mach build merge-zh-CN
python mach build misc
python mach build libs
python mach build stage-package
IS_LANGUAGE_REPACK=1 python mach gradle \
  geckoview:checkReleaseAarMetadata geckoview:assembleRelease \
  geckoview:publishReleasePublicationToMavenRepository
IS_LANGUAGE_REPACK=1 python mach gradle geckoview:writeRuntimeDependencies
```

完整 native 构建退出0；最终 about 页面/注册裁剪后重新 pre-export/export/native 链接通过。最终 Gradle AAR 构建/发布退出0（59任务），独立宿主 `:app:assembleRelease` 退出0（46任务）。`IS_LANGUAGE_REPACK=1` 只用于已完成 native/中文资源/stage-package 后的 Gradle 打包，避免上游 FasterMake 硬编码 en-US；AAR 元数据校验保持开启。

宿主的 Release lint 首次因 `ExpiredTargetSdkVersion` 失败；用户明确要求 target30，此宿主仅用于本地验证，因此**只对这一项 Play 发布策略诊断定点豁免**。没有关闭其他 lint、修改 AAR 元数据或抬高实际 SDK。宿主 Release APK 实际 Manifest 由 AAPT 确认 35/30/26；此验证不代表可按该配置上架应用商店。

实际检查：

- Java `BuildConfig.DEBUG=false`；Maven module 仅有 Release API/runtime 变体，没有 Glean SDK 依赖或 glean-native capability；SBOM 使用 Release runtime classpath。
- Android gkrust 的常规依赖树及完整构建 features 依赖树均无 glean/glean-core/glean-ffi/fog_control/jog/gecko-trace；实际 libxul 不再导出 Glean SDK UniFFI 初始化等接口，Java SDK 也未打包。
- 11 个 arm64 原生库的 staging/AAR/APK 摘要逐个一致，libxul Build ID 对应本次未剥离源码链接产物；DT_NEEDED 闭合，ELF LOAD 至少16KB对齐。
- AAR/APK 的 omni.ja 一致；localization 根、default.locale、multilocale 以及15个 locale 注册均仅 zh-CN，实际 commonDialogs 含中文，没有 en-US UI 目录或 aboutGlean 页面/专用资源。
- 独立 unsigned Release APK 的 ZIP16KB对齐检查通过；设备真实装载尚未验证。
- [产物检查脚本](../../scripts/verify-no-glean-aar.py) 直接读取 AAR/JAR/ELF；[空实现探针](../../scripts/test-no-glean-shims.py) 使用实际源代码片段，验证零状态 Rust 指标、无 FOG 初始化/回调、无 CPU/GPU 指标采集。

已记录失败后修正：生成器使用上游 `_generate_structure` 字段；使用对象目录 vendored Cargo 配置离线更新锁文件；Gecko Trace 默认路径移除后重新构建。首次部分 native 构建被主动终止，不计为成功。独立审查指出初始化 guard 插错位置，通过实际方法回调计数探针 RED→GREEN 修复；功耗采集也经实际函数预处理 RED→GREEN 关闭。上游与本机工具仍有警告，不宣称零警告。

设备启动、网页回归、真实4KB/16KB装载、生产稳定性、应用商店发布、上游所有示例/测试套件和动态加载均不在已通过范围。
