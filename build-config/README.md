# 静态 arm64 构建配置

`mozconfig-sdk35-arm64` 使用完整源码编译，不启用 Artifact Build，保留 WebRTC、普通媒体、PDF 和 Crash reporter；因真实 AAR 元数据失败关闭原生 HLS/Media3。裁剪依据见 `patches/sdk35/README.md`。

构建前固定 `upstream-research.json` 的修订并应用 `patches/sdk35/series`。需要单独准备：

- 只提供 `platforms/android-35` 的产品 SDK 根目录。
- 锁定上游要求的 Build Tools 37.0.0、cmdline-tools 23.0、Bundletool 1.18.3；工具版本与 compileSdk 不同。
- NDK r30（本机验证版本为 r30-beta2）、Java 17，以及 Mozilla bootstrap 选出的 Clang 等工具。
- 含 `aarch64-linux-android` 标准库的 Rust；显式选择 RUSTC/CARGO，避免误用不含交叉目标的另一套 Rust。

在运行 mach 前设置下列本机环境变量，不将本机路径或凭据写入版本库：

| 变量 | 含义 |
| --- | --- |
| MOZCONFIG | 本目录 mozconfig 的绝对路径 |
| MOZBUILD_STATE_PATH | 项目专用 Mozilla 工具链与缓存目录 |
| GECKO_OBJDIR | arm64 产品构建输出的绝对路径 |
| GECKO_ANDROID_SDK_ROOT | API 35 产品 SDK 根目录 |
| GECKO_ANDROID_NDK_ROOT | 所选 NDK r30 的根目录 |
| GECKO_JAVA_HOME | Java 17 的 Home 目录 |
| RUSTC / CARGO | 同一套 Rust 工具链的实际可执行文件路径 |
| ANDROID_HOME / ANDROID_SDK_ROOT | 都与 GECKO_ANDROID_SDK_ROOT 一致，防止 Gradle 选择其他 SDK |
| GRADLE_MAVEN_REPOSITORIES | 本次使用下方记录的四个构建依赖仓库及顺序 |
| GRADLE_FLAGS | 添加 `-Pandroid.builder.sdkDownload=false`，防止 Gradle 自动取得更高平台 |

本次使用以下构建依赖仓库覆盖值，与生成配置一致。坐标过滤由补丁 0003 处理；这些是构建输入，不是内核分发渠道：

```sh
export GRADLE_MAVEN_REPOSITORIES="https://repo.maven.apache.org/maven2/,https://dl.google.com/dl/android/maven2/,https://maven.mozilla.org/maven2/,https://plugins.gradle.org/m2/"
```

在源码根目录用项目固定 Python/uv 运行：

```sh
mise x python@3.12.14 uv@0.13.0 -- python mach configure
mise x python@3.12.14 uv@0.13.0 -- python mach build pre-export
mise x python@3.12.14 uv@0.13.0 -- python mach build export
mise x python@3.12.14 uv@0.13.0 -- python mach build binaries
mise x python@3.12.14 uv@0.13.0 -- python mach gradle \
  geckoview:checkDebugAarMetadata geckoview:assembleDebug \
  geckoview:publishDebugPublicationToMavenRepository
```

configure 通过后核对 `config.status.json` 的 SDK/ABI 参数，不能只看 mozconfig 文本。AAR、宿主和验证任务以实际构建记录为准，不把尚未执行的命令记为通过。

本次已验证的准确步骤和失败边界见[构建记录](../docs/builds/2026-10-23-sdk35-static.md)。上游总 `mach build` 额外构建多个示例/测试应用，其中消息示例依赖要求 API 37；以上产品目标与独立宿主不跳过元数据检查。


## Lite / 无 Crash reporter / zh-CN 变体

使用 `mozconfig-sdk35-lite-zh-CN-arm64`，先应用 SDK35 补丁再应用 `patches/lite-zh-CN/series`。相同 SDK/工具链环境外，设置独立 `GECKO_OBJDIR`，以及指向已固定中文翻译仓库根目录的 `GECKO_L10N_BASE`。旧配置仅适用于没有应用产品移除补丁的基线源码工作区。

新对象目录必须先执行 `pre-export`、`export`，再执行 `binaries`；上一轮 `binaries` 的成功使用了已由总构建准备好的对象目录，不能将其当作从零生成全部公共文件的命令。

## 无 Glean 后端 / 中文 Release 变体

使用 `mozconfig-sdk35-lite-no-glean-release-zh-CN-arm64`，依次应用 `sdk35`、`lite-zh-CN`、`no-glean-release` 三组补丁，固定相同翻译和工具环境，使用独立 objdir。产品补丁移除 SDK 后端并改为发布 Gradle Release 组件；必须 `--disable-glean`。旧变体需在仅应用各自补丁的独立源码目录重建，不能将旧 mozconfig 用在此最终产品源码上。

完整命令、无操作兼容层边界及实际摘要见[Release 构建记录](../docs/builds/2026-10-26-no-glean-release-zh-CN.md)。最后执行 `checkReleaseAarMetadata`、`assembleRelease` 和 `publishReleasePublicationToMavenRepository`；不能重命名旧 Debug AAR 冒充 Release。

## 无 PDF.js / Web Speech，保留 DevTools

在上述三组补丁之后应用 `patches/pdf-speech/series`。当前推荐配置为 `mozconfig-sdk35-trim-os-thinlto-zh-CN-arm64`；`mozconfig-sdk35-trim-o2-zh-CN-arm64` 是相同裁剪源码的体积对照。两版使用独立 objdir、相同工具链与固定翻译，完整执行 configure → pre-export → export → binaries → merge-zh-CN → misc → libs → stage-package → Release 发布。

新增 `--disable-pdfjs`，使用上游 `--disable-webspeech`；产品补丁同时移除启动入口、资源及 Speech Java/JNI 生成。DevTools server 保留。优化版使用 C/C++ `-Os` 和 `--enable-lto=thin`，Rust 维持原配置，不启用跨语言 LTO。实际编译选项、AAR 摘要及设备检查见[本轮构建记录](../docs/builds/2026-10-26-pdf-speech-cxx-size.md)。旧配置只能在对应补丁阶段的源码工作区使用。
