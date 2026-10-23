# SDK 35 Static GeckoView Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 从固定 Gecko 源码完整编译 arm64 静态内核，验证 compileSdk=35、targetSdk=30、minSdk=26，并按真实失败决定兼容补丁。

**Architecture:** 上游源码和完整工具链留在项目主目录的 ignored `upstream/`，补丁、mozconfig 和验证记录留在实施分支。先生成静态 GeckoView AAR，再用 SDK 35 的宿主消费该 AAR，核对最终 APK；此轮不实现动态加载。

**Tech Stack:** 固定 Gecko 修订、Mozilla mach/bootstrap、Android API 35、arm64 Android NDK/Rust/C++、Gradle/AGP、现有 macOS arm64 构建机。

**Spec:** [总体方案](../specs/2026-10-22-geckoview-lite-runtime-design.md)；[SDK 35 回移约束](../../compatibility/sdk35-backport.md)。

## Global Constraints

- compileSdk=35，产品宿主 targetSdk=30，minSdk=26，发布 ABI arm64-v8a。
- 上游修订 `d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a`；不使用 Artifact Build 代替 native 编译。
- 保留静态独立接入，不实现渠道下载、动态安装或完整浏览器产品。
- 不引入 API 36/37 的 android.jar 或平台存根通过 SDK 35 检查。
- 不虚报 AAR 元数据；不删除 TLS、沙箱、站点隔离等必要能力。
- 可选功能只按实际编译失败删除或回移；每项附错误、变更和验证证据。
- 原始构建日志、源码、工具链、大型产物和本机路径配置不提交公开仓库。

## Review Focus

- 实际生成的 SDK 参数必须为 35/30/26，不能只修改宿主配置。
- AAR 的 native 库来自本次完整源码编译，不能误用下载的 Gecko native 产物。
- 传递依赖真实支持 SDK 35，不能通过改写 minCompileSdk 隐藏不兼容。
- API 36+ 引用可能出现在源码、生成代码、资源或测试宿主，按边界检查。
- 打包成功不代表设备运行验证通过，两类结论分别记录。

## 执行约定

用户已明确要求按已确认方案开始实施；本计划作为执行记录，不再要求重复审批。默认在本会话直接执行，结束时进行独立审阅。原生 worktree 工具因聊天根目录非 Git 仓库而失败，使用 Git worktree 回退。配置与上游补丁以实际 configure/build/消费检查作为验证，不为版本常量写镜像式单元测试。

### Task 1: 固定源码、工具链及 SDK 35 配置

**Files:** `upstream-research.json`（读取）；新增 `build-config/mozconfig-sdk35-arm64`、`patches/sdk35/series` 及实际必要的 patch；本机 `upstream/`（ignored）。

**Interfaces:** 输入固定源码修订与本机 API 35 平台；输出可重复应用的补丁集、mozconfig、工具链清单和 configure 日志。

- [x] 下载固定修订源码，验证压缩包和展开目录；记录来源摘要。
- [x] 获取该修订要求的 Python、Rust/NDK/Clang/Java 等工具，不修改全局运行时默认值。
- [x] 用 API 35 的构建尝试暴露上游参数/平台查找不匹配，保存原始失败。
- [x] 修改统一 SDK 选择与生成参数，只采用必要的配置补丁；确认导出 35/30/26。
- [x] 执行 configure；读取完整结果，固定本次工具版本和工作目录。

### Task 2: 完整静态内核编译与必要回移

**Files:** `patches/sdk35/*.patch`、`patches/sdk35/series`、本机完整构建日志；新增 `docs/builds/2026-10-23-sdk35-static.md`。

**Interfaces:** 消费 Task 1 的源码与配置；输出本机 arm64 native 库、Java/资源构建和有据可查的兼容补丁。

- [x] 先执行完整 `mach build`；若其附带的上游示例应用失败，明确记录，并以 `mach build binaries` + 独立 AAR/宿主完成产品闭包，不启用 artifact mode。
- [x] 对实际失败逐个定位配置、平台 API、生成资源或依赖根因。
- [x] 必要时删除可选 API 36+ 集成或回移到公开兼容实现，每次只处理一类已复现错误。
- [x] 重跑失败阶段，再继续完整构建；保留失败日志和最终成功日志。
- [x] 保存本地构建来源、配置及补丁摘要；未通过阶段不能标记成功。

### Task 3: AAR、宿主 APK 与编译契约验证

**Files:** 必要的最小静态宿主配置、验证命令记录、构建报告；本机 `artifacts/`（ignored）。

**Interfaces:** 消费本地构建 AAR；输出静态宿主 APK、包文件清单、SDK/ABI/元数据验证结果。

- [x] 使用该修订实际支持的 Gradle/package 任务生成 AAR，记录准确任务名。
- [x] 用 compileSdk=35/targetSdk=30 的最小宿主消费本地 AAR，验证传递依赖。
- [x] 用 AAPT/ZIP/ELF 工具核对最终 Manifest、AAR 元数据、arm64 native 库和 Gecko 资源。
- [x] 报告 AAR/APK 大小、摘要及 native 编译来源；运行测试未执行则明确列出。
- [ ] 执行文档、补丁应用及构建产物检查，提交代码/补丁/记录并做独立审阅。

## 完成条件

完整 native 构建、静态 AAR 和 API 35 宿主 APK 均通过，最终 target=30/min=26，且有可复核的来源与日志。设备运行、广泛网页回归、性能测试和动态加载不属于本轮完成结论。如果外部下载或工具环境阻止构建，记录实际阻塞阶段与可复现命令，不能用设计文档或单独的源码扫描替代构建成功。

## 实施范围调整（真实编译证据）

`mach build` 已完成 native 生成，聚合归档随后在 `messaging_example:checkDebugAarMetadata` 因其 Core/Core KTX 1.19.1 要求 API 37 失败。上游聚合任务还包括 AndroidTest、test_runner 与完整示例应用。此轮使用完整 `mach build binaries` 和 GeckoView AAR 发布目标，再由本项目独立 SDK 35 宿主检查产品传递依赖。保留上游测试和校验，不为本轮静态内核任务扩大到移植所有 Mozilla 示例；不得宣称聚合 `mach build` 或上游测试已通过。
