# PDF.js / Web Speech 裁剪与 C++ 体积优化

2026-10-26，固定源码 `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`（Firefox 160.0a1）。当前交付 **arm64 / Lite / zh-CN Release AAR：70.94 MiB**，保留 DevTools，移除 PDF.js 查看器和 Web Speech，继续保持无 Glean SDK 后端、无 Crash reporter、compileSdk35、targetSdk30、minSdk26。完整摘要见[机器可读记录](2026-10-26-pdf-speech-cxx-size.json)。

## 体积对照

| 变体 | AAR 字节 | MiB | 原生 libxul.so 字节 |
| --- | ---: | ---: | ---: |
| 上一版无 Glean Release，保留 PDF.js / Speech | 85,509,542 | 81.55 | 168,731,552 |
| 同源码裁剪后 `-O2` 对照 | 82,767,120 | 78.93 | 168,544,784 |
| 同裁剪源码 `-Os + ThinLTO` | 74,389,001 | 70.94 | 147,474,944 |

PDF.js / Speech 裁剪相对上一版减少 **2.62 MiB**。相同裁剪源码的编译/链接选项组合再减少 **7.99 MiB（10.12%）**；合计减少 **10.61 MiB（13.01%）**。两份裁剪产物用同一源码、翻译、工具链与功能选项，分别在独立 objdir 完整构建；构建时间戳/Build ID 不同。没有分别隔离 `-Os` 与 ThinLTO 的独立收益，也不将功能删除收益记作编译器收益。

## 产物与固定输入

优化版文件名：`geckoview-trim-os-thinlto-sdk35-zh-CN-arm64-release.aar`；SHA-256：

```text
6458653a780711ecf74d74856ba86f62ca2942a5d8f4e6d5baed9bc6784702e4
```

本地 Maven 坐标：`org.mozilla.geckoview:geckoview-default:160.0.20261026145830-SNAPSHOT`。对照文件为 `geckoview-trim-o2-sdk35-zh-CN-arm64-release.aar`，SHA-256 `9f3fb4b1ab4bdad36bba977c85a89951648acd3df94c3c2c763dfc2f557afe85`，坐标 `org.mozilla.geckoview:geckoview-default:160.0.20261026144442-SNAPSHOT`。

产物保存在本机 ignored `artifacts/sdk35-trim-os-thinlto-zh-CN/` 和 `artifacts/sdk35-trim-o2-zh-CN/`，未上传 AAR 或配置分发渠道。对应对象目录为 `upstream/obj-sdk35-trim-os-thinlto-zh-CN-arm64`、`upstream/obj-sdk35-trim-o2-zh-CN-arm64`；本地 Maven 位于各 objdir 的 `gradle/maven/`。

- Gecko 原始归档 SHA-256：`e218e5c8e645b49df9652813b8a6b8296d0ae6f58f4e3d0c620735acc36e2907`。
- 官方 zh-CN 翻译固定到 `352d5c0f72bddbb50d1869178eaa85b9c83d095e`，locale tree `10833dd1a5bcaa629a5c117c55131b0b27c32929`，两版不改翻译源码。
- Mozilla Clang 22.1.8（taskcluster-Qrcp9ntKSbi2KuTGHcCCWg）、Rust/Cargo 1.98.1、NDK 30.0.15729638（r30-beta2）、Java 17.0.20.1+1、Gradle 9.8.1、AGP 9.4.1、Python 3.12.14、uv 0.13.0。Build Tools 为 37.0.0，与 compileSdk35 是不同参数。
- 四组补丁依次为 sdk35 → lite-zh-CN → no-glean-release → pdf-speech；11 个补丁从原始归档 `patch -E -p1 --fuzz=0` 重放，50 个路径与实际编译源码一致；各补丁及源码摘要见 JSON。

## 裁剪边界

新增 `--disable-pdfjs`，移除构建目录、注册/stream converter、查看器、actor、启动导入/初始化、首选项及专用中文资源。GeckoView 的 PDF.js 状态返回 false；PDF 响应通过 `ContentDelegate.onExternalResponse` 交给宿主。保留 PDF 格式打印/生成和普通保存流程；打印/保存中的 URI 比较常量仍存在，远程 PDF 专用 reftest loader 不再可用，不声称所有 PDF 字符串都被删除。

上游 `--disable-webspeech` 排除 native/WebIDL；产品补丁同时排除 Android SpeechSynthesisService Java、sources/javadoc 与 JNI 生成。两版 XUL 链接输入无 webspeech，对象目录无 SpeechSynthesisService 生成文件，AAR 中无对应 Java 类；真机页面确认 speechSynthesis、SpeechRecognition、webkitSpeechRecognition 不存在。

保留 DevTools server、socket 安全模块及 GeckoViewRemoteDebugger，**477 个 DevTools 资源文件与上一版逐一哈希相同**。WebRTC、JXL、断词词典继续保留。Glean 的无状态兼容接口、旧 Telemetry 和通用 CrashMonitor 边界沿用[上一版说明](2026-10-26-no-glean-release-zh-CN.md)，不是删除全部遥测。

