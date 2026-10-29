# GeckoView 轻量运行时与可选动态加载方案

初稿日期：2026-10-22。更新日期：2026-10-23。版本：0.4。状态：待评审的设计方案，未进入产品代码实施。

## 1. 结论与范围

采用“GeckoView 源码定制 + 可重放补丁 + 统一构建产物 + 两种打包方式”。先做可用的轻量静态 SDK，再验证可选的外置内核。两种模式对网页提供相同的能力契约；动态化只是交付与装载方式，不作为轻量化的前提。

本项目提供 Android SDK、内核裁剪配置、内核包规范、装载管理以及验证工具的设计。包来源由接入方注入，项目不保存渠道地址、下载链接、更新服务器配置或私钥，不内置网络下载器。后续样例使用本地生成的测试包。

本阶段交付文档和公开仓库。没有执行 Gecko 完整构建、设备测试或裁剪体积测试；所有收益数字均待实测。源码事实及引用见[源码调研](../../research/upstream-audit.md)。

## 2. 成功标准与非目标

成功标准：

- bundled 模式可完全离线启动，不依赖动态模块、包来源或安装状态。
- external 模式在未安装内核时给出结构化状态；安装合法包后可加载网页，原生库和 Gecko 资源不重复放入基础 APK。
- 每个配置公开能力清单；裁剪后保留能力的行为通过基线回归。
- 任意 Gecko 子进程只使用所属 Runtime 锁定的内核版本，不发生跨包混载。
- 同时报告基础 APK、完整下载量、安装占用、更新峰值空间、进程树内存和启动耗时。
- 上游更新可重放补丁，变更能对应回固定修订及测试报告。

首期不做：从零重写内核、替换系统 WebView provider、实现完整浏览器产品、渠道下载服务、运行中热替换、任意版本 Java/JNI 混用、跨应用共享一份运行时、完整 DEX/Android 资源插件化。也不以关闭沙箱、证书校验或站点隔离换取体积收益。

## 3. 平台与兼容性基线

产品构建适配目标为 `minSdk=26`、宿主 `targetSdk=30`、`compileSdk=35`，发布 arm64-v8a、测试补充 x86_64。允许删除依赖更高平台 API 且不属于必要行为的可选功能；是否完成适配需由 M0/M2 实测确认。SDK 不把接入应用 targetSdk 强制锁定为 30，采用其他合法 target/compile 组合的应用按支持矩阵验证。

三个版本参数分开处理：`minSdk=26` 决定支持下限；`targetSdk=30` 决定宿主选择的系统行为兼容级别；`compileSdk=35` 限定产品编译时的 Android 平台 API/资源。调研修订默认 minSdk=26、targetSdk=37、compileSdk=37.2，属于未修改上游的事实；保留该记录，不将其改写成已经通过 SDK 35 的构建结果。降低 compileSdk 不等于运行系统最高只支持 API 35，也不能据此删除新系统必须遵守的行为适配或 16 KB 页支持。

实施需同步调整 SDK 平台选择、生成的 compile 主/次版本和 target 参数、GeckoView 及产品测试宿主构建配置，使用 API 35 平台资源并消除 37.2 的平台路径假设；不得只修改宿主 Gradle。逐项处理 Java/Kotlin、生成代码、Android 资源及传递依赖对 API 36+ 的要求，核对最终合并 Manifest/APK 和 AAR 元数据。处理流程与删除规则见 [SDK 35 适配](../../compatibility/sdk35-backport.md)。

AAR 的 target 声明不能当作独立进程行为开关，Gecko 及其子进程仍运行在宿主应用的 target 契约下。检查 Gecko Java/native 对设备版本、应用 target、权限、存储、服务和链接器行为的假设；必要时增加局部可配置补丁，保留独立的上游参考构建。产品 lane 的 target 不能高于其 compileSdk；上游 SDK 37.2 / target 37 参考构建使用隔离工作区，不冒充 SDK 35 产品产物。

优先保留锁定上游的 NDK/Rust、JDK、Gradle/AGP 等工具，再验证它们与 SDK 35 平台的组合；compileSdk 降级不要求将所有工具版本一起降级。工具链或依赖确需变更时记录理由和回归结果，不直接替换成任意系统版本。分发环境对应用 target 的额外要求由接入方检查，target 30 适配目标不构成所有环境都可发布的承诺。

