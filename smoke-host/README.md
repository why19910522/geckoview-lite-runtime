# SDK 35 双模式消费验证宿主

此宿主用于消费**本机源码构建发布的 GeckoView**，固定 `compileSdk=35`、`targetSdk=30`、`minSdk=26` 和 arm64-v8a。默认使用 bundled；可显式启用 external 本地安装探针，不包含下载服务或内核分发地址。启动页面来自 APK 内的 `assets/smoke.html`。

## 输入

- 已应用补丁并成功完成 native 构建的固定 Gecko 源码目录。
- 该构建的本地 Maven 发布目录，包含 AAR、POM 和 Gradle module metadata。
- 发布的准确 Maven 坐标；不要填写远端官方 GeckoView 的坐标作为替代品。
- Java 17 和仅有 `platforms/android-35` 的 SDK 根目录；Build Tools 37.0.0 与 compileSdk 是不同参数。

在本机环境中设置 `RUNTIME_REPO`、`GECKO_SOURCE_DIR`、`GECKO_REPOSITORY`、`GECKO_COORDINATE` 的绝对路径/坐标，并设置 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`。两个 Android 环境变量均指向 SDK 35 根目录。

使用固定上游的 Gradle wrapper（9.8.1），不复制 wrapper 二进制到本项目：

```sh
"$GECKO_SOURCE_DIR/gradlew" -p "$RUNTIME_REPO/smoke-host" \
  -PgeckoRepository="$GECKO_REPOSITORY" \
  -PgeckoCoordinate="$GECKO_COORDINATE" \
  :app:assembleDebug --console=plain
```

构建需要额外 Mozilla 运行时依赖仓库时，可从本机环境传入 `-PupstreamDependencyRepository="$GECKO_DEPENDENCY_REPOSITORY"`。这是构建依赖源，宿主不读取它作为内核下载渠道。`org.mozilla.geckoview` 被独占限制为指定本地目录，不能回退到在线预编译内核。

保留 AAR 元数据检查与 SDK 自动下载禁用设置。输出 `app/build/outputs/apk/debug/app-debug.apk` 只证明编译/打包；未在设备启动前不能把页面内的成功标记当作运行验证结果。APK 包含调试签名，仅用于验证。

## 消费本地 Release AAR

将本地 Maven 路径与准确坐标切换到对应 Release 构建记录中的值，执行 `:app:assembleRelease`。当前已验证记录见[无 Glean 中文 Release AAR](../docs/builds/2026-10-26-no-glean-release-zh-CN.md)。宿主输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，仅用于消费和包检查；AAR 本身无需 APK 签名。

用户指定 targetSdk30，因此本地宿主只对 lint 的 `ExpiredTargetSdkVersion` 发布策略诊断定点豁免；其他 lint 和 AAR/API 元数据检查继续启用。不把本地验证结论表述为应用商店发布资格。

## 可选运行探针与 DevTools 验证

默认仍打开 `smoke.html` 且不启用远程调试。显式添加以下参数并同时构建 Debug/Release，可对本轮裁剪变体启用本地运行探针：

```sh
"$GECKO_SOURCE_DIR/gradlew" -p "$RUNTIME_REPO/smoke-host" \
  -PgeckoRepository="$GECKO_REPOSITORY" \
  -PgeckoCoordinate="$GECKO_COORDINATE" \
  -PsmokeApplicationId=dev.geckolite.smoke.trimprobe.opt \
  -PruntimeProbe=true -PexpectTrimmed=true \
  :app:assembleDebug :app:assembleRelease --console=plain
```

用独立 applicationId 避免覆盖已有应用，安装 Debug APK 后启动。探针检查 DOM 中文、Canvas 像素、WebGL context/version、合法空 Wasm 模块、IndexedDB/WebRTC 接口存在性，以及三个 Speech 接口缺失。它不测试实际 IndexedDB 读写、WebRTC 通话、音视频或真实站点。页面上的小型 layout/canvas 计时只用于发现明显异常，不构成全面性能基准。

等页面标题或本应用 `GeckoTrimProbe` 日志出现 `TRIM_PROBE` 完成结果后，再转发应用的 Gecko DevTools abstract socket 并运行：

```sh
# DEVICE_ID 和 PROBE_APP_ID 由接入方指定；记录 adb 实际返回的空闲端口。
adb -s "$DEVICE_ID" forward tcp:0 "localabstract:$PROBE_APP_ID/firefox-debugger-socket"
python3 "$RUNTIME_REPO/smoke-host/scripts/probe-rdp.py" \
  --port "$FORWARDED_PORT" --output "$LOCAL_REPORT_PATH" --navigate-pdf
```

脚本核验 RDP 握手、listTabs、getTarget、thread/inspector actor 及 console evaluation，随后读取页面结果。脚本不自动等待页面探针完成，过早执行会失败，可在完成后重试。可选 `--navigate-pdf` 仅调度本地 PDF 导航；还必须检查本应用 PID 的 `GeckoTrimProbe` 日志中 `EXTERNAL_RESPONSE` 回调和 `application/pdf`、414 字节响应长度，不能把调度成功当作 PDF 处理通过。宿主仅关闭测试响应流，实际应用需要实现保存或外部打开。

完成后移除自己创建的端口转发及测试应用，不清理其他应用或全设备日志。本轮实际设备证据和限制见[裁剪与优化记录](../docs/builds/2026-10-26-pdf-speech-cxx-size.md)。

## 外置内核探针

使用拆包工具输出的 `maven/` 与 bridge 坐标；构建时增加 `-PexternalCore=true -PprobeTrustAssets="$LOCAL_TRUST_ASSETS" -PsmokeApplicationId=dev.geckolite.smoke.external -PruntimeProbe=true -PexpectTrimmed=true`。该目录只需包含对应本机测试公钥 `probe-public.der`，不包含私钥或内核包。可传入 `-PlocalBuildRepository="$LOCAL_DEPENDENCY_MIRROR"` 复用本地构建依赖。

`-PisolatedCore=true`、`-PzygoteCore=true` 分别选择上游 isolated 与 app zygote 运行设置；默认均为 false，沿用原宿主默认值。测试时可以通过启动 Intent 的同名 boolean extra 覆盖，必须先彻底停止本测试应用再启动，不能在已有 Runtime 上切换。

把签名 `core-package.zip` 从本机输入流导入探针自身 `files/incoming-core.zip`，以 `installCore=true` 启动一次。探针安装到自己的 no-backup 目录、删除导入副本、重复 prepare 验证幂等和 OFD 租约，再创建 Runtime；后续冷启动无需再次导入。缺包或校验失败只显示失败状态，不初始化 Gecko。此输入方式仅为本地设备验证，不构成分发服务。

运行 `scripts/check-external-apk.py` 核验外置 APK：只允许小型 bootstrap SO，无 11 个 Gecko 库或 `omni.ja`。页面、RDP、PDF 的检查方法与 bundled 相同。每个实际启动的父/GPU/内容进程输出相同 packageId，isolated 子进程 UID 不同，收集日志时不可只按宿主 UID 过滤。