## C/C++ 编译与构建验证

使用 [O2 配置](../../build-config/mozconfig-sdk35-trim-o2-zh-CN-arm64) 与 [Os + ThinLTO 配置](../../build-config/mozconfig-sdk35-trim-os-thinlto-zh-CN-arm64)。后者实际 `dom/base/Document.cpp` 编译参数包括 `-Os -flto=thin -ffat-lto-objects`，生成的 C++ 对象含 `.llvm.lto`；配置生成的链接选项包括 ThinLTO 及 import limit/hot multiplier。Rust 维持原配置，`MOZ_LTO_RUST_CROSS` 为空，不是跨语言 LTO。安全与 ABI 选项沿用上游，不修改为 fast-math 或无异常等额外产品策略。

两版均完整通过以下顺序；工具环境沿用 [build-config](../../build-config/README.md)，每版独立 GECKO_OBJDIR/MOZCONFIG：

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
```

最终 Gradle 只在完整 native/中文资源 staging 后使用 `IS_LANGUAGE_REPACK=1`，避免上游 FasterMake 的 en-US 假设。保留 AAR 元数据检查，不用 Artifact Build、高平台编译存根或 SDK 自动下载绕过要求。编译存在上游警告，不宣称零警告。

两版都由独立宿主消费本地 Maven，`:app:assembleDebug :app:assembleRelease` 通过。实际 Release AAR `BuildConfig.DEBUG=false`，发布 metadata 仅 Release 组件，无 Glean SDK runtime 依赖；sources.jar 无移除的 Speech/reporter Java。11 个原生库的源码 staging → AAR → 两个 APK 内容摘要一致，libxul Build ID 与未剥离链接产物一致，DT_NEEDED 闭包通过。AAR 的 omni.ja 与两 APK 一致；15 个语言注册均为 zh-CN。实际宿主 Manifest 为 compile35/target30/min26；所有 ELF LOAD 段至少 16KB 对齐，两 APK 通过 `zipalign -c -P 16 4`。

## 真机与 DevTools

Vivo V2536A、Android 16、arm64 实体设备；基线与两份裁剪版采用独立应用 ID，同一本地探针。测试后只卸载本任务创建的三个探针应用，移除自己的转发，保留本地 APK 和证据。

| 检查 | 上一版基线 | 裁剪 O2 | 裁剪 Os + ThinLTO |
| --- | --- | --- | --- |
| 中文 DOM / Canvas 像素 / WebGL context/version / 空 Wasm 模块 | 通过 | 通过 | 通过 |
| IndexedDB.open / RTCPeerConnection 接口存在 | 通过 | 通过 | 通过 |
| speechSynthesis | 存在 | 已移除 | 已移除 |
| 三个 Speech 接口均缺失 | 不适用 | 通过 | 通过 |
| DevTools RDP 握手、listTabs、getTarget、console evaluation | 通过 | 通过 | 通过 |
| thread / inspector actor 存在 | 通过 | 通过 | 通过 |
| 本地 PDF 外部响应回调 | 未检查 | application/pdf，414 字节 | application/pdf，414 字节 |
| 小型 layout / canvas 计时（ms） | 45 / 4 | 34 / 3 | 37 / 2 |

DevTools 测试实际通过 adb 转发 Gecko abstract socket，console 读取 `window.runtimeProbeResult`，不是只确认文件存在。PDF 测试在页面检查后由 DevTools 调度本地 PDF 导航，再独立确认宿主外部响应日志；宿主关闭测试流，实际应用仍需实现保存或外部打开。默认宿主不开远程调试，只有显式运行探针参数启用。运行步骤见[宿主说明](../../smoke-host/README.md)。

小型计时每项 7 轮、去掉首轮后取固定中位次序值，仅是单次本地工作负载，未控制温度/调度，不能据此宣称性能提升或全站无退化。IndexedDB/WebRTC 仅检查接口，没有数据库读写、通话或媒体回归。

## 限制与审查

本轮仅验证一台设备的最小行为；没有首屏/PSS/耗电/完整网页基准、多设备/API矩阵、4KB/16KB 实机矩阵或所有上游测试。Release 组件仍来自固定 Nightly 源码，不代表 Mozilla 官方稳定版或生产认证。动态加载不在本轮实现范围。target30 宿主沿用仅 `ExpiredTargetSdkVersion` 的 lint 发布策略豁免，其他 lint/API/AAR 检查保留，不代表商店发布资格。

独立只读审查完成：源码闭包、补丁/源码摘要、两版产物、设备日志及报告一致，无 Critical 或 Important 问题。审查没有重复编译；原始构建/设备证据由本轮执行提供。已知测试工具小项：`probe-rdp.py` 不自动等待页面探针完成，必须先等 `TRIM_PROBE` 标题/日志再运行，过早执行可重试；这一前置条件已写入宿主说明。