调研版本固定为 `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`。原型可从此版本开始；生产发布另选受维护的稳定版，锁定修订、工具链、构建参数和补丁集后重新验证。不得依赖浮动 `main` 作为发布输入。

## 4. 轻量化策略

区分三种动作：编译排除、打包排除、运行时关闭。仅关闭 preference 不得宣称相应二进制已被裁掉。所有删除项需要记录配置、依赖闭包、行为变化、实际减少字节数及回归结果。

### 4.1 配置分层

| 配置 | 定位 | 默认行为 |
| --- | --- | --- |
| upstream-reference | 同修订、同 ABI、同优化级别的原始基线 | 保留上游默认功能，用于对比 |
| compat-lite | 默认 SDK，尽量维持通用网页兼容性 | 使用上游 Lite 构建；优化依赖、资源和打包，保留常用 Web API |
| content-lite | 接入方确认页面能力后的精简版 | 在 compat-lite 上按需排除 WebRTC、语音、HLS、PDF 等候选能力 |

配置与 bundled/external 是两个正交维度。每个发布包只能声明一个配置，不能在安装后临时拼接另一配置的库或资源。

### 4.2 裁剪矩阵

| 功能或内容 | compat-lite | content-lite 候选动作 | 注意事项 |
| --- | --- | --- | --- |
| GeckoView Omni 额外依赖 | 使用已确认的 `--enable-geckoview-lite` | 同左 | Lite 模式不等于完整移除所有遥测路径；构建和联网行为分别审计 |
| 依赖 API 36+ 的可选平台集成 | 删除或回移到 API 35 及以下实现 | 同左，可进一步精简 | 具体清单由实际源码、资源、依赖及编译失败建立；不能仅凭 `SDK_INT` 分支保留无法编译的符号引用 |
| 多 ABI 与符号文件 | 每 ABI 独立产物；符号单独留档 | 同左 | ABI 拆分是交付优化，不能算单 ABI 内核裁剪收益 |
| 测试、调试资源与可选工具 | 发布包排除；测试构建保留 | 同左 | 不误删运行时模块；关闭调试入口不等于删除全部相关代码 |
| HLS/Media3 | 保留 | 评估关闭 `MOZ_ANDROID_HLS_SUPPORT` | 该标志在源码中存在；具体设置语法须由锁定版本 configure 验证 |
| WebRTC | 保留 | 评估 `--disable-webrtc` | 会损失音视频通话、数据通道等能力，必须由接入方选择 |
| Web Speech | 保留 | 评估 `--disable-webspeech` | 要同时检查 Java、WebIDL、资源和错误行为 |
| PDF.js | 保留 | 评估排除内置 PDF 查看器 | 当前包清单包含 PDF.js；需要资源注册和调用链补丁，没有宣称存在通用编译开关 |
| WebExtensions | 保留内部基础设施 | 可关闭外部扩展安装入口；完整移除另做依赖分析 | GeckoView 事件和内置扩展可能依赖相关模块，不能直接删目录 |
| DRM/EME、设备专用能力 | 跟随基线 | 仅在配置实际启用且页面不依赖时评估 | 已经未编入的能力不重复计算节省 |
| 本地化、字体 | 保留必要回退和测试语言 | 按接入方语言清单缩减资源 | 上游已默认排除附带字体；不将现有默认配置当新增收益，中文和 emoji 须实测 |
| Crash reporter | 默认保留，允许构建时移除 | 可选择移除 | 评估 `--disable-crashreporter`；同步审计 native 依赖、Java 回调、crashhelper 服务与打包资源，不配置上传端点 |
| JS/Wasm、DOM、CSS、字体排版、Canvas、WebGL、存储 | 保留 | 首期保留 | 通用页面依赖广，属于内核能力主体 |
| TLS/NSS、权限、沙箱、多进程与站点隔离 | 保留安全策略 | 同左 | 内容服务声明数量不等于同时运行进程数，减少声明不等于降低 PSS |

Crash reporter 作为独立的构建选项，在 bundled 与 external 模式下都允许移除，不强制要求选择 content-lite。保留默认值便于对照；接入方可明确选择无 reporter 产物。上游存在 crash reporting 的 enable/disable 配置入口，但它对 Android crashhelper、相关服务、JNI 回调和资源的排除范围仍需实测，不能假定一个开关就已经移除了整个依赖闭包。只关闭上传或运行时禁用不计为编译裁剪收益。

