# SDK 35 源码补丁

输入为 `upstream-research.json` 固定的 Firefox 修订。按 `series` 顺序应用；本目录不提供预编译内核或下载渠道配置。构建依赖仓库属于工具链输入，与内核分发渠道无关。

| 补丁 | 原因 | 行为变化 |
| --- | --- | --- |
| 0001-sdk35-target30-platform.patch | 原始 configure 在只有 API 35 的 SDK 中查找 `android-37.2` 并失败 | 编译主/次版本改为 35/0，target 改为 30，整数平台使用 `android-35` 目录；min 保持 26 |
| 0002-sdk35-cursor-blink-default.patch | 上游光标方法用 API 35 android.jar 编译出现 4 个缺失符号 | 不读取 Android 36.1 的系统光标闪烁设置，统一沿用已有 500 ms 默认值；JNI 方法名称、签名保留 |
| 0003-route-build-dependencies.patch | Gradle 对无关仓库的 HEAD 请求超时后禁用整个仓库，导致依赖解析失败 | 按官方 Maven 坐标组过滤 Google、Mozilla、Central；保留插件仓库、自定义仓库、版本锁及校验 |
| 0004-sdk35-accessibility-fallback.patch | 完整构建的 `compileDebugJavaWithJavac` 报 API 36 的 required-field 与 tri-state 缺失符号 | 删除新版节点必填属性上报，沿用上游旧系统的 boolean checked 状态；基础无障碍保留，部分选中状态不再通过 API 36 接口上报 |
| 0005-optional-android-hls.patch | `checkDebugAarMetadata` 证实 Media3 1.11.1 的八个 AAR 要求 API 36 | 为上游既有 HLS 条件编译路径增加 `--disable-android-hls`；默认仍开启，本次 mozconfig 关闭原生 HLS/Media3 集成 |
| 0006-sdk35-core-dependency.patch | 同一检查证实 Core 1.19.1 要求 API 37 | 仅 GeckoView 模块改用 Core 1.16.0；未修改 AAR 元数据，不全局降级其他 Android 应用的目录项 |

光标方法已经过定向 javac 失败→成功验证；这不能代替整个 GeckoView 构建和设备运行验证。完整构建状态以 `docs/builds/` 的实际记录为准。

在源码根目录对每个补丁执行 `patch -p1 -i <补丁绝对路径>`，应用前可加 `--dry-run`。已应用源码可以使用 `--dry-run --reverse` 检查反向应用；不要在不匹配修订上强制忽略上下文应用。

这些补丁修改 Mozilla Public License 2.0 覆盖的上游文件，修改后的源文件继续保留原有 MPL 2.0 声明。完整修改源码由固定上游源码与本目录补丁共同重建；不声明替换上游许可证。

API 边界参考：[Android AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)，`setFieldRequired(boolean)`、`setChecked(int)` 自 API 36 提供。

依赖版本参考：[AndroidX Core 发布记录](https://developer.android.com/jetpack/androidx/releases/core)。是否兼容以实际 AAR 元数据和独立宿主打包为准。
