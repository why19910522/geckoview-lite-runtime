# 外置内核 M2 本地加载原型

2026-10-26 开始，2026-10-27 完成单设备验证。现可从同次裁剪版 Release 构建生成完整 bundled AAR、bridge AAR 和签名内核包；外置与静态对照均在一台 Android 16 真机通过默认、isolated、app zygote 三种模式的页面、真实 DevTools RDP 和 PDF 外部响应检查。当前仍是实验原型，尚未完成平台矩阵及 M3 自动可靠性门槛。

固定 Gecko 修订 `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`，沿用此前固定翻译、工具链和 `mozconfig-sdk35-trim-os-thinlto-zh-CN-arm64`。保持 compileSdk35 / targetSdk30 / minSdk26、arm64、Lite 和仅 zh-CN；移除 reporter、Glean SDK 后端、PDF.js、Web Speech，保留 DevTools、WebRTC、JXL、断词及 PDF 打印。C/C++ 继续 `-Os + ThinLTO`，不启用跨语言 LTO。完整摘要和输入见[机器记录](2026-10-26-external-core-m2.json)。

## 交付与体积

产物只保存在本机 ignored 的 `artifacts/external-core-m2/final/`，未上传 Release。该目录包含对应本地 Maven bridge 仓库、验证 JSON 和本机测试公钥；私钥不进入仓库。

| 产物 | 字节 | MiB |
| --- | ---: | ---: |
| 完整 bundled Release AAR | 74,396,312 | 70.95 |
| bridge Release AAR | 1,381,479 | 1.32 |
| 签名 core ZIP | 73,020,905 | 69.64 |
| 可选安装器 Release AAR | 18,347 | 0.02 |
| 外置 Debug 探针 APK | 4,318,572 | 4.12 |
| bundled Debug 对照 APK | 174,690,645 | 166.60 |
| 安装后的内核容器 | 170,790,884 | 162.88 |
| bridge 原生引导库 | 9,048 | 0.009 |

外置基础 APK 减少约 97.53%，完整引擎仍需另外安装。bridge 只包含一个小型 bootstrap SO，不包含 Gecko 的 11 个库或 `omni.ja`。内核容器保留这 11 个库与资源的原始字节；全部库与实际 `dist/geckoview` stage 摘要一致，完整 AAR、外置包和 bundled APK 之间逐一核对。此轮修改了 Android native bootstrap/资源访问，已真实重编 C++ 并重新 stage/发布；没有用旧库代替修改后的源码。

父进程把容器复制一次到 sealed memfd，供整个进程树共享，约 162.88 MiB 的内存后备会增加内存/交换空间压力。未测量 PSS 或全面性能，不能把 APK 体积收益解释为内存降低。保留下载 ZIP、安装容器和 APK 的总磁盘占用也高于单一基础 APK。

## 实际加载边界

1. 构建工具生成 P-256 签名的原始 UTF-8 manifest，APK 契约精确固定 manifest/core 摘要、长度、ABI、修订、裁剪配置、bridge 指纹、build ID、native 加载顺序/ZIP 偏移与资源 FD 槽。只允许同次构建配对。
2. 可选安装器只接收宿主提供的本地输入流与可信公钥。验签、严格 JSON、长度/路径/重复项及全部内容摘要检查通过后，在应用 no-backup 目录原子发布；失败不覆盖有效安装。没有渠道地址或下载器。
3. `prepare` 在第一次 Gecko native 初始化前固定选择。readonly core/lease FD 交给 bridge；复制成禁止写入/扩缩容的 memfd，再校验摘要，持有 Linux OFD 共享租约。重复 prepare 幂等，换包要求进程树冷启动。
4. 子进程在 Binder worker 完成契约、摘要、只读 FD 与租约检查后才返回 STARTED_OK。引导库使用 `android_dlopen_ext` 的 FD/offset 模式加载依赖闭包，注册同一 mozglue handle 的 11 个 JNI 方法；APKOpen 只复用已经加载的库。
5. XRE 路径与 ZIP reader 仅对严格数字、只读、regular 且完全 sealed 的 FD 特判，直接 dup/map；普通路径仍走原逻辑。zygote 跳过选择前的引擎预加载，保留 isolated 服务和沙箱。

Gecko 会跨进程序列化资源 URI，当前契约要求全部进程使用同一空闲 `resourceFd`，默认900，可配置64–1023。通过 `F_DUPFD_CLOEXEC` 原子占槽；冲突或设备限制时失败，绝不覆盖已有 FD。宿主需为该槽留空，后续独立资源协议可以移除这一限制。内核不支持 OFD/memfd/seals 或相关策略时失败，不自动取消隔离。

