# 可选本地外置内核安装器（M2 实验）

本模块只在 external 模式依赖。bundled 模式继续直接使用完整 GeckoView AAR，不引入安装器。本模块没有网络客户端、分发地址、自动更新或业务 UI。

## 接入顺序

1. 使用同次构建的 bridge AAR 及签名 core-package.zip；bridge 的资产包含精确配对契约。Java、JNI 描述、Android 资源与服务仍随 APK 更新，只允许当前契约对应的核心，不能跨 Gecko 版本替换。
2. 应用自行配置可信 P-256 公钥，并提供本地输入流。在工作线程创建 `new CoreStore(context, trustedKeys)`，调用 `install(localInputStream)`，得到受控包 ID。
3. 在创建任何 GeckoRuntime 或预加载 Gecko 之前调用 `prepare(packageId)`。安装与准备都涉及磁盘与摘要核验，不得放在 UI 线程。准备后才能按原 GeckoView API 创建 Runtime、Session 和 View。
4. `queryState()` 返回 ABSENT / INSTALLED / PREPARED；失败通过 `CoreException.code` 返回。`capabilityProfile()` 返回与包绑定的裁剪配置。PREPARED 相对于当前 CoreStore 实例：新实例可显示 INSTALLED，调用同包 prepare 后才显示 PREPARED；进程级选择仍不可更换。PREPARED 只说明选择已锁定，不代表 native/页面健康检查已完成。

```java
Map<String, PublicKey> keys = Collections.singletonMap("your-key-id", trustedP256PublicKey);
CoreStore cores = new CoreStore(context, keys);
try (InputStream input = callerProvidedLocalStream) {
    String id = cores.install(input);
    cores.prepare(id);
}
// 随后在主线程创建 GeckoRuntime、GeckoSession 和 GeckoView。
```

包来源和公钥由接入方注入，不能让包内 keyId 引入新的信任根。测试宿主的 local-test keyId/公钥只是本机测试配置，真实私钥不在仓库。

## 安装与进程边界

签名覆盖 manifest 原始 UTF-8 字节。先验签，再解析 manifest，拒绝重复 JSON 字段、越界整数、异常 ZIP 项和超限；精确比较 bridge 契约与核心/文件 SHA-256。只写应用私有 no-backup 目录，路径由 manifest 摘要生成，不按外部路径解压。临时包与有效目录分离，在安装互斥锁下先同步全部文件、最终权限、lease 与 staging 目录，再原子改名并同步 root；首次创建 root 同时同步新祖先及父目录。同步错误不会报告成功。`.lease-<id>` 是固定协调 inode，安装目录内 lease 为发布完整性标记；运行期只锁定 root 下的固定协调文件，不依赖硬链接（部分 Android 策略禁止创建硬链接）；重新输入合法同包可修复缺少必需文件的目录，修复须取得独占租约，有活动租约时返回 CORE_IN_USE。有效已有安装不被覆盖；没有通用磁盘损坏修复承诺。没有清理已安装包的 API。

准备时重新验证安装文件；只读 core 与 lease FD 交给 bridge。每个进程再次核对 APK 契约与容器摘要，并通过小型原生引导库持有该包的共享 OFD lease（关闭临时 dup 不会释放）；父进程只复制一次到禁止写入/扩缩容的 sealed memfd，以只读 FD 传递；子进程通过 Binder 继承固定身份和自己的 FD，不能自行选择最新版本。同进程选择不同包被拒绝。准备和首次 native 加载前，在 bootstrap 的实际 linker namespace 内预检整个非系统原生闭包；已有同名库会失败。加载使用包含 packageId 的路径名称，并通过固定导出符号的 dlsym/dladdr 来源与所选 memfd 对照。宿主必须在 prepare 前停止其他组件加载这些 SONAME，并在引擎生存期保持这一约束；预检不提供原子 linker namespace 隔离。zygote 仅延后 native 预加载，保留原 isolated 服务与进程模型。

Gecko 的跨进程资源 URI 要求固定资源 FD 槽：由 bridge 契约配置 `resourceFd`（默认900，可选64–1023），必须在全部进程中空闲。冲突会失败并保留已有宿主 FD；不自动寻找另一编号或覆盖。设备不支持 OFD、memfd/seals 或相关权限时返回 `CORE_LOAD_UNSUPPORTED`，不会关闭隔离。

当前精确配对契约只允许一个 core，尚无“同 bridge 多个可升级版本”的认证能力。不会在运行中替换核心，也不会对安装目录执行自动 GC。后续 M3 才实现候选自检、失败计数/隔离、支持多候选版本后的跨冷启动选择协调、profile 迁移与受限回退；本模块不能据此用于无验证的生产自动更新。

## 测试

纯 Java 真实签名/文件测试，不依赖 Android mock：

```sh
python3 runtime-external/tests/run.py \
  --pair "$LOCAL_PAIR_DIRECTORY" --public-key "$LOCAL_PUBLIC_KEY_DER" \
  --java-home "$JAVA_HOME"
```

Android 集成须用对应 bridge 编译并在真机验证主进程、isolated/zygote 与实际子进程。仅通过 Java 测试不证明动态链接器或沙箱访问可用。

文件与目录同步的调用顺序、故障传播和恢复已有测试；尚未进行物理断电、内核崩溃或文件系统崩溃验证。