移除后的能力清单必须标记不提供 Gecko Crash reporter 的 native minidump 与相关诊断回调，接入方不能依赖这些接口获取报告。保留构建侧符号文件不要求设备端保留 reporter。装载健康检查、启动失败恢复与包隔离不能依赖 reporter，详见第 9 节；系统日志和进程退出观察也不等价于完整的 Gecko 崩溃报告。该构建选项进入能力摘要与兼容指纹计算，带/不带 reporter 的宿主和核心不能未经认证混配。

构建优化采用上游支持的 release 优化、strip、链接和 Rust 配置，再评估 LTO 等选项。每次只改变一个配置因素，避免把优化级别差异、ABI 差异和功能裁剪混为一谈。

## 5. 组件与依赖方向

逻辑模块是后续实施的划分建议，当前没有对应实现：

| 模块 | 职责 | 依赖约束 |
| --- | --- | --- |
| runtime-api | 模式、能力、初始化结果与会话管理的薄接口 | 不暴露下载地址，不提供任意路径或环境变量注入 |
| geckoview-bridge | 定制 Java/JNI、Android res、Manifest、生成服务类 | bundled/external 共享同一份构建结果 |
| runtime-bundled | 将匹配的 native 和 Gecko 资源随应用打包 | 不依赖 external 安装器或来源接口 |
| runtime-external | 校验、安装、注册、版本选择、加载租约 | 包来源由接入方提供，不包含 HTTP 客户端 |
| engine-package-tools | 从统一 staging 产物生成 AAR 或签名内核包 | 生成能力清单、文件摘要和兼容指纹 |
| upstream-patches | 配置、资源、Java/native loader、子进程补丁 | 尽量局部修改，保持可重放 |
| test-host | 两种模式及不同配置的验证宿主 | 测试包由本地构建提供 |

依赖方向：接入应用 → 所选 runtime 模式 → bridge → Gecko。打包工具只在构建侧工作。不能让 bridge 反向依赖包下载、渠道 SDK 或业务页面。

V1 保留 GeckoView 的 View/Session/Runtime 使用模型，新增初始化入口负责先选定内核，再允许创建 Runtime。不得由 ContentProvider、Application 静态初始化或后台预加载抢先装载 native 库。

## 6. 两种接入模式

### 6.1 bundled：默认、可独立使用

发布组合 AAR：bridge + 匹配的原生库 + `assets/omni.ja` 等必要资源。加载路径使用应用已安装的库目录和资源路径。升级随应用版本进行，不使用动态安装器、外部包注册表或升级状态机。

接入方只依赖 bundled 组合，初始化后按 GeckoView 模型创建会话与 View。不额外把压缩内核 ZIP 放在 assets 再解压，避免安装体积重复。

### 6.2 external：可选外置内核

基础 APK 保留 bridge、Android 资源和预声明服务，不包含引擎原生库及外置 Gecko 资源。接入方提供包的只读文件描述符或输入流，以及本地信任策略；运行时接收、校验、安装后产生内部的 `InstalledCoreHandle`。

SDK 的对外概念操作为 `install(source)`、`prepare(selection)`、`queryCapabilities()`、`queryState()`。这些是设计名，尚未冻结 API。`source` 表示本地字节来源，不能是项目内置 URL；`selection` 只能选择已验证的包标识，不能直接指定任意 `.so` 路径。

external 缺包时返回 `CORE_NOT_INSTALLED`，由接入方展示其业务 UI；静态模式不出现这一状态。安装失败保留原来健康的内核。首期不自动回退到系统 WebView，以免出现 Cookie、权限和行为契约切换。确需同时内置备用包的接入方，要另行接受其体积成本；默认两种打包模式互斥。

### 6.3 V1 的动态化边界

外置内容包含由同一 Gecko 构建产生的全部必要 `.so`、Gecko JS/注册资源和 `omni.ja`。Android `res`、DEX、R 类、服务类、Manifest 与宿主一起发布。Gecko 资源容器不通过 PackageManager 安装，也不承担 Android 资源 ID 动态映射。

收益是基础 APK 不必携带内核主体，不是任意 Gecko 大版本可独立升级。宿主 Java/JNI、生成配置、资源 ID、服务协议与 native 构成兼容组合：首轮只允许同一次构建拆分出的 bridge/core 配对；后续同 bridge 更新也必须通过配对认证和全量测试。若升级改变 bridge 指纹、服务协议或资源契约，则必须更新宿主。

