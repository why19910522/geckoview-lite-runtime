# PDF.js / Web Speech 裁剪与 C++ 体积优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 本会话 inline 执行，完成后独立审查。

**Goal:** 保留 DevTools，移除 PDF.js 与 Web Speech，实测 C++ 体积优化并生成新的 Release AAR。

**Architecture:** 在现有10补丁产品源码后增加可重放产品补丁。先以裁剪后 -O2 建立对照，再以相同源码/工具链的 -Os + C++ ThinLTO 构建独立对象目录；Rust维持原配置，不使用跨语言LTO。两版均发布Release AAR并用本地Maven宿主消费。

**Tech Stack:** 固定 Gecko/翻译/SDK35/NDK r30-beta2/Clang22.1.8/Rust1.98.1/Java17/AGP9.4.1。

**Spec:** 用户本轮明确要求 DevTools 必须保留、删除 PDF.js 和 Web Speech、优化 C++。继续维持无Glean/无reporter/Lite/zh-CN/SDK35/target30/min26/arm64 Release。

## Global Constraints

- DevTools server 与远程调试链路保留；WebRTC、JXL、断词词典不在本轮裁剪范围。
- PDF.js查看器、stream converter、actor、启动入口、资源/专用翻译一起移除；PDF格式打印/生成不等于PDF.js查看器，不随意删除。
- WebSpeech native/Java/JNI闭包移除；网页接口不再提供，不能仅关闭pref。
- C++优化实测对照，比较同裁剪源码的O2与Os/ThinLTO，不将功能删除收益算成编译器收益。
- 保留旧产物，新产物仅本地 ignored artifacts；源码和报告同步现有PR，不合并main。

## Review Focus

- PDF没有悬空actor/模块导入；下载/外部处理不能假报内置预览成功。
- Speech Java排除必须与JNI生成及native配置同步，避免启动时缺失符号。
- DevTools资源存在不等于远程调试可用；独立核验连接链路。
- ThinLTO不是跨语言LTO；比较实际编译/链接参数与产物，而非只看mozconfig。
- 设备测试明确范围；没有代表性性能测试时不宣称全站性能不退化。

### Task 1: 产品闭包和O2对照

**Interfaces:** 消费上一版源码及10补丁；产出新增裁剪补丁、O2配置和对照Release AAR。

- [x] 旧AAR确认PDF.js与Speech Java/native仍存在，DevTools存在（RED）。
- [x] 增加产品配置和裁剪补丁，资源/actor/Java/JNI/组件完整移除。
- [x] 独立objdir configure/pre-export/export/binaries、中文资源、Release发布通过。
- [x] 检查SDK/locale/Glean/reporter/DevTools/裁剪内容，保存O2对照产物。

### Task 2: C++优化与最终验证

**Interfaces:** 与Task1相同源码；产出Os+ThinLTO Release AAR及体积对比、设备证据和报告。

- [x] 独立objdir配置Os+ThinLTO，核对实际C++及链接参数，完成源码构建和Release发布。
- [x] 两版独立宿主消费、本地页/接口/调试验证；设备可用时做最小实机检查。
- [x] 比较相同裁剪源码两版体积；核验最终来源、ELF/ZIP对齐、中文、DevTools及移除闭包。
- [x] 补丁重放、报告、graphify update、独立审查后提交并同步现有公开PR。
