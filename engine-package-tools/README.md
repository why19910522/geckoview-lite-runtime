# 从同次 Release 构建生成外置配对

工具只处理本地文件，不下载内核，不保存私钥或分发地址。先按构建文档生成完整 Release AAR，再生成桥接引导库与精确配对包。固定 arm64 / minApi26，原生库 ELF 与 core 容器的 ZIP 载荷均要求 16KB 对齐。

```sh
python3 engine-package-tools/build_bootstrap.py \
  --ndk "$ANDROID_NDK_ROOT" --output "$LOCAL_OUTPUT/libgeckolite_bootstrap.so"
python3 engine-package-tools/package_core.py \
  --aar "$FULL_RELEASE_AAR" --pom "$MATCHING_PUBLICATION_POM" \
  --bootstrap "$LOCAL_OUTPUT/libgeckolite_bootstrap.so" \
  --output "$NEW_PAIR_DIRECTORY" --private-key "$PRIVATE_KEY_OUTSIDE_REPOSITORY" \
  --key-id "$TRUSTED_KEY_ID" --revision "$PINNED_GECKO_REVISION" \
  --profile lite-zh-CN-no-glean-no-pdf-speech --sequence 1
```

`--output` 必须是新目录。P-256 私钥由调用方管理，运行期只配置可信公钥。OpenSSL CLI 用于构建期签名。若 Maven 目录包含多个 snapshot，必须从该版本 `maven-metadata.xml` 的无 classifier AAR/POM 项选取同次发布，不能任取一个文件。

输出：

- `bridge.aar`：原 Java/JNI 描述、Android 资源和服务，以及一个小型 `libgeckolite_bootstrap.so`；没有 Gecko 的 11 个库或 `omni.ja`。
- `core-package.zip`：恰好包含原始签名 manifest、签名封装和 `core.apk`。内层容器不是交给 Android 安装的 APK；它保存原样的 11 个引擎库与 `assets/omni.ja`，stored ZIP 格式。
- `host-contract.json`：APK 内同份契约的可读副本，固定 manifest/core 摘要、长度、ABI、修订、配置、构建身份和原生依赖加载顺序。
- 可选 `maven/`：若提供原发布 POM，生成原 artifactId 加 `-bridge` 的本地坐标，保留依赖和许可证信息。宿主独占该本地仓库中的 GeckoView 坐标，不回退到官方预编译引擎。

完整原 AAR 继续作为 bundled 输入，安装器和引导库只在 external 配对中依赖。两种接入不能同时放入同一 APK。

`--resource-fd` 可指定所有进程共用的资源 FD 槽（64–1023，默认 900）。Gecko 会跨进程序列化资源 URI；宿主需在准备前留空该槽。系统限制或槽被占用会直接报错，不覆盖已有 FD。

引导库通过 Linux OFD 锁持有包租约。父进程将已验证容器复制到封闭写入/扩缩容的 memfd，再以只读 FD 传递给所有子进程；引擎用 `android_dlopen_ext` 读取 FD 偏移，ZIP reader 直接 dup/map，不按路径重开文件，避免 isolated UID 重新打开应用私有文件。该副本由同一进程树共享，会增加运行期间的内存/交换空间压力；基础 APK 变小不等于内存降低。设备拒绝相关系统调用或执行映射时应直接失败，不能自动关闭进程隔离。具体通过模式与设备范围以构建记录为准。

```sh
GECKO_CLANG="$PINNED_CLANG" GECKO_ZIPALIGN="$ZIPALIGN" \
  python3 -m unittest discover -s engine-package-tools/tests -v
```

测试使用真实 arm64 ELF、ZIP 与 OpenSSL 验签。Android FD 权限、租约、动态链接和 Gecko 页面仍须真机验证。
