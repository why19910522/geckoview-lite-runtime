# M2 外置内核 bootstrap 补丁

固定 Gecko `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`，依次应用 `sdk35`、`lite-zh-CN`、`no-glean-release`、`pdf-speech`、本目录 `series`，使用 `patch -E -p1 --fuzz=0`。保留所有 isolated 服务与 app zygote 声明。

Java、Android 资源和服务留在 bridge。新增 `ExternalCore` 在首次 Gecko native 初始化前固定 APK 契约中的身份；完整重验核心摘要、只读描述符和共享 OFD 租约。父进程只创建一次完全封闭写入/扩缩容的 memfd，各子进程继承同一容器。小型引导库由 `engine-package-tools/native/bootstrap.c` 构建，只添加到 bridge；完整 bundled AAR 无此依赖。

`android_dlopen_ext` 按可信 APK 契约内的 ZIP 偏移加载完整依赖闭包，并从同一 mozglue handle 注册其 11 个 JNI 方法。APKOpen 在明确已预加载时只查找已加载的 SONAME/Bootstrap，不重新打开文件或回退到另一份引擎。普通 bundled 路径保留。

Android 的 `XRE_GetFileFromPath` 及 ZIP reader 对严格数字 `/proc/self/fd/N`、只读 regular FD、完整 seals 特判：保留 FD 路径并 dup/import 到 NSPR。其他路径继续使用原 canonicalization/open 行为。此特判解决 memfd 无真实文件名以及 isolated SELinux 只允许继承 FD 的问题。

Gecko 会跨进程序列化资源文件 URI。契约中的 `resourceFd` 必须在所有进程一致，默认 900，可在打包时配置 64–1023。使用 `F_DUPFD_CLOEXEC` 原子取得空槽；槽已被宿主占用或超出设备限制时直接失败，绝不使用 dup2/dup3 覆盖已有 FD。接入方须在准备前为该槽留空。该限制属于当前 M2 原型边界，后续独立资源协议可以移除它。

子进程在 Binder worker 上完成身份/摘要/租约选择后才返回 STARTED_OK，再由原 GeckoThread 初始化 native。父端记录包 ID、子 PID 与进程类型，便于核对 isolated 日志受限的设备。传递和接收端均关闭临时描述符，所选 core 与 OFD lease 保留至进程结束。相同选择幂等，不同选择要求整个进程树冷启动。

zygote 保留原隔离模型，仅跳过选择前的引擎预加载；实际子进程收到 Binder 选择后加载。同次完整 AAR 继续支持 bundled，不引入安装器。

新增 native 源码后必须真实编译 `binaries`、`libs`、`stage-package` 并发布 Release AAR，不能只重打 Java 包。格式、接入与测试见 [拆包工具](../../engine-package-tools/README.md)、[安装器](../../runtime-external/README.md) 和 [构建验证记录](../../docs/builds/2026-10-26-external-core-m2.md)。
