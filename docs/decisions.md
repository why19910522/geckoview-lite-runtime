# 决策记录

更新日期：2026-10-26。状态：方案及实施决策；静态构建与最小真机验证记录已建立。

## 用户已确认的要求

1. 基于 Gecko/GeckoView 修改源码，裁剪不需要的功能。
2. 提供动态加载方案；接入方也可以完全不使用动态化。
3. 项目不保存具体渠道链接，不依赖某个指定分发渠道。
4. 新项目位于 `dev/github/geckoview-lite-runtime`，同步创建 GitHub 公开仓库。
5. 初始阶段先产出方案；现已要求开始实施，先完成静态内核构建验证。
6. Crash reporter 也属于可移除的功能。
7. compileSdk 降为 35，允许删除不受支持的可选功能；具体功能按实际依赖分析与构建失败确定。
8. 静态验证明确使用 compileSdk 35、targetSdk 30。
9. 后续明确要求开启 GeckoView Lite、关闭并移除 Crash reporter、本地化只保留中文；当前按 zh-CN 生成 AAR。
10. 移除 Glean SDK 后端并生成 Release AAR。
11. DevTools 必须保留；删除 PDF.js 和 Web Speech，并优化 C++ 编译。

## 本方案采用的默认选择

这些是方案作者的工程选择，不代表用户已逐项确认。

| 决策 | 默认选择 | 原因 |
| --- | --- | --- |
| 产品形态 | 可嵌入 Android View 的运行时 SDK | 延续 GeckoView 使用方式，先减少底层改动 |
| 接入模式 | 默认 bundled；external 为可选构建依赖 | 不启用动态化时不引入安装器、升级状态机和包来源接口 |
| 裁剪配置 | 默认 compat-lite；content-lite 需要主动选择 | 未获得具体网站清单前，避免默认删除常用 Web 能力 |
| Crash reporter | 独立构建选项，默认保留但允许移除，适用于两种配置与两种接入模式 | 用户要求加入可移除清单；启动健康检查与恢复不能依赖 reporter |
| 发布 ABI | 首期 arm64-v8a；x86_64 用于测试 | 先控制构建与验证组合，其他 ABI 后续扩展 |
| Android 范围 | minSdk=26、宿主 targetSdk=30、compileSdk=35 为产品适配目标；SDK 不强制所有接入应用使用 target 30 | 按用户指定的 compileSdk=35 回移适配，保留未修改上游默认参数作为参考事实；静态构建通过，设备运行待验证 |
| SDK 35 不支持的功能 | 可选平台集成功能优先删除，必要行为用 API 35 及以下实现回移；逐项记录能力变化 | 不用 SDK 36/37 的编译存根或虚报 AAR 元数据绕过构建目标，也不删除安全必要能力 |
| 动态边界 | V1 外置 native 与 Gecko 资源；Java、Android res、Manifest 留在宿主 | 降低类加载、资源 ID 和 Android 服务注册的复杂度 |
| 版本切换 | 全进程冷启动后生效 | Gecko 不是可在同一进程任意卸载、重载的普通插件 |
| 交付模块 | SDK、打包工具、可重放补丁、测试宿主 | 本项目不承担网络传输、渠道运营或更新服务 |
| 上游管理 | 本仓库保存补丁及锁文件，上游在独立工作区检出 | 避免把庞大的上游历史和构建产物混入设计仓库 |
| 生产基线 | 原型可使用调研修订；发布前锁定受维护的稳定版修订并重跑全部门槛 | 调研 main 的 HEAD 不是已经验证的生产版本 |

## 后续确认项与当前处理

| 参数 | 当前处理 | 最迟确认时间 |
| --- | --- | --- |
| 实际网站、H5 应用和必须支持的 Web API | compat-lite 保守保留；用本地测试页建立初始验证集 | 选择 content-lite 裁剪项之前 |
| 包体、安装占用、首屏、PSS 的硬预算 | 先测同修订基线，提供差值；验证文档中的数值仅为候选门槛 | M0 基线评审 |
| SDK 35 / target 30 的实际兼容范围 | GeckoView 与产品宿主按 compileSdk=35 构建；检查源码、资源、传递依赖、AAR 元数据及主子进程行为，未实测前不声明兼容完成 | M0 建立 SDK 35 构建门槛；M2 验证外置加载 |
| 额外语言、32 位 ABI、低端机覆盖 | 中文与英文测试；暂不发布额外 ABI | 扩大支持范围之前 |
| 是否需要关闭页面后完全回收内核进程 | V1 不承诺；独立 Activity 方案作为扩展议题 | 实施独立承载模式之前 |
| 实际分发环境、签名密钥和应用发布策略 | 接入方负责配置与审核；本仓库不保存具体地址或私钥 | external 投产之前 |

上述产品参数不阻止开展基线和加载可行性验证。不得把“尚未确认不需要”的能力直接当作默认删除项。

首轮实施采用[静态产品构建闭包](builds/2026-10-23-sdk35-static.md)：完整 native 目标 + GeckoView AAR + 独立 SDK 35 宿主。上游聚合归档因消息示例依赖 API 37 未通过；没有通过删改上游测试来扩大通过结论。

当前 Lite 中文变体采用独立产品源码补丁移除 reporter；原“默认保留”用于旧构建基线，不覆盖用户本轮明确的移除要求。详见 [Lite 中文 AAR 记录](builds/2026-10-23-lite-zh-CN.md)。

2026-10-26：用户要求继续移除 Glean 并生成 Release AAR，保持 compile35/target30 和仅中文。当前产品额外移除 Glean SDK、FOG/JOG/Trace 后端，保留无操作指标兼容接口；实际 Release 组件及 optimized non-debug native 已验证。详见[Release 记录](builds/2026-10-26-no-glean-release-zh-CN.md)。此前 Debug 变体及“仍保留 Glean”的记录作为历史基线，不代表当前产物。

2026-10-26：按最新要求移除 PDF.js 查看器及 Web Speech 的构建/资源/Java/JNI 闭包，保留 DevTools server、WebRTC、JXL、断词词典和 PDF 打印/生成。PDF 响应由宿主通过 `onExternalResponse` 接管。相同裁剪源码的 `-O2` 与 C/C++ `-Os + ThinLTO` 对照，AAR 从 78.93 MiB 降至 70.94 MiB；选择后者为当前交付。Rust 不启用跨语言 LTO。两版在一台 Android 16 arm64 真机通过最小页面、Speech 接口移除、DevTools 与 PDF 外部响应检查；不据此保证全面性能或设备兼容性。见[本轮记录](builds/2026-10-26-pdf-speech-cxx-size.md)。

2026-10-27：完成 M2 单设备本地外置加载原型：同次构建精确 bridge/core 配对，P-256验签安装、OFD租约、sealed memfd 与 FD-offset 加载，保留 isolated/zygote 沙箱。资源 FD 槽由 APK 契约指定，默认900，冲突失败且不覆盖；共享162.88MiB容器增加内存压力。外置/静态三模式页面、RDP与PDF检查通过；16KB设备、其他Android/OEM及M3自动可靠性仍未完成。没有渠道地址、下载器、自动更新或GC。详见[本轮记录](builds/2026-10-26-external-core-m2.md)。
