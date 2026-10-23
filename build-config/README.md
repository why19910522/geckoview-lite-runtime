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
mise x python@3.12.14 uv@0.13.0 -- python mach build binaries
mise x python@3.12.14 uv@0.13.0 -- python mach gradle \
  geckoview:checkDebugAarMetadata geckoview:assembleDebug \
  geckoview:publishDebugPublicationToMavenRepository
```

configure 通过后核对 `config.status.json` 的 SDK/ABI 参数，不能只看 mozconfig 文本。AAR、宿主和验证任务以实际构建记录为准，不把尚未执行的命令记为通过。

本次已验证的准确步骤和失败边界见[构建记录](../docs/builds/2026-10-23-sdk35-static.md)。上游总 `mach build` 额外构建多个示例/测试应用，其中消息示例依赖要求 API 37；以上产品目标与独立宿主不跳过元数据检查。
