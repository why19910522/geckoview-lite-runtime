# 上游源码调研与证据

核查日期：2026-10-22。只完成源码与官方文档阅读，没有完整检出、构建或运行 Gecko。

## 调研输入

上游：[mozilla-firefox/firefox](https://github.com/mozilla-firefox/firefox)。通过 GitHub API 查询并固定研究修订：[`d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`](https://github.com/mozilla-firefox/firefox/commit/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a)，提交时间为 2026-10-22 04:03:27 UTC。结构化记录见 [upstream-research.json](../../upstream-research.json)。

研究对象使用当前官方仓库，未把旧 `mozilla/gecko-dev` 镜像中的代码当作本方案的版本事实。少量文件通过 GitHub Contents API 取得并在临时工作区阅读；未将上游源码副本提交本仓库。下列源码链接均固定到同一 SHA，可用于复核。

## 源码观察与设计推论

| 编号 | 固定修订源码 | 已确认观察 | 对方案的影响 |
| --- | --- | --- | --- |
| S1 | [GeckoLoader.java](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/mozglue/GeckoLoader.java#L183) | 库路径通过 BaseDexClassLoader 和应用 ClassLoader 查找；`getLibraryBase` 从 mozglue 推导根目录；`loadLibsSetupLocked` 设置 `MOZ_ANDROID_LIBDIR` | 外置模式应统一库根目录，并在 mozglue 首次加载前固定选择；不能只替换 XUL |
| S2 | [GeckoThread.java：资源入口](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/GeckoThread.java#L332) | 主进程参数将 `-greomni` 指向 `context.getPackageResourcePath()` | 外置 Gecko 资源需要独立入口；仅外置 `.so` 仍会加载 APK 内的资源 |
| S3 | [GeckoThread.java：初始化顺序](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/GeckoThread.java#L445) | 线程等待初始化后，先加载 mozglue，再准备环境及其他库 | 包选择需要进入初始化契约，不能等环境变量设置之后再修改 mozglue 的来源 |
| S4 | [APKOpen.cpp](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mozglue/android/APKOpen.cpp#L152) | native 侧从库目录拼接路径；XUL 存在单独 override；还有 SQLite、NSS 等装载路径 | XUL override 不是完整的独立内核插件机制；需核对全部库依赖与 linker 行为 |
| S5 | [GeckoProcessManager.java](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/process/GeckoProcessManager.java#L859) | 子进程启动数据继承 active extras 等信息 | 可以研究通过初始化信息携带包身份；仍需验证线程时序及访问权限 |
| S6 | [GeckoServiceChildProcess.java](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/process/GeckoServiceChildProcess.java#L53) | `onCreate` 启动 GeckoThread；Binder start 构造 InitInfo；不支持复用已启动进程 | 子进程需在首次 native 装载前拿到所选核心，不能动态重用旧进程加载新包 |
| S7 | [AndroidManifest_overlay.jinja](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/AndroidManifest_overlay.jinja#L14) | 含 crashhelper、media、socket、GPU、RDD、utility 和普通/isolated/zygote content 服务声明 | 服务声明及生成类随宿主固定，版本握手须覆盖各类实际进程 |
| S8 | [ZygotePreload.java](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/gecko/process/ZygotePreload.java#L17) | zygote preload 直接调用 `System.loadLibrary` 装载 mozglue、xul、nss3 | external 不可遗漏 zygote 预加载路径，也不能默认它能读取私有动态包 |
| S9 | [geckoview/build.gradle](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/build.gradle#L118) | Gecko assets 从 staging 引入；JNI 目录单独引入；Lite 与 Omni 的依赖能力不同 | 有明确打包拆分入口，但尚未形成可直接发布的 bridge/core 两套产物 |
| S10 | [mobile/android/moz.configure](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/moz.configure#L5) | 存在 Lite 开关、HLS 标志和内容服务数配置；附带字体默认已排除 | 优先验证现成配置，不能把默认已做的优化再次算作定制收益 |
| S11 | [toolkit/moz.configure](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/toolkit/moz.configure#L1330) | 存在 Web Speech 和 WebRTC 的 configure 选项；EME 选择受条件约束 | 可以做独立裁剪实验；有选项不代表 Android 组合已经通过构建和回归 |
| S12 | [package-manifest.in](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/installer/package-manifest.in#L97) | 包清单包含扩展注册和 PDF.js 资源 | 删除 PDF/扩展涉及注册、资源和依赖，不能直接从 ZIP 中删文件后宣称兼容 |
| S13 | [android-sdk.configure](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/build/moz.configure/android-sdk.configure#L155) | 此修订默认 minSdk=26、targetSdk=37 | 首期沿用支持下限；实施时重新检查所选生产修订和工具链 |
| S14 | [GeckoRuntime.java](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntime.java#L694) | Runtime 创建经过 init；shutdown 使已关联 Session 失效 | 设计不能将关闭 Session 或 shutdown 视为可安全卸载并重载 native 的契约 |
| S15 | [toolkit/moz.configure：Crash reporter](https://github.com/mozilla-firefox/firefox/blob/d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a/toolkit/moz.configure#L3499) | 存在 `--enable-crashreporter` 布尔配置并生成 `MOZ_CRASHREPORTER`；默认值受目标与开发构建条件影响 | 将对应的 `--disable-crashreporter` 列为编译裁剪入口；Android 服务、回调和资源是否随之排除需要依赖分析和构建验证，不能拿开发构建默认未启用的结果冒充 release 裁剪收益 |

表中“影响”是工程推论，不是上游保证的外置装载 API。

## 官方文档依据

- [GeckoView 架构](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)：View/Session/Runtime 分工、跨语言事件、多进程与 Lite/Omni。Lite 是依赖精简变体，不是一个几 MB 的完整浏览器内核保证。
- [GeckoView 贡献指南](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-quick-start.html)：嵌入层与 Gecko 的分层、构建和开发入口。
- [Android 动态代码加载](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)：来源信任、存储位置、完整性与分发环境限制。工程默认只操作内部不可变安装内容。
- [Android 14 动态代码只读要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)：动态代码文件的只读时序。V1 不做 DEX 插件化；此条不能替代 native 加载验证。
- [Android 16 KB 页适配](https://developer.android.com/guide/practices/page-sizes)：原生库需要核查 ELF 对齐并在对应环境测试；不将仅测试 4 KB 环境视为兼容完成。
- [Mozilla MPL 2.0 FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/)：理解上游文件授权及分发源码义务的入口，最终按实际文件许可证和分发内容检查。

以上均为源码或工程文档引用，不是具体内核分发渠道或下载链接。文档页面会更新，源码事实以固定 SHA 为准。

## 必须由原型回答的问题

1. 独立 Gecko 资源容器可否被该版本完整解析，包含内置模块、注册信息和子进程资源访问。
2. 绝对路径装载 mozglue 后，JNI 注册、NSS/SQLite/XUL 及 `DT_NEEDED` 的完整依赖是否仍落在同一包。
3. isolated/zygote 进程如何取得只读文件和 native 依赖，FD 是否需要扩展现有启动协议；路径传递本身不证明可访问。
4. 本机可用的完整 Gecko Android 构建环境、首轮构建成本及稳定版生产修订。
5. 各裁剪项在 Android 上的有效性、实际减少体积、页面能力损失和链接期依赖。
6. 同 bridge 兼容更新允许哪些源码变动，生成 BuildConfig、JNI 或 service 契约是否改变；未经配对测试不能推导兼容。
7. 禁用 Crash reporter 后，crashhelper 服务、native 依赖、Java/JNI 回调及打包资源的实际残留；无 reporter 产物的启动失败检测与恢复是否仍独立可用。

未运行这些验证之前，只能说方案已定位入口，不能说已经支持动态加载、已经缩小体积或已经完成平台适配。
