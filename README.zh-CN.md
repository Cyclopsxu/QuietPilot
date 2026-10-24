# 隐控 ShadowAuto（二次开发版）

[English](README.md) | 中文详细手册

隐控是基于 [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto) 二次开发的 Android 自动化原型：让真实 App 在虚拟显示屏中运行，由模型读取页面、规划操作，用户通过控制端和悬浮球提交任务、查看进度、处理人工边界并接收结果。

**本手册按截至 2026-10-24 的代码与已有验证记录整理。当前支持同机不同 App 并行；同 App 冲突时用户优先。虚拟屏创建成功不等于焦点隔离通过，兼容实验模式也不等于全程无打扰。**

上游提供 shell/app_process、VirtualDisplay、UI/OCR 操作、分屏输入、H.264 预览、Android 控制端与浏览器部署器等基础能力；本分支在其上增加任务验收、人工交接、调度与保护、技能学习、语音与上下文、历史与指标，并修复部分华为设备兼容问题。上游基线为 `611e5eb0e1c94befda3c17c97438912dfd30e8a8`。保留 [Apache-2.0 许可证](LICENSE)，出处见 [NOTICE](NOTICE)，修改范围见 [二次开发说明](docs/DERIVATIVE_CHANGES.md)。本分支与上游无背书关系。

## 给评审的概览

让模型在手机后台虚拟屏里操作真实 App，用户同时使用主屏；当前为 Android 原型，验证范围见下表。

**演示：**[五段演示操作与录制方案](docs/DEMO.md)。本分支完整演示尚未录制，实际视频链接和时间点待补；上游视频不作为本分支证据。

**路线与取舍：**虚拟屏让任务留在同一台手机、同一真实 App/登录环境，便于原页面交给用户。云手机/第二台设备增加账号与设备成本，应用分身依赖厂商且改变数据环境，主屏无障碍点击会占用用户界面，iPhone 不提供本原型依赖的 shell/虚拟屏能力。选择 Apache-2.0 的 ShadowAuto 复用 shell、UI/OCR、输入和投屏底座；下列新增见[出处与改动说明](docs/DERIVATIVE_CHANGES.md)。

| 相对上游新增 | 源码入口（android-shell/.../ext，另注明者除外） |
| --- | --- |
| 原任务页面在主屏与虚拟屏间交接、完成后交付 | [handoff](android-shell/src/main/java/com/silentauto/shell/ext/handoff/) |
| 参数化技能学习、复用与有限救援 | [skill](android-shell/src/main/java/com/silentauto/shell/ext/skill/) |
| 另一家模型独立验收；查询只读、答案必须有原文 | [verify](android-shell/src/main/java/com/silentauto/shell/ext/verify/)、[query](android-shell/src/main/java/com/silentauto/shell/ext/query/) |
| 执行中语音追加；读取当前屏幕“边看边办” | [amend](android-shell/src/main/java/com/silentauto/shell/ext/amend/)、[context](controller-app/src/main/java/com/silentauto/controller/ext/context/) |
| 镜像抓图与有预算恢复；重复动作退出 | [CaptureMirror](android-shell/src/main/java/com/silentauto/shell/CaptureMirror.java)、[guard](android-shell/src/main/java/com/silentauto/shell/ext/guard/) |
| 干扰量化和原始时间序列归档 | [metrics](android-shell/src/main/java/com/silentauto/shell/ext/metrics/) |

| Mate 40 Pro 实测（2026-10-23/24） | 结果与限制 |
| --- | --- |
| 抓图后读取控件 | 60/60 抓图成功、根不可读0、原屏切换0；仅三类页面短测 |
| 计算12×13 / 高德公共地点地址 | ANSWERED，6步/4步，有截图与原文；高德仅搜索结果页 |
| “先清除再计算” | 原版25步FAILED；本轮7步ANSWERED156，另一次16步因重复被终止，两者均保留 |
| 备忘录同页交接 | VERIFIED，同根任务转主屏、正文哈希一致、次屏释放 |
| 备忘录技能探索 / 复用 / 人为偏离救援 | 均 VERIFIED，8 / 10 / 13步；复用调用10→8但耗时27.7→49.4秒，仍回退模型，见[三段报告](docs/evidence/skills/report.md) |
| 微信发送、支付前语音追加 | 尚未按 Step 6 完整验收，不计成功 |

证据均从[仓库内索引](docs/evidence/README.md)进入。“不打扰”采用防抢焦点触摸、危险键门禁、无障碍输入和剪贴板恢复；本机缺原生独立焦点与本地 IME 确认，兼容模式仍未认证全程隔离，本轮三轮采样四项0、机主反馈正常，但首轮录像缺口且主屏同文已由机主确认手动输入；仍不将周期采样当作全程保证；历史轮还存在短暂无焦点/IME不活跃记录，见[能力边界](docs/ISOLATION.md)及[三轮验收状态](docs/evidence/isolation/report.md)。

**模型与落地：**executor（DeepSeek）规划，verifier（百炼视觉）独立验收，intake（百炼 Omni）理解语音；国内接口白名单用于限制数据出境路径，仍需服务合规与个人信息授权。普通手机当前需 ADB/app_process；Shizuku + 无线调试是待实现的免电脑启动方向，商用隔离能力需厂商合作，也受平台协议、账号及隐私风险约束。

**与 Coding Agent 协作：**产品/方案文档→任务说明→实现→真机验证→修正。实际反馈包括：交换抓图 Surface 后控件不稳→独立 CaptureMirror；高德/美团节点点击返回成功却未激活→重新定位后边界触摸；查询整段目标误填城市框→关闭宽泛纠正；算出156仍反复清除→加入重复检测和独立验收。对应事实与限制保留在[实测报告](docs/evidence/capture-stability/report.md)。

**已知限制：**①全程隔离待三轮人工录像；②验证码、支付等需本人处理，部分 App 不兼容；③备忘录技能仍会回退模型，微信与语音追加缺完整路径证据；④RPC 仅监听回环但未鉴权，同机 App 可下发任务，计划使用32字节随机 token；⑤跨设备/Android 版本及长期稳定性未覆盖。

## 目录

