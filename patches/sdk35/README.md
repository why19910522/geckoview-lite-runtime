# SDK 35 源码补丁

输入为 `upstream-research.json` 固定的 Firefox 修订。按 `series` 顺序应用；本目录不提供预编译内核或下载渠道配置。

| 补丁 | 原因 | 行为变化 |
| --- | --- | --- |
| 0001-sdk35-target30-platform.patch | 原始 configure 在只有 API 35 的 SDK 中查找 `android-37.2` 并失败 | 编译主/次版本改为 35/0，target 改为 30，整数平台使用 `android-35` 目录；min 保持 26 |
| 0002-sdk35-cursor-blink-default.patch | 上游光标方法用 API 35 android.jar 编译出现 4 个缺失符号 | 不读取 Android 36.1 的系统光标闪烁设置，统一沿用已有 500 ms 默认值；JNI 方法名称、签名保留 |

光标方法已经过定向 javac 失败→成功验证；这不能代替整个 GeckoView 构建和设备运行验证。完整构建状态以 `docs/builds/` 的实际记录为准。

在源码根目录对每个补丁执行 `patch -p1 -i <补丁绝对路径>`，应用前可加 `--dry-run`。已应用源码可以使用 `--dry-run --reverse` 检查反向应用；不要在不匹配修订上强制忽略上下文应用。

这些补丁修改 Mozilla Public License 2.0 覆盖的上游文件，修改后的源文件继续保留原有 MPL 2.0 声明。完整修改源码由固定上游源码与本目录补丁共同重建；不声明替换上游许可证。
