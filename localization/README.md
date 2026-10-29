# 简体中文本地化输入

`zh-CN-source.json` 固定 Mozilla 官方翻译仓库修订与 zh-CN 子树。翻译源码仅放 ignored 本机工作区，不将全部语言翻译或预编译语言包放入项目。

在独立目录获取固定修订，sparse checkout 只取 `zh-CN`；设置 `GECKO_L10N_BASE` 为包含该 zh-CN 子目录的仓库根目录。产品 mozconfig 使用 `--enable-ui-locale=zh-CN` 与该 l10n base。本次不打包其他 UI locale，也不以重命名英文资源代替中文翻译。

例如在本机 ignored 目录中执行（`$LOCALE_CHECKOUT` 为新的本机翻译目录）：

```sh
git clone --filter=blob:none --sparse --no-checkout https://github.com/mozilla-l10n/firefox-l10n.git "$LOCALE_CHECKOUT"
git -C "$LOCALE_CHECKOUT" sparse-checkout set zh-CN
git -C "$LOCALE_CHECKOUT" checkout 352d5c0f72bddbb50d1869178eaa85b9c83d095e
export GECKO_L10N_BASE="$LOCALE_CHECKOUT"
```

需要从零重建对象目录时，先生成公共文件再编译，不能只运行增量快捷目标：

```sh
mise x python@3.12.14 uv@0.13.0 -- python mach build pre-export
mise x python@3.12.14 uv@0.13.0 -- python mach build export
mise x python@3.12.14 uv@0.13.0 -- python mach build binaries
mise x python@3.12.14 uv@0.13.0 -- python mach build merge-zh-CN
mise x python@3.12.14 uv@0.13.0 -- python mach build misc
mise x python@3.12.14 uv@0.13.0 -- python mach build libs
mise x python@3.12.14 uv@0.13.0 -- python mach build stage-package
IS_LANGUAGE_REPACK=1 mise x python@3.12.14 uv@0.13.0 -- python mach gradle \
  geckoview:checkDebugAarMetadata geckoview:assembleDebug \
  geckoview:publishDebugPublicationToMavenRepository
```

最后的 `IS_LANGUAGE_REPACK=1` 仅用于已经完成 native、Java/JNI、中文资源和 stage-package 之后的 AAR 发布，采用上游提供的单语言重打包模式。它避免 Gradle 再调用硬编码 en-US 的 FasterMake 资源任务；不能用于跳过前面的构建，也没有关闭 AAR 元数据检查。

语言资源与最终包装的完整验证见本次构建记录。
