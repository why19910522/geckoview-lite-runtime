# 无 Glean 的中文 Release AAR 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 本会话直接执行，结束时独立审阅。

**Goal:** 移除 Glean SDK 运行后端，生成 compileSdk35 / targetSdk30 / minSdk26 / arm64 / zh-CN 的 Release AAR。

**Architecture:** 延续固定 Gecko 修订及原九补丁；增加产品级无 Glean 补丁，断开 Rust Glean SDK、FOG 控制及 JOG 后端依赖。引擎现有指标调用保留无存储、无线程、无上传的兼容空实现，避免改动网络/渲染功能；独立 objdir 完整重建。Gradle 显式发布 Release 组件，宿主使用本地 Maven 精确坐标。

**Tech Stack:** 固定 Gecko d6fd7a4、现有 SDK35/NDK r30-beta2/Clang/Rust/Java17，固定官方 zh-CN 翻译。

**Spec:** 用户本轮直接要求关闭并移除 Glean、Release AAR、compileSdk35、targetSdk30 和仅中文；此前 reporter 移除继续保持。实现采用无操作兼容层是构建者选择，不将接口字符串仍存在误报成 SDK 后端仍运行或声称所有遥测均已删除。

## Global Constraints

- compileSdk35、targetSdk30、minSdk26、arm64-v8a、仅 zh-CN UI 语言包。
- 保留 Lite、无 Crash reporter、HLS 关闭；无 Artifact Build；保留旧产物。
- 移除 Glean SDK 的实际 native/Java/Maven 依赖及初始化、存储、采集、上传后端，不能仅 disable_upload。
- Release Java 组件 + optimized non-debug native；不伪称 Mozilla 官方稳定发行、设备验证或签名发布。
- 产物只保存在本机 ignored artifacts；补丁及记录同步现有公开分支/PR。

## Review Focus

- 空实现必须初始化输出，不能返回成功却持有未完成 Promise、读未初始化指针或让后台任务继续采集。
- Cargo 工作空间锁文件存在 SDK 不代表产物包含 SDK；检查实际编译依赖及最终 ELF 导出。
- Rust/JS/C++ 既有调用不能因后端删除破坏网络、渲染、启动及异常处理。
- Release 必须来自实际 Release variant/native 配置，不能把 Debug AAR 重命名。
- 资源、JNI、库摘要、本地 Maven、AAR 与独立宿主必须来自本次 objdir，不能远端回退或混用旧包。

### Task 1: 无 Glean 源码闭包与 Release 配置

**Files:** patches/no-glean-release/*、build-config/mozconfig-sdk35-lite-no-glean-release-zh-CN-arm64、scripts/verify-no-glean-aar.py。
**Interfaces:** 消费现有九补丁和固定翻译；产出无 SDK 依赖的产品源码、空实现及 Release 配置。

- [x] 对旧 AAR/config 执行检查，确认原生 Glean 后端存在（RED）。
- [x] 增加显式禁用配置、移除 Rust 后端、实现无副作用兼容层，移除 Java Glean 上报入口。
- [x] 配置 Release 组件/发布与独立 objdir，configure 及导出通过。
- [x] 定向验证空实现行为与实际依赖图，记录兼容接口保留范围。

### Task 2: Release AAR 构建与消费验证

**Interfaces:** 消费 Task1 产品源和配置；产出本地 Release AAR、Maven、宿主 APK、摘要及复现记录。

- [x] pre-export/export/binaries 完整 native 编译，merge-zh-CN/misc/libs/stage-package。
- [x] checkReleaseAarMetadata / assembleRelease / Release Maven publication 通过。
- [x] 验证 Glean 后端/依赖与 reporter 不存在，11 库来源/ELF、zh-CN UI 内容和注册一致。
- [x] 独立宿主 compile35/target30 消费精确本地 Release 依赖并打包通过。
- [x] 记录构建类型、大小、摘要、补丁重放与验证边界；graphify update、独立审阅及同步 PR。

实施说明：Nightly 的 Gecko Trace 默认采集路径一并关闭，Glean `RecordPowerMetrics` 预处理为空函数；代价是相关 trace/功耗诊断不可用，符合关闭 Glean 的产品目标。旧 Telemetry 全面移除不在本次范围。

独立审查发现初始化 guard 错位，真实方法探针 RED→GREEN 修复。宿主 target30 的 Release lint 仅定点豁免 `ExpiredTargetSdkVersion`，保留用户参数及其他检查；不将其表述为商店发布资格。

独立审查（2026-10-26）已完成，无未解决的 Critical、Important 或 Minor。产物、依赖、补丁摘要和文档范围一致。
