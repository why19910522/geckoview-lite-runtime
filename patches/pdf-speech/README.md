# PDF.js / Web Speech 产品裁剪

固定 Gecko 修订 `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`，依次应用 `sdk35`、`lite-zh-CN`、`no-glean-release`、本目录四组 `series`；每个补丁使用 `patch -E -p1 --fuzz=0`。当前是定制产品分支，不能将其作为任意上游桌面产品的通用开关补丁。

`--disable-pdfjs` 排除 PDF.js 构建目录、组件注册、stream converter、默认首选项及查看器资源；同时移除 GeckoView 启动导入、actor 和初始化观察者，`isPdfJs` 返回 false，Troubleshoot 明确返回未构建。打包阶段排除固定翻译中的 pdfviewer/aboutPDF 专用资源，不修改上游翻译仓库。页面收到 PDF 时由宿主 `GeckoSession.ContentDelegate.onExternalResponse` 处理；本项目探针关闭响应流，实际应用应实现保存或外部打开。

保留 PDF 格式打印/生成及普通保存流程；其中 URL 比较常量不等于 PDF.js 加载入口。远程测试模块中的 PDF reftest loader 尚有查看器 URI，但该专用测试不再可用；DevTools 正常调试不依赖它。不声称删除产物中的所有 PDF 字符串。

使用上游 `--disable-webspeech` 关闭 DOM/WebIDL 与 native speech 构建，并同步移除 Android `SpeechSynthesisService` Java 编译、sources/javadoc 及 WrapForJNI 生成入口。原始源码文件仍可存在于完整上游源码树，交付 AAR 不包含该服务。产品 Gradle 对重新启用 PDF.js/Speech 的不一致配置直接失败。

DevTools server、socket 安全模块和 GeckoViewRemoteDebugger 保留；两版 477 个 DevTools 资源文件与前一版内容哈希一致，并通过真实 RDP 握手、listTabs、getTarget 和 console evaluation。宿主默认不打开远程调试，仅显式 `runtimeProbe=true` 时开启。

使用 [O2 对照](../../build-config/mozconfig-sdk35-trim-o2-zh-CN-arm64) 或 [Os + ThinLTO](../../build-config/mozconfig-sdk35-trim-os-thinlto-zh-CN-arm64)，按 configure、pre-export、export、binaries、merge-zh-CN、misc、libs、stage-package 顺序完整源码构建，再执行：

```sh
IS_LANGUAGE_REPACK=1 python mach gradle \
  geckoview:checkReleaseAarMetadata geckoview:assembleRelease \
  geckoview:publishReleasePublicationToMavenRepository
```

沿用固定 Python/uv 和工具环境；每版独立 objdir。`IS_LANGUAGE_REPACK=1` 只用于资源和 native 已完整生成后的最终打包。用 `scripts/verify-trim-aar.py` 检查实际 AAR，并核对源库到 APK 的来源、SDK、ELF/ZIP 对齐；不能只检查配置文本。