完整 Java/DEX 插件化列为未来独立课题，不作为 V1 达标条件。

## 7. 已定位的源码改造

以下是“已核查的入口 + 拟议修改”，不代表补丁已经验证。每个入口的不可变源码链接见[调研](../../research/upstream-audit.md)。

| 源码入口 | 已观察行为 | 拟议修改 |
| --- | --- | --- |
| `GeckoLoader.java` | 通过 ClassLoader 找库、加载 mozglue 并设置库目录 | 引入仅初始化一次的包选择；external 以校验后的绝对路径调用 `System.load`；显式设置依赖库根目录 |
| `mozglue/android/APKOpen.cpp` | 从库目录装载 XUL、NSS、SQLite 等；存在局部 XUL override | 审计完整装载闭包，确保所有库来自同包；不能只用 XUL override 代替完整方案 |
| `GeckoThread.java` | 先加载 mozglue；主进程 `-greomni` 指向应用资源路径 | 在首次 native 加载前固定选择；external 改用匹配的 Gecko 资源容器，bundled 保留默认资源入口 |
| `GeckoRuntime.java` | 汇集初始化参数并启动 GeckoThread | 在 Runtime 创建前完成包准备；拒绝进程内更换核心选择 |
| `GeckoProcessManager.java` | 将 active extras 与初始化信息传给子进程 | 传递不可变包身份、兼容摘要及必要的资源访问句柄，不让子进程自行查询“当前最新版” |
| `GeckoServiceChildProcess.java` | 服务创建后启动线程，初始化信息到达后继续装载 | 在首次装载前验证父进程选择，丢失或不匹配立即终止该子进程启动 |
| `AndroidManifest_overlay.jinja`、`ZygotePreload.java` | 声明普通/isolated/zygote 服务；zygote 直接 `loadLibrary` | 保留安全模式；external 不允许在拿到包选择前预加载错误版本，isolated/zygote 资源访问必须单独验证 |
| `geckoview/build.gradle`、`package-manifest.in` | JNI、assets 与资源进入 AAR/包 | 从同一 staging 生成两类交付，检查重复项并生成文件闭包 |

优先保留原 native loader 的依赖语义，不把内部库重构成新的稳定 C ABI。先验证绝对路径、linker namespace、JNI 注册顺序、`DT_NEEDED`、资源注册和各子进程可达性，再决定需要哪些 native 补丁。

Gecko 资源首先验证保持原 `assets/omni.ja` 布局的独立 ZIP 容器，并让资源入口指向该容器；原始 `omni.ja` 直接作为入口是否可用另行测定。两种布局不是已验证的等价方案，M2 必须选定一种并冻结格式。

## 8. 内核包与版本契约

建议包布局如下；文件名与元数据字段尚待 M2 冻结，不是已经实现的格式：

```text
core-package/
  manifest.json
  manifest.sig
  lib/<abi>/<来自构建清单的完整原生库集合>
  gecko-resources.zip
  notices/
```

`manifest.json` 包含 schemaVersion、packageId、上游 revision、patchSetDigest、buildId、ABI、minApi、page-size 支持、bridgeFingerprint、serviceContractDigest、capabilityProfile、capabilityDigest、profileCompatibilityEpoch、securityEpoch、releaseSequence，以及每个文件的相对路径、长度、SHA-256。它不包含渠道名、下载 URL、更新地址或凭据。

bridge 指纹覆盖 Java/JNI 描述、生成 BuildConfig、必要 Android 资源契约和服务声明。打包工具从宿主与引擎共用的构建输入生成，不能只比较 Gecko 版本号。V1 精确配对失败即拒绝；不能仅凭包内自报兼容就放行。

签名方案默认 `SHA256withECDSA` / P-256，签名覆盖 manifest 的原始 UTF-8 字节，避免不同 JSON 序列化导致摘要不一致。`manifest.sig` 的小型签名封套携带有界的 keyId 和签名字节；keyId 仅用于选择宿主已经信任的公钥，不提供路径或引入新信任根。先验证 manifest 签名，再解析且拒绝重复字段、越界数字、异常路径和不支持的 schema；随后按签名文件清单核验内容。签名私钥保存在构建签名环境，本仓库不保存真实密钥。

