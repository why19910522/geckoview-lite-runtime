# 外置内核 M2 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans，当前会话 inline 执行，最后一次独立审查。用户已要求开始动态化实现，沿用既有设计，不重复请求实施授权。

**Goal:** 从同一裁剪版构建拆出 bridge AAR 和本地内核包，保留 bundled 接入，在保留进程隔离的情况下验证 external 主/子进程加载。

**Architecture:** Java/Android 组件留在 bridge；全部引擎 so 与 Gecko 资源进入只读、16KB ZIP 对齐的 core 容器。宿主注入本地输入流和公钥；校验安装后固定包身份，将只读容器 FD 和身份通过现有 Binder bootstrap 传给子进程。每个进程只固定一次，不从子进程查询最新版。

**Tech Stack:** 既有固定 Gecko/工具链、Java17、Android API35/target30/min26、arm64、Python3 标准库 + OpenSSL 构建期签名。

**Spec:** `docs/superpowers/specs/2026-10-22-geckoview-lite-runtime-design.md` §§5–10、M2；此前用户要求 DevTools 保留、无 PDF.js/Speech/Glean 后端/reporter、Lite/zh-CN，继续生效。

## Global Constraints

- bundled 默认离线可用，不依赖 external 安装器；无网络下载器、渠道地址或运行中替换。
- bridge/core 只允许同次构建精确配对；包签名、公钥信任和完整性均验证后才能加载。
- 只读应用私有 no-backup 安装目录；拒绝错误签名/配对/ABI/大小/路径/重复项，失败不影响已安装包。
- isolated 服务声明与沙箱不降级；zygote 在收到选择前不得预加载另一份引擎。
- 动态化先完成 M2 本地加载与最小安装门槛。M3 自动候选自检、健康隔离/回退、profile 迁移保护尚不宣称已实现，不提供自动升级或 GC。
- 产物本地保留，代码/证据同步现有公开 PR，不合并主分支。

## Review Focus

- Binder FD 数字在接收端变化：资源入口与库入口必须使用接收端 FD，不复用父进程整数。
- 相同包多进程与旧子进程存活：不能混载不同包，不能在准备之后更换选择。
- 安装途中断电/并发/重复输入：暂存不能成为有效包，已有包保持可读。
- ZIP64/重复条目/链接/超限与损坏签名：校验失败不得触发任何 native 初始化。
- APK 内不得偷偷保留第二套引擎；默认 bundled 的早期加载及服务行为不能被 external 补丁破坏。

### Task 1: 拆包工具与固定配对

**Files:** `engine-package-tools/package_core.py`、`engine-package-tools/tests/test_package_core.py`。
**Interfaces:** 输入已验证完整 Release AAR、外部签名私钥、keyId、zipalign；输出 bundled 原输入、bridge AAR、签名 core-package.zip、host-contract.json。容器保持 `lib/arm64-v8a/*.so` 与 `assets/omni.ja`，库 stored/16KB 对齐；manifest 签名覆盖原始 UTF-8。

- [x] 测试拆包后 bridge 无引擎、core 原生库/资源摘要一致、签名可验证；缺失依赖和异常输入拒绝，先 RED 后 GREEN。
- [x] 从实际新 AAR 生成本地测试配对，检查无重复载荷、11 库闭包和格式。

### Task 2: Gecko 加载与子进程 bootstrap

**Files:** `patches/external-core/`；上游 GeckoLoader、GeckoThread、GeckoProcessManager、GeckoServiceChildProcess、ZygotePreload 的局部补丁。
**Interfaces:** 仅接收已校验 core 的只读 PFD 与固定身份；`GeckoLoader.prepareExternalCore` 在首次 native 前固定选择；主进程和子进程的库基址/greomni 使用本进程 FD。默认未选择则 bundled。

- [x] 新 host/bridge 接口在旧 AAR 上不具备，记录 RED；新增选择、幂等/拒绝更换及子进程传递。
- [x] 完整产品目标构建、Release 发布、补丁零 fuzz 重放；真机验证 FD ZIP namespace 和资源访问。若平台不支持则 fail closed，不取消隔离。

### Task 3: 本地校验与安装 API

**Files:** `runtime-external/`（纯 Java 验证逻辑 + Android 适配）。
**Interfaces:** `CoreStore.install(InputStream)` 返回受控包 ID；`prepare(packageId)` 在 native 前绑定匹配包；查询 ABSENT/INSTALLED/PREPARED。宿主注入可信 P-256 公钥，host contract 从 bridge 资产读取。只接受安装 ID，不提供任意 so 路径 API。

- [x] 签名/重复 JSON/错误配对/损坏 payload/异常 ZIP/超限、重复安装、失败保留旧包，先真实文件 RED 后 GREEN。
- [x] 包目录原子发布、安装互斥、准备前重验；持有进程树租约，拒绝活跃不同包；不实现自动 GC/切换/健康回退。

### Task 4: 双模式宿主与设备证据

**Files:** `smoke-host/`、`docs/builds/2026-10-26-external-core-m2.{md,json}`、接入文档。
**Interfaces:** 外置探针 APK 仅 bridge + installer；测试包从本地导入，不放入 APK；保留既有 bundled probe。所有实际启动的子进程日志关联同包身份。

- [x] 缺包结构化失败、安装后冷启动、重复准备/错误包拒绝；保留页面/Speech/PDF/DevTools 检查。
- [x] 真机覆盖默认子进程、isolated 与 zygote 的加载；记录未启动的进程类型和未验证设备范围，不夸大。
- [x] 比较基础 APK/内核包/安装字节；执行针对本轮变更的全套测试和 graphify AST 更新。
- [ ] 最终独立审查，处理重要发现，再提交同步 PR。