格式以 `engine-package-tools/package_core.py` 的 schemaVersion1 实际输出为准。方案中的安全 epoch、多候选版本、健康隔离和 profile 保护字段属于后续阶段，尚未伪装为已实现的升级协议。`PREPARED` 与子进程选择确认均不代表页面健康。

## 验证证据

| 检查 | 实际结果 |
| --- | --- |
| 真实 ELF/ZIP/OpenSSL 拆包测试 | 3/3 |
| 真实签名、JSON、损坏/异常输入、失败保留安装 | 22 项通过 |
| 并发安装 | 两个独立 JVM 对同一目录安装，收敛到同一有效包，无暂存残留 |
| 输入中断 | 结构化失败，保留旧包，清除暂存 |
| OFD、sealed 与 FD 冲突 | 关闭 dup 后租约仍阻止独立写锁；已选核心拒绝可写别名写入；资源槽冲突被拒绝且不覆盖 |
| 进程选择 | 重复准备成功；不同包被拒绝；缺包在 Gecko 初始化前失败 |
| 产品构建 | C++ binaries/libs/stage、Release AAR 发布、双宿主 Debug/Release 构建通过 |
| 裁剪门槛 | 无 reporter/Glean SDK 后端/PDF.js/Speech，中文与 DevTools 保留，SDK 与优化配置通过 |
| 产物门槛 | 签名、全部载荷 SHA、16KB ZIP 对齐、双 APK 实际 SDK35/30/26 元数据通过 |
| 源码补丁 | 12 个补丁、60 条源码路径零 fuzz 重放，与实际编译源逐字节相同 |

设备为 Vivo V2536A，Android16 / API36 / 4KB pages。Debug 探针消费上述 Release AAR。以下六组均完成 DOM 中文、Canvas、WebGL、空 Wasm、IndexedDB/WebRTC 接口存在性、Speech 缺失检查；真实 RDP 握手、listTabs、getTarget、thread/inspector、console evaluation；PDF 回调 `application/pdf` 和414字节响应。外置每组的1个GPU和3个内容进程均完成同一 packageId 选择确认；isolated/zygote 内容进程使用独立 isolated UID，zygote 组实际使用 zygoteTab 服务。

| 模式 | external | bundled 对照 |
| --- | --- | --- |
| 默认子进程 | 通过 | 通过 |
| isolated | 通过 | 通过 |
| app zygote + isolated | 通过 | 通过 |

结构化探针结果保存在自身私有文件，测试后拉取到本机 `artifacts/external-core-m2/{external-final,bundled-final}/`；覆盖部分设备不输出应用 INFO 日志的情况。子进程确认历史最多128条，是选择诊断，不能作为存活/健康状态。测试仅访问 APK 内的本地页面/PDF，不涉及用户浏览数据。

## 重建与复核

在此前四组补丁后应用 [external-core](../../patches/external-core/README.md)，沿用固定环境执行：

```sh
python mach build binaries
python mach build libs
python mach build stage-package
IS_LANGUAGE_REPACK=1 python mach gradle \
  geckoview:checkReleaseAarMetadata geckoview:assembleRelease \
  geckoview:publishReleasePublicationToMavenRepository
```

已有中文资源与配置不变，此次增量 native 编译通过；从零构建仍按[上一轮完整顺序](2026-10-26-pdf-speech-cxx-size.md)准备对象目录。新增小型 bootstrap 使用明确指定的 NDK r30 `30.0.15729638`、API26 编译，16KB ELF alignment。随后按[拆包工具](../../engine-package-tools/README.md)、[安装器](../../runtime-external/README.md)、[双模式宿主](../../smoke-host/README.md)操作。使用 `smoke-host/scripts/verify-external-pair.py --help` 复核签名、原始载荷、APK 与 SDK；不要只检查配置文本。

构建依赖远端重验证曾返回缺失项；使用 ignored 的本地 Maven 依赖镜像恢复已有固定输入，后续双宿主 offline 构建通过。镜像保留 Gradle module 的变体文件名与摘要，不改变依赖版本。没有声称当前环境已通过全新在线依赖解析或上游聚合应用/测试套件。

## 尚未覆盖

- 16KB 只验证 ELF/ZIP 结构；尚无16KB内存页设备运行证据。API26–35和其他 OEM 的运行矩阵未完成。
- 尚未实现自动更新、候选健康检查、故障计数/隔离、profile 迁移/受限回退、跨版本进程协调或 GC。没有运行中卸载/替换能力。
- 无独立 bridge-compatible 内核升级；Java/JNI/Android 组件仍随 APK 发布。生产签名、公钥轮换、维护基线及发布策略由后续阶段落实。
- 未触发的 socket/RDD/utility 等进程类型、真实网站、媒体通话、存储读写和长期稳定性未验证。页面计时为探针结果，不是性能基准。
- unsigned Release 宿主已构建/检查，未安装执行；真机结果来自消费 Release AAR 的 Debug 探针。
