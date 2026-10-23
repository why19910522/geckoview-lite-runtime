# compileSdk 35 回移与裁剪方案

日期：2026-10-23。状态：静态产品 native/AAR/独立宿主构建已通过，设备运行与动态模式待验证。实际失败、裁剪及验证范围见[构建记录](../builds/2026-10-23-sdk35-static.md)。

## 构建目标

产品 GeckoView、bridge、bundled/external 模块及验证宿主使用 `compileSdk=35`，首期宿主 `targetSdk=30`、`minSdk=26`。SDK 35 所需的源码回移与功能删除均需通过两种接入模式验收。不能因依赖难以处理就悄悄把产品编译 SDK 升回 36/37。

上游研究修订默认 compileSdk=37.2、targetSdk=37。保留独立上游参考 lane；它可以用上游平台 SDK，但不得代替 SDK 35 产品构建结果。新版本工具链不等于新版本 compileSdk，NDK、Rust、JDK、AGP、Build Tools 分别固定与验证，不要求全部同步降级。

## 已核查的修改入口

- `build/moz.configure/android-sdk.configure`：平台主/次版本、SDK 平台目录查找和导出的 `ANDROID_COMPILE_SDK_MAJOR/MINOR`、`ANDROID_TARGET_SDK`。
- `mobile/android/geckoview/build.gradle`：从生成参数选择 compileSdk major/minor，并独立设置 min/target。API 35 平台的选择必须与 configure 结果一致，消除 37.2 路径或 minor 假设。
- 其余 Gradle 公共参数、生成 BuildConfig/JNI、测试宿主与传递依赖：实施时沿实际配置链展开，不假定已经存在可直接使用的覆盖开关。

完整不可变源码引用见[源码调研](../research/upstream-audit.md)。上述入口说明改造位置，不证明仅修改参数就足以构建。

## 不支持项的处理规则

| 类型 | 处理顺序 | 验收要求 |
| --- | --- | --- |
| API 36+ 的可选 Android 平台集成 | 删除相应适配器及调用入口；或回移到 API 35 及以下实现 | 页面/宿主得到明确的降级或不支持结果，能力清单同步变化 |
| 必要的平台行为与安全机制 | 优先采用公开 API 的兼容实现 | TLS、证书、权限边界、沙箱、多进程、站点隔离不得因编译降级被删除 |
| 新增 Android 类型、方法、常量或 override | 从产品编译源码闭包中移除或改写 | `SDK_INT` 判断、RequiresApi 注解或运行时关闭都不能消除编译符号依赖 |
| SDK 35 不认识的 Android 属性与资源 | 评估删除可选表现或采用已有资源表达 | 不虚造同名平台属性，不把新属性悄悄变为含义不同的自有属性 |
| 要求 minCompileSdk>35 的第三方 AAR | 删除可选依赖；必要依赖选择经验证且仍可维护的版本，或源码回移 | 同时验证功能与安全更新来源，不只修改 AAR 元数据绕过检查 |
| 源码生成和测试依赖 | 调整生成输入及 SDK 35 测试配置 | 产品不能依赖 API 36/37 的 compileOnly 存根；不通过删除关键测试冒充兼容 |
| native 代码与工具链 | 保留以 minSdk 为依据的符号可用性、页大小和装载验证 | compileSdk 下降不自动要求降低 NDK，也不证明 native 可以在所有设备加载 |

上述是处理类别。首轮实测已删除新版光标设置与 API 36 无障碍节点属性集成，关闭原生 HLS/Media3，并将 GeckoView 的 Core 固定到 1.16.0。HLS 依据是当前 Media3 1.11.1 元数据要求 API 36，并不代表 HLS 协议本身无法在 API 35 实现。WebRTC、语音、PDF、扩展及 Crash reporter 仍按总体裁剪矩阵处理；不把它们一概标记为“SDK 35 不支持”。普通 HTML/CSS/JS 能力也不能按 Android API 的版本数字推断是否应删除。

首期优先删除可选集成或使用旧公开 API 回移，不用非 SDK 接口反射规避平台约束。若必要行为无法在限制下实现，则明确报告该功能或运行环境未通过支持门槛，不删除安全边界后宣称成功。

## 实施与退出条件

1. 固定源码、依赖和工具链，为上游参考与 SDK 35 产品建立隔离工作区。
2. 先调整平台选择及生成参数，执行完整 Java/Kotlin、资源、JNI/native 和打包构建；native 改动不使用 Artifact Build 代替。
3. 将实际失败归类为配置、平台符号、资源、生成代码、依赖元数据或运行行为问题；逐项记录源位置、原有能力、删除/回移方案及验证结果。
4. 先得到不做额外功能精简的 SDK 35 回移基线，再叠加 compat-lite/content-lite 的裁剪，分别归因收益。
5. 用只提供 API 35 平台的产品构建环境和独立 API 35 接入宿主验证最终 AAR；检查所有传递依赖，不能以降写 minCompileSdk 或抑制校验代替真实兼容。
6. 对两种接入方式重跑资源、JNI、多进程、外置包指纹及 4 KB/16 KB 装载测试。删除项修改 bridge/service/capability 契约时，生成新配对产物，拒绝与旧包混载。

M0 必须完成 SDK 35 静态构建及 AAR 消费；M2 必须完成同配置外置装载。运行环境继续覆盖 API 26、30、34、35 及更高版本；compileSdk=35 不是运行系统上限。

## 参考

- [Android 15 SDK 设置](https://developer.android.com/about/versions/15/setup-sdk)：API 35 平台与 compileSdk 设置。
- [Android 库发布准备](https://developer.android.com/build/publish-library/prep-lib-release)：AAR minCompileSdk 对接入应用的约束。
- [Android SDK 版本参数](https://developer.android.com/ndk/guides/sdk-versions)：native API 可用性、应用 target 与设备版本的区别。