安装目录位于应用私有内部存储、排除备份；最终目录名由已验签 manifest 的摘要生成，不直接使用外部输入拼接路径。拒绝绝对路径、`..`、符号链接、重复 ZIP 项、压缩炸弹和未列出的可执行文件。限制单包大小与解压总量；校验 ELF ABI、依赖和 4 KB/16 KB 页兼容性。native 文件按只读不可变处理；Android 14 对 DEX/JAR/APK 动态代码的只读要求不能误当 native linker 可行性的证明。

签名只证明包来源，不证明任意包适合当前宿主；仍需校验宿主许可配对、ABI、API、能力和安全版本下限。分发政策由接入方按实际环境判断，外置装载接口不意味着所有环境都允许下载可执行代码。

## 9. 安装、选择与生命周期

external 的包状态：`ABSENT → STAGING → VERIFIED → INSTALLED → PENDING → ACTIVE → HEALTHY`。异常进入 `REJECTED` 或 `QUARANTINED`；进程已固定版本时，新包只能停留在 PENDING，不改变运行中的 ACTIVE。bundled 不使用此安装状态机。

1. 安装器在受控临时目录写入内容，验证签名、文件、兼容条件及空间预算；原子发布安装目录和事务注册记录。崩溃留下的 staging 在下次启动清理，不能当作有效版本。
2. Runtime 创建前选择一个已安装包，并取得覆盖整个 Gecko 进程树的租约。主进程、socket、GPU、content、media/utility 等实际启动的进程继承同一包身份。
3. 使用进程树协调锁，阻止仍有旧版本进程存活时建立不同版本 Runtime。新主进程启动也要识别旧子进程并回收或等待，不能仅检查当前主进程 PID。
4. PENDING 只有在旧 Runtime 进程树完全退出后才可激活。安装和垃圾回收锁与运行租约分离；清理器不能删除 active、pending 或被进程租约持有的包。
5. 首期健康检查要求完成 Runtime 初始化、子进程握手及本地页面执行。通过启动事务标记、就绪握手、Binder 进程死亡通知和超时独立记录启动失败，不能以收到 Crash reporter 回调作为失败检测前提。候选策略为两次启动失败隔离，调试构建可显式重试；已识别的用户取消或系统回收不计为包损坏。无 reporter 且原因无法确定时记录为“健康检查未完成/进程退出”，不伪称已定位 native 崩溃原因。
6. 普通网站的单次 content 崩溃不直接认定内核包损坏。诊断按 packageId/buildId 关联，不记录访问 URL 或页面内容。

健康自检由 external 模块预声明的 `:corecheck` 进程承载，使用独立临时 profile。协调器保证它与正式 Runtime 不并行占用同一组 Gecko 服务：只在尚未创建正式 Runtime 的冷启动窗口运行自检，结束后退出自检进程及全部子进程，再允许正式 Runtime 启动。不能在同一个 PID 先启动测试 Runtime、shutdown 后又重新创建正式 Runtime。若已有正式 Runtime，新包及其自检都等待下次冷启动。

V1 的“版本回退”默认只覆盖候选包通过健康检查之前的阶段；自检失败时旧包仍使用原 profile。正式 profile 一旦被新核心打开或迁移，不能直接交给旧核心；首期不提供其后的自动降级。需要降级时必须存在已验证的 profile 兼容契约或完整一致性快照，不默认复制正在写入的 SQLite 数据库。自检成功也不保证真实页面永不崩溃，正式 profile 打开之后的失败按受限恢复流程处理。

安全下限独立于“上一次可用版本”。低于宿主或已接受安全策略下限的包即使曾经健康也不允许自动回退。没有合格旧包时返回可恢复错误，不循环启动崩溃版本。

## 10. 多进程与内存边界

动态加载不自动降低页面、JS heap、纹理或渲染进程占用，也不保证 `shutdown()` 后卸载 native 库。同一进程只使用一个核心，关闭会话不等于可以重建另一版本 GeckoRuntime。

V1 默认在接入应用承载 View 的进程运行 GeckoRuntime，保留 Gecko 的子进程模型。因此，要完整释放该 Runtime，需退出承载进程及其子进程；本 SDK 不主动终止接入应用主进程，也不承诺关闭页面后完全回收。

