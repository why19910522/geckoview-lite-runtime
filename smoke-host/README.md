# SDK 35 静态消费验证宿主

此宿主用于消费**本机源码构建发布的 GeckoView**，固定 `compileSdk=35`、`targetSdk=30`、`minSdk=26` 和 arm64-v8a。它不包含动态加载、下载服务或内核分发地址。启动页面来自 APK 内的 `assets/smoke.html`。

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
