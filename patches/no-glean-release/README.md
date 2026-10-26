# 无 Glean 后端 / Release 产品补丁

在固定 Gecko `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a` 上依次应用 `patches/sdk35/series`、`patches/lite-zh-CN/series`、本目录 `series`。每个补丁使用 `patch -E -p1 --fuzz=0`；不能在此产品源码上使用旧的 Glean/reporter 开启配置。

本补丁是产品分支裁剪：增加 `--disable-glean` 的配置记录；将 Rust SDK、FOG 控制和 JOG 后端从实际 Android 静态链接依赖中移除；现有引擎指标调用经零状态 Rust 声明和 inline C++ ABI 空实现适配。保留兼容类型/JS 与 C++ 指标绑定，不声称源码及产物里每个 `Glean` 字符串都消失。空实现没有 SDK 初始化、数据库、队列、线程、IPC payload、上传器或指标缓存；GIFFT 映射清空，避免从这些调用继续向旧 Telemetry 镜像数据。与 Glean 相连的 Gecko Trace 收集/上传路径也关闭，Rust collector 不进入链接；`about:glean` 注册、页面和专用 Fluent 资源也移除；旧 Telemetry 和其他非 Glean 组件不在本轮全面移除范围。

两个 `no_glean/*_ffi_generated.h` 是基于固定修订 cbindgen 接口生成并审核的兼容头；不再运行后端 crate 的 cbindgen。所有非 const 输出均清空/归零；采集 getter 返回无值，报告/注册操作返回 `NS_ERROR_NOT_AVAILABLE`，启动初始化空操作返回 `NS_OK`，不创建后端。FOG 单例不安装 idle observer/内存 reporter，初始化不注册生命周期任务。Glean 的 `RecordPowerMetrics` 在禁用配置下编译为空函数，不读取 CPU/GPU 时间或生成对应 profiler marker。升级 Gecko 时必须重新核对接口，不能盲目复用。

Rust crate `firefox-on-glean` 名称保留用于源码依赖兼容，其实现已替换为不依赖 Glean SDK 的声明；不得仅以 Cargo.lock 的工作空间包列表或该兼容 crate 名称判断实际 SDK 链接。以 Android gkrust 依赖树、编译指纹、实际 libxul 导出和 AAR/SBOM 为证据。构建期 Python `glean_parser` 用于读取上游 schema，属于工具链，不进入 AAR。

Gradle 改为发布 `release` 软件组件及对应 sources/javadoc，SBOM 使用 `releaseRuntimeClasspath`。JNI 导出仍遵循上游 Debug 绑定生成任务；最终分发 AAR 来自 `assembleRelease`，并检查其 `BuildConfig.DEBUG=false`。native 使用 `--disable-debug --enable-optimize --enable-release`。

构建配置见 [mozconfig](../../build-config/mozconfig-sdk35-lite-no-glean-release-zh-CN-arm64)。按原中文构建顺序执行 configure、pre-export、export、binaries、merge-zh-CN、misc、libs、stage-package，然后：

```sh
IS_LANGUAGE_REPACK=1 python mach gradle \
  geckoview:checkReleaseAarMetadata geckoview:assembleRelease \
  geckoview:publishReleasePublicationToMavenRepository
```

`IS_LANGUAGE_REPACK=1` 仅用于已完整生成资源与 native 后的最终 Gradle 打包，避免上游 FasterMake 硬编码 en-US；未绕过 AAR 元数据。Python 和工具版本沿用项目固定环境。

通过 `scripts/verify-no-glean-aar.py` 检查真实 AAR；还需核验本次对象目录库来源、依赖闭包、中文注册、APK SDK 与对齐。Release variant 不代表 Mozilla 官方稳定版、生产安全认证、设备兼容性验证或 APK 签名发布。