独立 `:browser` Activity 是后续可选承载模式：把真实 GeckoView 和 Runtime 一起放在独立进程，由接入方通过页面级入口使用。它不等价于将远端 View 透明嵌回现有布局；远端 Surface、输入、无障碍和生命周期代理属于另一项工程。

isolated 进程不能假定能读取应用私有目录。M2 必须验证受控 FD 传递、只读映射及 native 依赖解析，必要时修改 bootstrap 协议；仅传路径不算完成。zygote 在包选择前加载库也要禁止或改造。不得靠取消原有隔离或改成单进程“解决”动态装载；未达到该门槛时保持 bundled 可用，external 不发布为受支持能力。

## 11. 构建、维护与授权资料

实施阶段建立固定源码修订、固定工具链、配置文件、按序补丁集及每 ABI 构建工作区。native 修改必须完整编译，不能只用 Artifact Build 验证。统一 staging 后生成 bundled AAR、bridge AAR、external 包、符号文件、能力清单和构建来源记录。

每个发布包记录来源修订、补丁摘要、工具链版本、ELF build-id、包摘要及测试报告；符号文件按同一 build-id 留档。优先使用可验证的确定性输入；在实测前不宣称构建逐字节可复现。

只向 SDK 接入方暴露必要的配置，不对外承诺 Gecko 私有实现的稳定 ABI。上游升级先重放补丁，再运行关键装载和兼容验证。安全更新不能长期停留在旧源码，只因裁剪补丁难以合并。

保留 Mozilla 源文件许可证及 notices，打包时生成第三方清单和修改源码的可获取说明。仓库公开不自动替代二进制分发义务；正式发布前按实际文件许可证检查。自有新增代码的许可证另行确定，当前仓库没有声明替换上游授权。

## 12. 阶段与退出条件

以下是方案阶段划分，不是已执行的实施任务或测试结果。详细验证见[验证方案](../../validation-plan.md)。

| 阶段 | 交付 | 继续条件 |
| --- | --- | --- |
| M0 基线与 SDK 35 适配 | 独立上游参考构建、工具链锁定、SDK 35 / target 30 产品宿主、回移与删除清单、基础测量报告 | 产品在 API 35 平台下真实构建，依赖/AAR 元数据可接入；SDK 回移成本与功能裁剪收益分开报告 |
| M1 静态轻量版 | compat-lite bundled、独立裁剪实验、能力差异报告 | 保留功能回归通过；收益可归因；静态模式离线独立可用 |
| M2 动态可行性 | 无网络依赖的本地包安装与装载原型 | 原生库闭包、资源、JNI、所有实际子进程、isolated/zygote、16 KB 均验证；冻结包格式 |
| M3 动态可靠性 | 签名、安装事务、冷启动切换、租约、健康检查和 profile 保护 | 故障注入通过；拒绝混载、不合格降级及错误配对 |
| M4 发布准备 | 两种接入产物、接入文档、来源与许可证资料、升级演练 | 明确支持矩阵；以维护中的生产基线重新通过门槛 |

如果 M1 收益不足，先报告占用结构再决定是否扩大裁剪；如果 M2 平台约束无法在保留安全边界时满足，则 external 暂缓，bundled 继续作为完整可交付方案。不得用实验性动态能力拖累静态接入，也不得把未验证分支描述为已经支持。

## 13. 评审时需要确认的重点

本方案默认保守保留常用网页能力，采用可嵌入 View 的 SDK，首期不承诺任意版本独立升级或页面关闭后完全回收。后续由实际网页清单决定 content-lite 的删除项，由 M0 实测决定预算。独立承载、完整 DEX 插件化和额外 ABI 均需单独扩展范围。已确认要求与默认选择分别记录在[决策记录](../../decisions.md)。

## 2026-10-27 实施状态

[M2本地加载原型](../../builds/2026-10-26-external-core-m2.md)已在一台Android16/4KB设备验证 bundled 与 external 的默认、isolated、app zygote 模式。实际 schemaVersion1 使用同次构建精确配对、11库及omni容器、P-256签名、OFD租约、sealed memfd和APK配置的固定空闲资源FD槽。完整格式与限制以该记录及工具代码为准；上文更广的 epoch/多候选/健康回退协议保留为后续目标。16KB运行设备和平台矩阵未完成，因此 external 仍为实验能力，未跨过完整生产发布门槛。
