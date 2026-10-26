# Lite / 无 Crash reporter / 简体中文 AAR 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 本会话直接执行，结束时独立审阅。

**Goal:** 在已验证 SDK35/target30/min26/arm64 基线上开启 GeckoView Lite，关闭并从 AAR 移除 Crash reporter，只打包 zh-CN 本地化，生成可消费的 AAR。

**Architecture:** 保留上一轮产物与六个 SDK35 补丁；增加独立产品配置和可重放的无 reporter 补丁。使用独立 objdir，固定 Mozilla 中文翻译修订，以实际 ZIP/JAR/Manifest/ELF 和独立宿主编译验证产物。

**Tech Stack:** 原固定 Gecko 修订、相同工具链与 SDK35 平台，Mozilla firefox-l10n zh-CN。

**Spec:** 用户本轮明确要求；中文按简体 zh-CN 处理。已授权直接生成产物，无需重复方案审批。

## Global Constraints

- compileSdk35、target30、min26、arm64，HLS 继续关闭，无 Artifact Build。
- 开启 --enable-geckoview-lite；按该修订实际实现说明其效果，不虚称 Glean 已全部移除。
- --disable-crashreporter，加产品源码补丁排除 Reporter/Handler/Helper/Pull Java 类和 AIDL；不吞掉 Android 默认异常处理。
- 仅 zh-CN 本地化包，不删除网页 Intl/字符集等多语言内容支持。
- 保留上一轮 AAR；新产物仅放本机 ignored artifacts，不上传发布渠道。

## Review Focus

- 编译常量关闭不等于 reporter 类、原生库、Manifest 服务已移除。
- 单 locale 的生成配置与最终 omni.ja 注册、目录必须一致，不能只改文件夹名。
- Lite 开关不自动等于 Glean 本体已裁剪。
- Java/JNI 改动必须重新生成绑定与 native，检查未捕获异常处理没有递归/静默丢失。
- 不能用旧 AAR 或远端预编译 Gecko 通过宿主验证。

### Task 1: 配置、翻译输入和无 reporter 源码闭包

- [x] 旧 AAR 检查确认 reporter 类/库、en-US locale 仍存在，作为实际 RED。
- [x] 固定官方 zh-CN 翻译修订，增加产品 mozconfig 和来源记录。
- [x] 增加独立产品补丁，移除 reporter Java/AIDL 与启动路径；非空 crash handler 设置明确拒绝。
- [x] configure 核验 Lite=true、Crash=false、locale=zh-CN、35/30/26；Java/JNI export 通过。

### Task 2: native、AAR 与独立消费验证

- [x] 独立 objdir 完整 native build binaries，通过后构建发布 GeckoView AAR。
- [x] 核验无 reporter 类/库/服务，omni.ja 仅注册并打包 zh-CN，确认实际中文内容。
- [x] 用独立 SDK35/target30 宿主消费该本地 AAR，通过元数据和打包验证。
- [x] 记录体积、摘要、native 来源和与旧 AAR 的差值；标注设备运行未验证。
- [x] 重放补丁、graphify update、独立审阅后同步现有公开仓库分支/PR。

独立只读审阅（2026-10-26）未发现 Critical、Important 或需报告的 Minor 问题；审阅范围为产品补丁、配置与构建记录一致性，未重复编译或扩展设备验证结论。