- [1. 已实现的功能](#1-已实现的功能)
- [2. 系统组成与运行方式](#2-系统组成与运行方式)
- [3. 安装与启动](#3-安装与启动)
- [4. 模型配置与数据流向](#4-模型配置与数据流向)
- [5. 实际使用流程](#5-实际使用流程)
- [6. 自动化循环与动作执行](#6-自动化循环与动作执行)
- [7. 抓图、视频、控件与恢复机制](#7-抓图视频控件与恢复机制)
- [8. 焦点隔离、并发与主屏保护](#8-焦点隔离并发与主屏保护)
- [9. 完成验收与长文写入](#9-完成验收与长文写入)
- [10. 人工交接、结果交付与失败回退](#10-人工交接结果交付与失败回退)
- [11. 语音、上下文与追加要求](#11-语音上下文与追加要求)
- [12. 技能学习与复用](#12-技能学习与复用)
- [13. 查询任务与来源证据](#13-查询任务与来源证据)
- [14. 状态、历史、指标与本地存储](#14-状态历史指标与本地存储)
- [15. RPC 接口与连接可靠性](#15-rpc-接口与连接可靠性)
- [16. 开发、回归与验收方法](#16-开发回归与验收方法)
- [17. 已有真机结果与尚未解决的问题](#17-已有真机结果与尚未解决的问题)
- [18. 故障排查](#18-故障排查)
- [19. 源码索引与资料组织](#19-源码索引与资料组织)

## 1. 已实现的功能

| 能力 | 当前行为 | 实现入口 |
| --- | --- | --- |
| 真实 App 后台执行 | shell 创建任务虚拟屏，在目标 App 内搜索、点击、输入、滚动；控制端显示实时预览 | `AutomationEngine`、`VirtualDisplaySession` |
| 多任务 | 最多同时运行两个任务；同 App 串行，主屏用户操作优先 | `TaskScheduler`、`MainAppConflictGuard` |
| 模型分工 | executor 规划，verifier 独立验收，intake 理解语音 | `ModelConfig`、`AiClient` |
| 文字办事 | 从目标到操作、结果验收、交付与历史归档 | `ActionExecutor`、`ResultVerifier` |
| 只读查询 | 浏览与搜索后返回短答案、页面引文、来源；执行只读动作约束 | `ReadOnlyGuard`、`AnswerVerifier` |
| 语音任务 | 64dp 悬浮球，约 1 秒按住，录音就绪后震动，松手提交，上滑取消 | `FloatingBallService`、`VoiceIntake` |
| 屏幕上下文 | 录音前读取主屏上下文，密码节点遮蔽，可关闭，120 秒过期 | `ScreenContextReader`、`ScreenPrivacy` |
| 执行中追加 | 修改、补充或取消；多个可能目标时选择任务 | `AmendmentQueue`、`VoiceRouting` |
| 人工边界 | 登录、验证码等暂停；原页面移到主屏处理，再交回后台 | `HandoffManager`、`ManualTaskTransfer` |
| 完成后交付 | 保留需查看的页面，提示“去查看”；确认原页面转移成功后释放虚拟屏 | `ResultRetention`、`TaskDisplayTransfer` |
| 交付失败回退 | 校验转移和回退状态；可用时保留预览供重试或结束查看 | `PageIdentity`、`AutomationTaskView` |
| 技能学习 | 从符合条件的成功轨迹编译参数化技能，按当前控件定位复用 | `TrajectoryRecorder`、`SkillCompiler`、`SkillRunner` |
| 技能救援 | 偏离后最多 3 个救援动作；失败回模型，连续弃用后标为 suspect | `SkillRunner`、`SkillStore` |
| 抓图稳定性 | 独立图层栈镜像抓取次屏，持续排空图像，不切换原视频输出面 | `CaptureMirror` |
| 控件恢复 | 区分缺失根、遍历不完整与真实叶节点；有限重连，旧目标拒点 | `UiBridge`、`UiRecoveryBudget`、`ObservedTarget` |
| 历史与指标 | 结果、证据截图、步骤、模型调用、耗时、技能来源和干扰观测 | `TaskArchive`、`TaskProgress`、`DisturbanceMonitor` |
| 连接保护 | 避免回环自连接、旧读线程误清新连接、慢客户端阻塞任务 | `RpcConnection`、`RpcClient`、`RpcServer` |

这些是代码能力，适用范围仍受设备隔离、App 页面可访问性、登录/验证码、模型规划质量和系统安全策略限制。实际验证范围见第 17 节。

## 2. 系统组成与运行方式

```text
用户文字 / 悬浮球语音
           │
controller-app：配置、任务页、视频预览、交接、历史、技能
           │ 本机 TCP / 逐行 JSON-RPC
           ▼
android-shell：adb shell + app_process
  ├─ 任务调度、状态、冲突与焦点保护
  ├─ 当前 UI → 模型/技能 → 动作 → 再观察
  ├─ VirtualDisplay → 真实目标 App
  ├─ UiAutomation / OCR / 次屏截图
  └─ 独立验收 → 查询答案或保留页面交付 → 历史
           │
           └─ HTTPS 请求已配置的模型服务
```

| 模块 | 职责 |
| --- | --- |
| `android-shell` | shell 权限的执行进程、系统服务桥接、自动化和本机 RPC |
| `controller-app` | 手机上安装的“隐控”，负责 UI、录音、配置和交接控制 |
| `android-stubs` | 编译时 hidden API 声明，不是可独立运行的服务 |
| `paddler-ocr` | Paddle Lite OCR 封装、原生库、模型和字典准备 |
| `web-launcher` | Svelte/Vite + TangoADB/WebUSB 部署工具 |
| `scripts` | 主机回归、设备探针、模拟模型测试 |

**两个 Android 产物用途不同：**控制端 APK 使用安装器安装；shell APK 作为 `CLASSPATH` 文件由 `app_process` 加载。只安装控制端不会自动获得 shell 权限，也不会自动启动执行后台。无需 root，但需要已授权的 ADB shell；隐藏 API 的行为取决于系统。

本项目没有负责客户账号、密钥下发、计费或模型代理的业务后端。手机上的 shell 是本地执行后台，模型推理仍依赖外部服务。两者不能混为“全部离线”。

## 3. 安装与启动

### 3.1 环境要求

- Android 10 / API 29 或以上是代码最低要求，**不是所有 Android 10+ 设备的兼容保证**。
- 构建使用 JDK 17，Android SDK Platform 36；SDK 路径通过本地环境或 `local.properties` 配置。
- 本机 ADB、USB 数据线，手机启用开发者选项与 USB 调试，并允许电脑调试授权。
- 构建 Web 启动器时需要与其 Vite 版本兼容的 Node.js/npm；浏览器部署需要支持 WebUSB 的浏览器和安全上下文。
- 初次 Gradle/OCR 准备需要下载依赖；模型任务需要网络与有效 API 配置。
- 华为安装器可能要求手动确认或锁屏密码。密码只在手机输入，不写入配置或日志。

先检查连接：

```sh
adb devices -l
```

`device` 才表示授权完成；`unauthorized` 需在手机接受授权。多个设备时，将下文所有 `adb` 改为 `adb -s <设备序列号>`。

### 3.2 构建并准备部署资源

在仓库根目录执行：

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
./gradlew :android-shell:syncWebLauncherArtifacts
```

主要输出：

| 文件或目录 | 用途 |
| --- | --- |
| `controller-app/build/outputs/apk/debug/controller-app-debug.apk` | 安装到手机的控制端 |
| `android-shell/build/outputs/apk/debug/android-shell-debug.apk` | shell 执行代码 |
| `android-shell/build/ocr-runtime/` | OCR 原生库、模型与字典 |
| `web-launcher/static/silent-shell.apk` | 同步后的 shell 产物 |
| `web-launcher/static/controller-app.apk` | 同步后的控制端产物 |
| `web-launcher/static/ocr/` | 启动器上传的 OCR 资源 |

OCR 默认参与构建。单独准备可用：

```sh
./gradlew :paddler-ocr:preparePaddleLiteOcr
./gradlew :android-shell:copyPaddleLiteOcrRuntime
```

准备任务下载 PaddleLite v2.10、OpenCV Android SDK、PP-OCRv2 中文移动端模型和字典；下载目录 `paddler-ocr/PaddleLite`、`OpenCV`、`PaddleOcrAssets` 不提交到 Git。shell APK 不能替代外置 OCR 运行时资源。

### 3.3 通过 ADB 手动部署

安装控制端：

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
```

同包名、同签名覆盖安装通常保留已有配置；卸载、清除应用数据或更换不兼容签名是不同操作，不应当作更新步骤。

上传 shell 和 OCR：

```sh
adb push web-launcher/static/silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell 'mkdir -p /data/local/tmp/shadowauto/ocr'
adb push web-launcher/static/ocr/. /data/local/tmp/shadowauto/ocr/
```

停止旧进程后启动。此操作会中断旧后台及其实时虚拟屏页面，先处理完需要保留的任务：

```sh
adb shell 'if [ -f /data/local/tmp/silent-auto.pid ]; then kill "$(cat /data/local/tmp/silent-auto.pid)" 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi'
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup sh -c 'exec app_process /system/bin com.silentauto.shell.Main --port=43110' >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

然后打开手机上的隐控，确认连接，再配置模型。手机重启或 shell 进程被杀后，需要重新启动后台；不能将控制端前台服务理解为保证 shell 永久存活。

日志：

```sh
adb shell cat /data/local/tmp/silent-auto.log
adb logcat -v time -s ShadowAutoShell
```

停止后台：

```sh
adb shell 'if [ -f /data/local/tmp/silent-auto.pid ]; then kill "$(cat /data/local/tmp/silent-auto.pid)" 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi'
```

### 3.4 浏览器部署

先同步当前分支产物，再启动本地启动器：

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
cd web-launcher
npm install
npm run dev
```

打开 Vite 输出地址，按页面流程连接 USB、上传资源、安装控制端和启动 shell。浏览器与 adb server 可能竞争 USB；只有遇到独占冲突时才考虑 `adb kill-server`，它会停止该电脑的其他 ADB 会话。

[上游在线启动器](https://android-notes.github.io/ShadowAuto/)属于上游发布渠道，不能用来证明已部署本分支最新修改。测试本分支应使用本地构建资源。

### 3.5 隔离能力不足时的兼容实验

默认启动会检查独立焦点、禁止抢焦点和本地输入法策略。能力不足时阻止后台执行，不静默降级为“已经隔离”。

仅在明确接受风险的专用测试中，可在上面的启动命令末尾 `--port=43110` 后追加：

```text
--allow-unverified-isolation=true
```

这只是放开实验门禁，不会补齐厂商缺失的能力，也不会授予额外系统权限。状态与历史会保留未验证标记。普通部署不应默认开启。

## 4. 模型配置与数据流向

在手机配置页分别填写角色的 API Key、API Base 和模型名称，然后测试连接。

| 角色 | 工作 | 当前代码预填值 |
| --- | --- | --- |
| executor | 目标理解、选 App、动作规划、技能匹配/编译等 | DeepSeek，`deepseek-flash` |
| verifier | 页面证据、结果核对、查询独立提取等 | 阿里云兼容接口，`qwen3-vl-plus` |
| intake | 语音理解，区分查询、办事和追加意图 | 阿里云兼容接口，`qwen3.8-omni-flash` |

2026-10-24 核对[DeepSeek 官方接口文档](https://api-docs.deepseek.com/api/responses)：`deepseek-flash` 当前由 DeepSeek-V4.1-Flash-1017 提供服务。别名将来可变；控制端测试连接会显示服务端返回的 `model`，若仍返回别名，不能据此认定权重快照。


代码中的预填名称不是服务当前可用性、价格或效果承诺；以账号可用模型和连接测试为准。视觉验收需要相应图像能力，语音理解需要相应音频能力，不能仅凭接口返回 200 就认定所有任务能力齐全。

当前远程地址校验只接受以下官方主机的 HTTPS，以及供本机测试使用的回环 HTTP/HTTPS；不是任意 OpenAI-compatible 代理均可直接填写：

```text
https://api.deepseek.com/v1
https://dashscope.aliyuncs.com/compatible-mode/v1
```

地址不允许嵌入用户信息、查询参数或片段。支持按角色的 `models` 配置；旧 `apiKey`、`apiBase`、`model` 字段保留兼容，不建议新集成依赖旧字段代替角色分工。

shell 还支持启动参数与环境变量（只在实际启动 shell 的环境生效）：

| 配置 | 参数 / 环境变量 |
| --- | --- |
| RPC 端口 | `--port` / `SILENT_AUTO_PORT` |
| 各角色 Key | `--executor-api-key` / `SHADOWAUTO_EXECUTOR_API_KEY`，verifier/intake 同理 |
| 各角色地址 | `--executor-api-base` / `SHADOWAUTO_EXECUTOR_API_BASE`，其他角色同理 |
| 各角色模型 | `--executor-model` / `SHADOWAUTO_EXECUTOR_MODEL`，其他角色同理 |
| 默认服务商 Key | `DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY` |
| 旧单模型配置 | `--api-key`、`--api-base`、`--model` / `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL` |

参数格式为 `--名称=值`。默认角色配置上可叠加角色参数/环境；旧单模型配置会让初始三角色共用同一配置，再接受角色覆盖。任务 RPC 的 `models` 可进一步覆盖。Mac 上导出变量不会自动传入 `adb shell`，也不会自动更新控制端已保存配置。命令行参数可能暴露在进程信息中，日常优先使用控制端配置页。

配置保存于控制端 `MODE_PRIVATE` 的 `ai_config` SharedPreferences，字段为 `<role>.apiKey`、`<role>.apiBase`、`<role>.model`。**当前没有实现自有后端密钥分发，也不是 Keystore 加密密钥方案。**本地环境文件只是开发输入，不能认为安装 APK 会自动导入其中的密钥。

外部模型按流程可能收到用户目标、可见 UI 文本、页面截图、语音和读取的上下文。OCR 在设备侧执行，不代表后续模型推理也在本地。模型配置和测试证据不要提交真实密钥；包含 `models` 的 RPC 请求也不适合完整打印到日志。

## 5. 实际使用流程

### 5.1 文字任务

1. 启动 shell，打开隐控并确认已连接。
2. 填写并测试 executor/verifier；使用语音再配置 intake。
3. 输入目标，尽量明确 App、操作对象、限制和交付方式。
4. 在任务页观察步骤和视频；需要时停止当前任务或停止全部任务。
5. 遇到人工边界按提示处理。完成后接收结果，不把“模型说完成”直接当作实际成功。

短任务示例：

```text
查询：用计算器计算12乘13，告诉我结果。
查询：用高德查苏州博物馆本馆的地址，不启动导航。
办事：把下列已核实信息写入标题为“测试｜参观信息”的备忘录，保留结果让我查看。
浏览：在美团选一家适合约会的餐厅，只浏览，不预订、不下单、不支付，选好后保留页面。
```

同一个自然语言目标能否稳定完成仍取决于模型和页面。先做短任务验证，再增加跨 App 与长文目标；不能将一次成功等同于通用业务可靠性。

### 5.2 语音任务

授权麦克风、悬浮窗及系统要求的通知/前台服务权限，开启悬浮球。按住约 1 秒，等短震后说话，松手提交；上滑超过约 80dp 取消。录音前的拖动可移动悬浮球。

震动代表录音已实际启动，不只是长按计时结束。读取上下文或申请音频焦点可能增加等待时间。上下文可在设置关闭。

### 5.3 完成与人工处理

- **“去处理”**：任务尚未完成，需要本人登录、验证或确认；可以移到主屏处理，再交回后台。
- **“去查看”**：已到结果交付阶段，尝试把原任务页面移到主屏；成功后才清理虚拟屏。
- **查询答案胶囊**：显示短答案与引文，可手动复制或打开来源；查询通常不继续占用虚拟屏等待查看。
- **历史截图**：用于回看证据，不能恢复被关闭的 App 会话。

## 6. 自动化循环与动作执行

`AutomationEngine` 负责调度与生命周期，`ExecutionContext` 汇集当前任务上下文，`ActionDecision` 表达动作，`ActionExecutor` 执行动作。动作来源可区分 `model`、`skill`、`rescue` 和 `query_recovery`，便于记录和比较。

基本循环：

1. 检查停止请求、同 App 冲突、主屏焦点和待处理追加。
2. 获取目标虚拟屏的当前 UI 与健康诊断，不复用已失效节点编号。
3. 尝试适用技能；没有可用技能时由执行模型规划。
4. 模型可读取布局或请求 OCR，再提交动作；单次决策观察工具调用有上限。
5. 校验只读约束、目标身份与输入策略，执行一个动作。
6. 等待界面变化，重新观察并写入轨迹。
7. 对人工请求和完成声明分别走交接或独立验收。
8. 结束后记录指标、证据与历史，释放资源。

当前主循环最多 25 步，单轮观察工具调用上限为 4。达到上限是失败或受控收尾条件，不代表业务已完成。网络错误、模型输出错误和无效动作也不能伪装为成功。

动作覆盖控件点击、坐标触摸、输入框聚焦、文字输入、全选/清除/粘贴、滚动、拖拽、返回、按键、等待、请求人工处理和报告结果。次屏触摸使用对应 `displayId`；模型给出坐标并不自动绕过页面身份或查询约束。

`ObservedTarget` 保存新鲜观察中的路径、类、资源 ID、文本和边界。执行时重新核对；连接代际改变、目标被替换等情况会放弃旧计划再观察。高德、美团部分节点会返回点击成功但界面没有激活，因此这些页面采用重新定位后的边界触摸；其他页面保留原生控件点击和触摸兜底。

输入优先使用页面可见、可验证的控件。搜索优先点击实际搜索按钮；隔离不足时不注入可能抢主屏焦点的搜索/回车按键。查询模式已关闭“把整段目标自动填进任何疑似搜索框”的宽泛纠正，避免城市选择框被填入商家描述。

## 7. 抓图、视频、控件与恢复机制

### 7.1 H.264 预览与独立抓图

任务虚拟屏正常输出到 MediaCodec 输入 Surface，编码为 H.264；控制端通过 `H264VideoDecoder` 解码到 TextureView。

旧抓图路径临时将虚拟屏输出改接 ImageReader，再恢复编码面，可能扰动目标 App。新增 `CaptureMirror` 将次屏图层栈输出到**单独的非安全合成显示**：

- 仅接受正数次屏 ID 和非主屏图层栈，不抓主屏、不启用安全内容捕获。
- 反射读取 `DisplayManagerGlobal` 的 display 信息与 layer stack，通过 `SurfaceControl` 建立独立输出。
- ImageReader 使用 RGBA 格式、3 个缓冲；专用线程持续 `acquireLatestImage`，只保留最新一张并关闭旧图。
- 需要截图时复制最新图像，不在每一帧创建 Bitmap，避免缓冲积压。
- 原 VirtualDisplay 输出面保持连接视频编码器，镜像模式不切换原输出 Surface。
- 释放时关闭图像、监听器、合成显示和读帧线程；等待被中断时恢复中断状态。

若镜像在创建阶段不被系统支持，保留 `legacy_surface_swap` 回退并记录原因。**已经建立的镜像若后来失败，返回截图失败；不会临时切回原屏换面，也不会返回关闭前的旧图。**静态页面在没有新帧时可能读取仍有效的最新渲染帧，这与返回已关闭镜像的旧图不同。

`isolation.capture` 记录抓图模式、回退原因、请求数、失败数、原输出面切换次数及收到帧数；任务释放前写入最终记录。模式为 `layer_stack_mirror` 不等于设备所有隔离能力通过。

### 7.2 UI 读取和空控件判断

`UiBridge` 串行化公共读取/操作入口，并记录连接代际。遇到空窗口会清理 AccessibilityInteractionClient 缓存，等待约 180ms 后重读一次。

诊断包括 `connectionGeneration`、`rawWindowCount`、`nullRootCount`、`rootReadMs`、`rootsUnavailable`、`cacheRecoveryAttempted`、`readableNodeCount`、`nullChildCount`、`incompleteTraversal`。这些字段用于区分无窗口、根不可读和遍历失败，避免把真实无子节点页面误判为空树。

系统无响应弹窗按 `android:id/aerr_wait` / `android:id/aerr_close` 等实际控件识别，不能仅凭页面正文出现“无响应”三个字就重启 App。

### 7.3 有预算的恢复

`UiRecoveryBudget` 对连续不可读观察执行：

| 阶段 | 行为 |
| --- | --- |
| 前两次缺失 | 等待，不点击、不输入 |
| 第三次缺失 | 尝试一次 UiAutomation 重连 |
| 重连后再次持续缺失 | 仅查询任务可尝试一次 App 恢复重启 |
| 仍无法恢复 | 暂停交给用户，保留可用页面/预览 |

编辑、写入类任务不靠自动重启 App 恢复，避免丢失未保存内容。目标页面已被移到主屏时，也不反复拉起目标 App 制造前后台闪烁。预算恢复不能保证修复 App 自身 ANR；系统或应用问题仍可能需要人工处理。

### 7.4 OCR

`PaddleOcrEngine.init()` 初始化运行时，`recognize(Bitmap)` 返回文本、置信度和坐标框。模型可以在自绘、节点稀疏或控件不足时调用 OCR；OCR 结果仍需结合页面和动作约束使用，不把识别文字当作可点击控件的无条件授权。

## 8. 焦点隔离、并发与主屏保护

虚拟屏创建会尝试不同 flags，并记录最终 `selectedFlags` / `selectedFlagsHex`、`candidateIndex`、独立焦点和禁止抢焦点请求、本地输入法结果。评估将以下概念分开：

- `configurationAvailable`：需要的配置是否被接受。
- `behaviorValidated`：实际行为是否已验证；不能因为创建成功就自动置为通过。
- `experimentalOverride`：是否在能力不足时明确选择实验放行。
- `backgroundAllowed` / `status`：是否允许继续及原因。

次屏触摸增加 `FLAG_NO_FOCUS_CHANGE`（64），主屏触摸保留原有行为；次屏原生按键由 `BackgroundKeyPolicy` 保护，隔离未确认时禁止危险按键路径。这些改动减少焦点扰动，但不等于给设备补上系统缺失的独立输入法或焦点权限。

`ForegroundWatcher`、`MainAppConflictGuard` 和 `BackgroundFocusGuard` 检查用户正在使用的 App 与主屏窗口。用户打开同一目标 App 时，让用户优先，后台暂停/排队；恢复执行需重新观察当前页面。未授权焦点变化触发保护后，不仅因一次焦点恢复采样就自动继续制造抖动。

最多两个任务并发指不同 App 的受控并发；不是同一个 App 的两套账号或两个完全独立会话。保留结果和等待交接也可能占用任务资源。

剪贴板有快照、恢复及变化观测。它不构成“从未临时触碰全局剪贴板”的证明。是否干扰输入法、中文候选词与滚动，需要专项并行测试，不能只看任务成功或某个计数为 0。

## 9. 完成验收与长文写入

办事任务的 `done` 是模型提交的完成声明，随后 `ResultVerifier` 根据目标、当前页面、截图等证据独立核对。未通过时继续观察、纠正或交接，不直接标为成功。

长文备忘录中的确定性 `DocumentProof` **目前专用于华为备忘录 `com.huawei.notepad`，且要求目标使用约定的完整正文格式**。它按实际资源 ID 核对标题和完整正文，再观察返回列表、重新打开后的相同正文，而不是只认标题或“保存成功”。一般自然语言目标、其他备忘录 App 并不自动获得这套精确证明，仍走通用验收。

目标模板如下；标记需独占对应行，正文结束标记不可在正文中重复：

```text
把以下内容写入华为备忘录，保存并重新打开核对。
标题：测试｜参观信息
完整正文开始：
这里填写逐字验收的完整正文，包括最后一行和来源链接。
完整正文结束。
```

已有测试核对过完整正文并在主屏接收同一备忘录任务。字符截断、只写入部分段落、未保存或打开错误对象都不能以标题匹配代替验收。

视觉验收仍受屏幕覆盖范围和模型判断限制，不是对任意第三方 App 数据库的事务证明。对于搜索/调查任务，缺少来源或未实际核实的项目应保留“待核实”，不能用推断补成已验证事实。

## 10. 人工交接、结果交付与失败回退

### 10.1 任务执行中的人工处理

```text
后台遇到人工边界 → 暂停并提示
  → 用户选到主屏处理 → 尝试移动原任务
  → 用户在主屏完成 → 点交回后台继续
  → 核验任务/页面并转回虚拟屏 → 重新观察再执行
```

验证码与登录由本人处理。系统受保护页面可能黑屏，转到主屏比在小预览里拖动验证码更可用。主屏处理期间暂停自动操作；不把模型请求人工处理当作自动抢屏许可。

交接有超时管理，默认等待预算为 10 分钟，并区分手动处理中与返回后的等待。停止、取消和追加需要与转移过程协调，避免一边销毁显示、一边移动页面。

### 10.2 任务完成后的“去查看”

需交付的办事结果会保留页面，显示待查看提示。用户点击后：

1. 记录用户主动交接，暂停相关自动动作。
2. 查找原任务与所属 display，移动原 task 到主屏。
3. 通过 `PageIdentity` 核对包名、页面特征和可用实体锚点；不只看目标 App 是否启动。
4. 确认接收成功后再释放虚拟屏和相关资源。
5. 若转移失败，尝试恢复原页面并核验回退；可用时保留预览，允许重试或结束查看。

`moved`、回退状态和 `previewAvailable` 必须分别表达，不能把“发出了移动命令”当作转移成功，也不能在没有检查时宣称回退成功。重新启动一个 App 首页不是原页面交付。

### 10.3 正常退出与失效情况

用户可以停止任务、结束查看或停止全部任务。等待与交接路径也应响应取消，不要求靠强杀应用退出。系统杀进程、重启手机、App 自身崩溃仍可能使实时页面丢失；历史截图只保留证据，不提供进程/页面恢复保证。

## 11. 语音、上下文与追加要求

### 11.1 悬浮球与录音

- 球体 64dp，默认避开边缘 28dp，并考虑系统手势边距，改善曲面屏误触返回。
- 长按门槛 1000ms；上下文读取和录音初始化完成后才发出约 80ms 短震。
- 16kHz、单声道、16-bit PCM，封装为 WAV；约 30 秒保护上限。
- 松手停止并提交，上滑取消；取消不创建新语音任务。
- 录音申请瞬时音频焦点，结束释放；其他 App 是否自动恢复音频由其实现决定。

### 11.2 上下文与隐私

手指按下时先请求上下文，再改变录音/音频焦点和相关反馈，减少把自己的悬浮界面当作用户上下文的机会。上下文包含当前 App 与页面信息；密码节点遮蔽，受保护页面有检测与拒绝路径。条目有效期 120 秒，支持显式丢弃和关闭读取。

遮蔽不意味着自动识别并去除所有私人信息。普通文本、地址和页面内容仍可能包含敏感数据；上下文关闭也不阻止任务执行中为目标 App 验收而读取页面。

### 11.3 意图确认与追加

intake 区分办事、查询和追加。高置信度办事提供 3 秒取消窗口，低置信度办事要求确认，10 秒未确认取消。存在多个追加目标时展示选择，10 秒未选择按新任务处理。查询、办事和追加的路由按不同分支处理，不将所有语音统一强制确认。

`modify` 修改已有要求，`add` 增补，`cancel` 停止。追加进入队列并更新有效目标，保留原目标与修改记录；执行循环在受控点消费，不能在旧动作执行到一半任意改变对象。含追加的任务不用于自动编译新技能，避免将临时修订混入固定流程。

## 12. 技能学习与复用

### 12.1 从轨迹到技能

轨迹记录步骤、动作来源、UI 特征、动作前后指纹、参数与结果。符合学习条件的成功任务由 `SkillCompiler` 编译为参数化步骤，检查元数据、参数引用及动作结构；不是保存所有坐标然后盲目回放。需要人工交接、失败或含追加等不符合条件的轨迹不应沉淀为普通成功技能。

轨迹与技能使用临时文件发布/原子替换等方式，减少半写入文件进入读取流程。损坏技能跳过匹配。

### 12.2 匹配、定位和救援

- 仅将 active 且任务类型相符的技能交给匹配器，明确覆盖目标才匹配。
- 参数取自本次任务；必需参数缺失、类型错误或跨 App ID 歧义时放弃匹配。
- `SkillRunner` 根据当前 UI 的语义与指纹定位，不沿用旧 targetIndex 或旧坐标。
- 界面偏离时进入救援，最多 3 个救援动作，救援只恢复流程，不直接绕过完成验收。
- 仍失败则弃用本次技能，回到模型规划。
- 同技能连续弃用 3 次标为 `suspect`，不再作为普通 active 技能匹配。

### 12.3 管理与统计

技能页支持查看、启用、禁用和删除。用户禁用状态不会因普通学习被随意覆盖；成功的新探索可恢复 suspect 技能。记录使用次数、成功次数、平均耗时、平均模型调用，以及首次执行对比。一次“模型调用减少”只说明该样本，不是跨设备或跨任务性能基准。

查询技能保留“如何重新找到答案”的流程，不把上次答案/引文作为下次结果；每次仍需重新读取和验收。

## 13. 查询任务与来源证据

RPC `startTask` 的 `type` 支持 `action`（默认）和 `query`。查询流程：

```text
只读规划 → 搜索/浏览 → 提交候选答案与引文
  → 独立从当前证据提取 → 核对 → ANSWERED / NOT_FOUND / 失败或人工处理
```

`ReadOnlyGuard` 限制搜索、浏览、滚动等操作，阻止查询流程执行下单、支付、预订、发送、收藏/分享、启动导航等写操作。查询的人工请求限定在登录/验证码等允许边界。它是代码约束与页面判断组合，不应描述成对任意恶意页面或所有交易入口都形式化证明安全的沙箱。

输入必须落在当前确认的搜索框或有角色约束的高德路线端点。高德匿名搜索框需满足指定容器、编辑框位置和邻近搜索控件等条件；美团 MSC 城市选择只放行实际城市导航和对应搜索框，不能借此开放任意地址、交易或促销按钮。

`report_answer` 候选短答案上限为 60 个 Unicode 码点，并提供来源页面的连续引文。`AnswerVerifier` 独立提取后再比较，不先看候选答案照抄。系统版本类问题还区分系统版本、内核、构建号等字段。循环停滞时可转为受控的截图/OCR 查询恢复；不是硬编码预期答案。

找不到时应报告已查范围，`NOT_FOUND` 不能解释为整个互联网或整个 App 全局不存在。回答胶囊约显示 8 秒；复制与打开来源由用户点击触发。来源 App 的打开不等于恢复原查询虚拟屏的同一个页面。

## 14. 状态、历史、指标与本地存储

### 14.1 生命周期

| 状态 | 含义 |
| --- | --- |
| `QUEUED` | 等待调度或冲突解除 |
| `RUNNING` | 自动执行 |
| `PAUSED` | 因保护或冲突暂停 |
| `WAITING_USER` | 等待本人处理或交付接收 |
| `VERIFIED` | 办事结果验收通过并进入终态 |
| `ANSWERED` | 查询答案通过验收 |
| `NOT_FOUND` | 在本次搜索范围未找到 |
| `FAILED` | 执行、验收或恢复失败 |
| `CANCELLED` | 用户取消/停止 |
| `HANDOFF_TIMEOUT` | 交接等待超时 |

底层兼容字段和进度状态可能同时存在，集成时以字段含义区分，不把单个 `state` 字符串替代完整生命周期。需要读取最终证据的测试应等待归档完成标记 `finalized`，避免终态通知刚发出、截图和指标仍未落盘时就断言缺失。

### 14.2 保存位置

| 位置 | 内容与性质 |
| --- | --- |
| 控制端私有 `ai_config` SharedPreferences | 三角色密钥、地址、模型；不是加密保险库 |
| `/data/local/tmp/silent-shell.apk` | shell 运行文件 |
| `/data/local/tmp/silent-auto.pid` / `.log` | 后台进程标记与启动日志 |
| `/data/local/tmp/shadowauto/ocr/` | OCR 库、模型和字典 |
| `/data/local/tmp/shadowauto/skills/` | 按 App 包名组织的技能 JSON |
| `/data/local/tmp/shadowauto/trajectories/` | 执行轨迹 |
| `/data/local/tmp/shadowauto/history/` | 任务归档 |
| `/data/local/tmp/shadowauto/evidence/` | 证据截图 |
| `/data/local/tmp/shadowauto/context/` | 上下文临时文件 |
| `/data/local/tmp/shadowauto/no-screen` | 关闭上下文读取的设置标记 |

shell 文件位于 ADB shell 可访问区域，不等同于控制端私有目录。没有实现云同步、跨设备恢复、统一客户管理或永久审计存储。清理目录前先确认是否还要保留历史/技能，不能把“重新安装控制端”和“清空 shell 数据”混成一步。

### 14.3 干扰和性能指标怎么解释

记录模型角色调用次数、耗时、步骤来源、技能比较、抓图统计，以及前台 App 切换、焦点、IME 目标、剪贴板摘要变化、gfxinfo 等观测。剪贴板指标使用摘要，不能据此声称没有任何瞬时变化。

用户主动交接与意外切屏分别记录相关信息，归因是基于时间关联的辅助解释，不能当作严格因果证明。原始前台切换次数仍应保留。掉帧采样要标明 App/进程与时段；桌面样本不能代表用户持续刷信息流的体验。不可用指标应显示不可用，不能写成零或通过。

## 15. RPC 接口与连接可靠性

服务默认监听 `127.0.0.1:43110`，使用 TCP 上逐行 JSON-RPC，而不是 HTTP REST。请求带 `id`，响应按 ID 关联；订阅还会收到状态、日志或视频事件，不能把读到的第一行无条件当作当前请求结果。

| 方法 | 用途 |
| --- | --- |
| `ping` / `status` / `logs` | 健康、后台状态、近期日志 |
| `subscribeStatus` | 状态订阅，不接收视频流 |
| `startTask` / `stopTask` / `listTasks` | 创建、停止和列出任务 |
| `dumpUi` | 诊断读取当前任务 UI |
| `forceStart` | 显式任务控制入口；不是隔离认证 |
| `submitVoiceTask` / `confirmTask` | 提交语音理解、确认或取消 |
| `resolveVoice` | 指定追加目标，或选择 `new` |
| `amendTask` | `{taskId, kind, text}`，kind 为 modify/add/cancel |
| `captureContext` / `discardContext` / `setContextReading` | 上下文读取、丢弃和开关 |
| `resumeTask` / `injectTouch` | 交接响应、预览触摸 |
| `openTaskOnMain` / `takeManualOnMain` | 主屏查看/人工转移入口，按当前状态处理 |
| `takeResultOnMain` | 结果接收，返回实际转移与预览状态 |
| `noteUserHandoff` | 记录用户主动交接 |
| `openQuerySource` | 用户点击后打开查询来源 App |
| `listHistory` / `getTask` | 历史列表、步骤与证据 |
| `listSkills` / `setSkillStatus` / `deleteSkill` | 技能管理 |

无密钥健康请求示例：

```json
{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
```

任务结构示意（占位值不可直接运行；不要把真实密钥写入共享脚本）：

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "startTask",
  "params": {
    "taskId": "example-query-001",
    "type": "query",
    "goal": "用高德查苏州博物馆本馆地址，不启动导航",
    "models": {
      "executor": {"apiBase": "https://api.deepseek.com/v1", "apiKey": "<executor-key>", "model": "<executor-model>"},
      "verifier": {"apiBase": "https://dashscope.aliyuncs.com/compatible-mode/v1", "apiKey": "<verifier-key>", "model": "<vision-model>"}
    }
  }
}
```

完整参数以 `RpcServer` 分发和 `AutomationEngine` 实现为准。状态事件使用 `event="status"`，`type` 保留 action/query；控制端兼容旧 `type="status"`。

连接保护：

- `RpcConnection` 先绑定源端口，排除源端口等于服务端口的情况，最多重试 8 次，避免服务未启动时发生回环自连接。
- `RpcClient` 写入失败清理连接，不自动重放可能已经到达服务端的修改请求。
- 旧读线程只清理自己的 socket，不能误删已建立的新连接。
- `OverlayRpc` 使用状态订阅和按 ID 关联的等待结果，支持重连。
- 服务端每客户端独立发送队列，容量 64；慢客户端断开，不让视频/日志写阻塞全体任务。

当前 RPC 没有应用级鉴权；回环绑定也不等于同机其他进程完全不可访问。不要将端口转发给不可信网络或把它作为公开服务部署。

## 16. 开发、回归与验收方法

### 16.1 构建和静态检查

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug :android-shell:lintDebug :controller-app:lintDebug
```

Web 启动器修改后，在 `web-launcher` 执行 `npm run build`。只改文档不需要重新安装或重启手机。

### 16.2 主机交互回归

先完成 Android 构建，让脚本复用已解析的 Gson 2.11.0 缓存，然后：

```sh
./scripts/test-interaction-regressions.sh
```

脚本运行六组：`RpcConnectionRegression`、`UiRecoveryRegression`、`InteractionRegression`、`TransferProbe`、`QueryProbe`、`RpcOutputRegression`。覆盖连接拒绝、恢复预算、动作/只读保护、转移判断、查询与发送队列等逻辑。主机测试不证明厂商系统实际接受了 hidden API。

### 16.3 设备探针与模拟任务

`scripts/tests/CaptureMirrorDeviceRegression.java`、`TouchFlagsDeviceRegression.java` 是 Android 运行时探针，需要按专用设备流程编译/部署；不由上面的主机脚本自动运行。镜像探针包括拒绝主屏/负 ID、正常抓图、关闭后失败、UI 继续可读及重复释放。

以下模拟模型脚本会操作连接设备，只在专用测试设备/模拟器运行：

```sh
node scripts/emulator-mock-ai-test.mjs
```

模型模拟可检查流程，不替代真实模型、真实 App 和真实输入法验收。

### 16.4 建议验收顺序

1. 后台健康与控制端断线重连，确保服务关闭时正确拒连。
2. 固定公开页面反复“抓图→读控件”，记录模式、失败、代际、读取耗时与输出切换。
3. 计算器和地址查询等短任务，等待 finalized 并对照截图/引文。
4. 人工处理中主屏接管、交回后台、完成后去查看。
5. 人为制造转移失败、抓图失败、空根、停止/超时，检查真实回退与退出。
6. 用户在另一 App 持续中文输入、保留拼音候选词并滚动，后台同时搜索输入滚动；结合双方画面与日志验收隔离。
7. 再扩大到跨 App 调查和长文写入，不因前面测试通过就省略来源和正文核验。

录制主屏或导出截图可能包含私人信息，应事先获得使用者授权并使用空白测试页。

## 17. 已有真机结果与尚未解决的问题

主要实测设备为 Mate 40 Pro（NOH-AN01），HarmonyOS 4.2.0.192 / Android API 31。不能把设备名称和最低 SDK 当作适配列表。

### 17.1 最新短测：2026-10-24

已安装控制端连接修复，部署独立抓图镜像，完成构建/Lint与六组主机回归。实际控制端重连确认连接到了后台而非自身。

| 项目 | 结果 | 范围 |
| --- | --- | --- |
| 计算器抓图后读 UI | 20/20 成功，根不可读 0 | 读取中位/最大 7/170ms |
| 美团当前公开页抓图后读 UI | 20/20 成功，根不可读 0 | 9/15ms；验证弹窗关闭后的页面 |
| 高德首页抓图后读 UI | 20/20 成功，根不可读 0 | 33/393ms |
| 镜像故障与释放 | 3 轮通过 | 主动关闭后返回失败，不交换原屏面；UI 仍可读 |
| 普通计算 12×13 | ANSWERED，156，6 步 | 截图和独立提取核对 |
| 高德查询本馆地址 | ANSWERED，姑苏区东北街204号，4 步 | 搜索结果页确认，未进入独立详情页 |
| 首轮“先清除再计算” | **FAILED，25 步** | 曾算出 156 后又反复清除，属于仍存在的规划问题 |

60 轮重复抓图均为 `layer_stack_mirror`，misses=0、surfaceSwitches=0。两个成功短查询分别有一次镜像抓图成功。正式任务的前台、焦点、IME、剪贴板观测为 0；掉帧不可用，本轮未做持续中文候选词并行测试，**不认证全程无打扰**。

美团这轮没有通过商家/酒店调查；关闭验证弹窗返回公开页不等于完成验证。直接加载安装 APK 的一个临时设备测试被系统终止，未计为通过；后续使用同一连接源实现编译的设备测试通过，已安装控制端另有实际重连记录。

本轮未复现旧的“抓图后控件丢失”，但尚不能证明所有 ANR 均由旧换面抓图引起，更不能证明所有 App/系统都稳定。

### 17.2 前几轮已经覆盖的能力

- 语音震动、录音和执行中追加由使用者实际确认正常。
- 主屏处理后交回后台，使用者确认主屏操作流畅并能继续。
- 美团早期版本验证过完成提示与同页交付；不能代替最新版本完整业务验收。
- 备忘录有完整正文、保存后列表与重开检查，以及同一任务从虚拟屏移到主屏的记录。
- 川西路线查询已有部分多段证据；整套旅行规划、全部餐饮与住宿调查没有完成统一验收，不宣称已通过。
- 曾做另一 App 中文候选词与滚动并行体验，使用者反馈正常；期间旋转/重开导致窗口变化，原始焦点/IME 记录不全为 0，不能包装为严格无干扰通过。

### 17.3 仍需关注

1. **隔离能力：**该华为设备的回退配置未确认独立焦点/禁止抢焦点及本地 IME 完整能力，仍属于明确授权的兼容实验。无焦点触摸与按键门禁改善行为，但不提升为系统隔离认证。
2. **模型规划：**重复清除、无效等待、循环动作仍可能发生；最新失败样本已保留。
3. **第三方验证：**美团验证码会重复触发，登录状态和频道验证可能独立；不能自动替本人处理，也不能将未核实商家写成已确认。
4. **App 无响应：**高德历史上出现厂商窗口/布局相关 ANR。新的抓图和恢复减少一个触发因素，尚未证明根治全部原因。
5. **其他设备：**隐藏图层栈抓图和隔离 flags 依赖系统；旧抓图回退存在输出面切换，需独立验收。
6. **证据边界：**原始切屏计数、用户交接与测量范围必须一起报告；桌面掉帧不能外推到信息流，缺失指标不能记作通过。
7. **实时结果：**强杀或重启会丢失实时页面；没有跨重启恢复原 UI 会话的能力。
8. **产品化：**没有客户账号、服务端密钥分发、统一计费、发布更新系统或通用设备认证。

脱敏后的详细报告、循环数据、任务 JSON 与已检查截图见[仓库内证据索引](docs/evidence/README.md)和[抓图报告](docs/evidence/capture-stability/report.md)。原始资料继续留在工作区外，不直接公开。

## 18. 故障排查

| 现象 | 优先检查和处理 |
| --- | --- |
| ADB 没设备 | 数据线、USB 调试、授权弹窗；华为还需检查仅充电时允许 ADB 的选项 |
| 安装命令等待 | 查看手机系统安装器/锁屏验证；未确认前不能当作安装成功 |
| 控制端连接不上 | 先看 shell 日志与进程；安装控制端不等于启动后台；重连后确认 ping |
| 连接显示异常但后台未启动 | 确认使用包含 `RpcConnection` 修复的控制端，不把回环自连接当作成功 |
| 看不见悬浮球 | 悬浮窗权限、服务是否启动、通知/省电限制；先打开隐控检查权限引导 |
| 长按后不能立刻说话 | 等实际就绪震动；检查麦克风、音频焦点、上下文请求与 intake 配置 |
| 任务排队或无法再加 | 检查两任务上限、同 App 冲突、待接收结果/交接是否仍占资源 |
| 设备拒绝后台执行 | 查看 isolation 原因；不要删除门禁或默认打开实验参数 |
| 主屏闪烁/输入被影响 | 保留焦点、IME、前台和用户交接时间线；停止任务，确认隔离模式，不用自动拉起 App 掩盖 |
| 截图失败或黑屏 | 看 capture mode、misses 和回退原因；受保护页面转主屏本人处理 |
| 截图正常但空控件 | 看 rootsUnavailable、遍历诊断和代际；等待有限恢复，不对旧编号盲点 |
| 高德点击后没反应 | 检查新鲜目标、真实触摸和页面变化；App 自身 ANR 走有限恢复/人工处理 |
| 搜索无法回车 | 隔离不足时按键可能被主动阻止；应找实际搜索控件，不强行注入主屏按键 |
| 完成但没页面 | 区分查询自动收尾与办事保留结果；查验收、WAITING_USER、转移/回退与 finalized |
| 去查看失败 | 查看 moved 与 previewAvailable，使用可用预览重试或结束；不要重启 App 假装原页面恢复 |
| 长文看似写了但验收失败 | 核对完整正文、保存、列表和重开对象，不只对标题 |
| 模型接口失败 | 检查允许主机、角色配置、余额/权限和能力；日志不要包含 Key |
| OCR 不可用 | 检查外置 OCR 库、模型与字典是否上传到正确目录 |

## 19. 源码索引与资料组织

以下路径均相对仓库根目录：

| 要读什么 | 文件或目录 |
| --- | --- |
| 执行总流程 | `android-shell/src/main/java/com/silentauto/shell/AutomationEngine.java` |
| RPC 分发、事件和队列 | 同目录 `RpcServer.java` |
| 虚拟屏、截图、输入与 UI | 同目录 `VirtualDisplaySession.java`、`CaptureMirror.java`、`InputBridge.java`、`UiBridge.java` |
| 扩展模块 | 同目录 `ext/`，按 guard/handoff/skill/query/verify/context/voice/amend/history/metrics 分包 |
| 控制端任务页与配置 | `controller-app/src/main/java/com/silentauto/controller/` |
| 悬浮球、历史与上下文保护 | 控制端 `ext/overlay/`、`ext/history/`、`ext/context/` |
| OCR | `paddler-ocr/` |
| 浏览器部署 | `web-launcher/` |
| 回归脚本与探针 | `scripts/test-interaction-regressions.sh`、`scripts/tests/` |

本地工作区资料按用户约定放在仓库同级 `../docs/`：`tasks/` 为阶段任务，`guides/` 为指南，`reports/` 为分析，`validation/` 为原始验证产出。它们与仓库内的 `docs/` 不同；公开仓库不使用个人电脑绝对路径作为证据链接。

上游技术原理文章：[中文](docs/android-virtual-display-ai-automation.md) / [English](docs/android-virtual-display-ai-automation.en.md)。它们介绍上游架构，不覆盖本分支全部扩展与最新验证。

上游演示视频（不是本分支新增功能的验收录像）：

<video src="shadowauto.mp4" controls width="100%"></video>

本项目沿用 [Apache License 2.0](LICENSE)。再分发时保留适用的原始署名、许可证与修改说明；原始贡献者的权利和本分支修改说明分别见 [NOTICE](NOTICE) 与 [DERIVATIVE_CHANGES](docs/DERIVATIVE_CHANGES.md)。
