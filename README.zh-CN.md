# 隐控 ShadowAuto（二次开发版）

[English](README.md) | 中文

> 本项目基于 [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto) 二次开发，保留原项目及作者、贡献者署名，继续采用 [Apache-2.0](LICENSE)。上游基线为 `611e5eb`；本分支新增和修复详见 [二次开发改动说明](docs/DERIVATIVE_CHANGES.md) 与 [NOTICE](NOTICE)。

隐控 ShadowAuto 是一个面向 Android 10 及以上设备的开源手机助手原型，支持后台自动化、技能学习和语音发起任务。它通过 `adb shell` 和 `app_process` 启动 AI 驱动的 shell 进程，把真实 App 打开在独立 VirtualDisplay 中运行；用户主屏幕可以继续正常使用，自动化任务则在后台虚拟屏里静默执行。

ShadowAuto 的重点不是简单脚本点击，而是让 AI 具备类似手机助理的屏幕理解和操作能力：shell 进程可以读取虚拟屏上的 UiAutomation UI 节点树，也可以在无障碍节点缺失、自绘页面或复杂业务页面中调用离线 OCR 读取屏幕文字和坐标，再通过 InputManager 把触摸、按键和输入事件注入到指定 display。

以下功能包含上游基础能力及本分支扩展；各自来源见[改动说明](docs/DERIVATIVE_CHANGES.md)。

## 已实现功能

| 功能 | 当前能力 |
| --- | --- |
| 后台执行与实时投屏 | 每个任务使用独立 VirtualDisplay；读取 UI 节点，必要时调用 Paddle Lite 离线 OCR，通过 H.264 查看执行画面 |
| 自动操作与独立验收 | 支持点击、输入、搜索、滚动、返回等操作；执行模型负责规划，视觉验收模型根据当前界面判断是否完成 |
| 多任务调度 | 最多同时执行两个任务；同一 App 串行排队，主屏正在使用目标 App 时等待或暂停，离开后重新观察并继续 |
| 技能学习与复用 | 录制轨迹，将通过验收且未经过人工交接的任务编译为参数化技能；使用当前控件特征定位，支持匹配、重放及停用/删除 |
| 技能异常救援 | 页面偏离时最多尝试三步救援；失败后回退模型探索，连续放弃的技能标为 suspect，避免继续自动匹配 |
| 语音悬浮球 | 64dp 触摸区域、曲面屏边距、拖动吸边；按住约 1 秒，录音真正就绪后短震，松手提交，上滑取消 |
| 边看边办 | 语音发起前可读取主屏截图和文字，辅助理解“这家店”等指代；支持“不读取屏幕”、密码区域遮盖及临时上下文过期清理 |
| 状态胶囊与人工交接 | 展示执行、排队、待确认和待处理状态；验证码、登录、支付等需要人工处理时通知用户；“到主屏处理”移交原页面，处理后点“交回后台继续”，失败时保留预览 |
| 查询答案带回 | QUERY 任务只读搜索与浏览，独立提取并核对页面原文；答案和来源在胶囊展示 8 秒，用户可主动复制或打开来源 App；支持没查到及不确定标记 |
| 执行中追加 | 同一次语音理解可修改、补充或取消进行中的任务；拿不准时询问归属，排队、暂停和人工等待状态均可接收追加；历史记录追加时间线，有追加的任务不编译技能 |
| 结果保留与主屏接手 | 选餐厅、挑商品、查询比较等任务可保留结果；点“去查看”转移原任务，核对结果名称、地址等关键内容后释放虚拟屏；失败时检查回退结果并提供预览或明确提示 |
| 任务历史与效率统计 | 查看结果、验收截图、调用次数、耗时、技能命中及 model/skill/rescue 步骤来源 |
| 干扰观测与输入保护 | 记录前台变化、焦点、输入法、剪贴板和掉帧指标；包含同 App 冲突暂停与剪贴板保护，无法测量的指标显示未知 |
| 分角色模型配置 | executor、verifier、intake 分别配置；密钥保存在手机应用私有配置中，当前没有统一的服务端密钥分发 |

### 验证进展（2026-10-22）

- **主屏人工处理：** 新增“到主屏处理 → 交回后台继续”。构建/Lint、往返与故障逻辑探针通过；真机美团已通过同一餐厅任务到主屏人工处理、用户点继续后交回后台、最终完成提示和“去查看”，释放虚拟屏后页面仍保留；用户确认主屏操作正常。本轮重跑未再触发滑块，人工流转用选店确认边界验证。

- **本地与模拟器**：Step 2–5 已实现，具备构建、Lint、逻辑探针、mock 集成和原生界面回归记录；这些结果不等于所有真实 App 均已兼容。
- **新增查询/追加（5.8/5.9）：** 专用模拟器夹具与本地模型 mock 已覆盖答案依据、只读拦截、技能重放、活动任务追加、归属超时、历史及原生胶囊交互。Mate 40 Pro 已用真实模型通过计算器查询、执行中修改/取消与胶囊展示；系统版本查询的重复滚动已通过独立截图/OCR 恢复，在真机读到完整版本号，另增加系统版本字段检查，正常配置连续三轮复查通过；用户已确认震动、录音和语音追加正常。默认办事结果保留、实际“去查看”入口、真实转移失败后的预览回退及重试到主屏均已在计算器上通过，虚拟屏释放后原结果仍在；更多 App 兼容性仍需测试。旧 Settings 基线还有一项输入文字可见性断言失败。
- **Mate 40 Pro / HarmonyOS 4.2.0.192**：已完成 ADB 部署、真实模型接口测试、计算器及美团餐厅选择；上一版已验证结果预览、同一餐厅任务转到主屏，以及虚拟屏释放后页面仍保留。后台回退另有真机故障注入记录。
- **最新交接修复 `d1df15e`**：已完成实体文字校验、回退状态区分、语义结果保留、统一查看入口和交接中状态，构建/Lint/本地边界回归通过；后续 `7e9d205` 已用计算器通过同一任务主屏转移和真实转移失败回退；后续已通过美团选店、主屏人工确认往返及最终“去查看”成功流程；本轮未再触发验证码。真实语音理解准确率、长时间保活和其他厂商兼容性仍需继续验证。

## 一次任务如何完成

输入文字或按住悬浮球说话 → 可选读取当前屏幕 → 模型理解与确认 → 匹配技能或模型探索 → 独立视觉验收 → 办事结果默认保留等待“去查看”（明确不保留时自动结束），查询则把答案带回胶囊。遇到验证码等人工边界时暂停交接；结果需要查看时，将原页面交到主屏继续操作。

## 上游演示视频

以下视频沿用原项目素材，展示上游版本，不代表本分支全部新增功能。

<video src="shadowauto.mp4" controls="controls" style="max-width: 100%;"></video>

如果当前页面不支持直接播放视频，可以打开 [shadowauto.mp4](shadowauto.mp4) 查看。

## 上游技术文章

- [Android 虚拟投屏与 AI 后台静默自动化实践](docs/android-virtual-display-ai-automation.md)
- [Android Virtual Display and AI Background Automation](docs/android-virtual-display-ai-automation.en.md)（英文）

## 工程结构

- `android-shell`：通过 `app_process` 启动的 shell 自动化进程。负责创建 VirtualDisplay、启动应用、读取 UI 节点、调用 OCR、注入输入、操作剪贴板、执行 AI tool-call 循环、提供 JSON-RPC、输出日志和推送 H.264 虚拟投屏。
- `controller-app`：Android 控制端 App。包含模型配置、文字任务、语音悬浮球、状态胶囊、投屏与交接、任务历史和技能管理。
- `web-launcher`：Svelte + TangoADB WebUSB 启动器。页面只展示一个启动按钮，点击后选择设备、上传 shell APK、OCR 文件和控制端 APK，并启动 `app_process`。
- `android-stubs`：shell 模块编译时使用的 Android hidden API stub，仅用于编译。
- `scripts`：本地测试脚本。

## 环境要求

- Android 10 及以上设备；虚拟屏、hidden API 和主屏转移能力需要按厂商系统验证。
- 目标设备已开启 ADB 调试。
- 默认只需要 shell 权限，不需要 root。
- JDK 17。
- Web 启动器需要 Node.js 和 npm。
- `web-launcher` 需要 Chrome 或其他支持 WebUSB 的浏览器。
- executor 需要支持 streaming 和 tool calls 的兼容 chat-completions 接口；verifier 需要视觉输入，使用语音功能时 intake 还需支持音频与图片输入。各角色可使用不同服务商。

## 编译

编译 shell 和控制端 App：

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
```

把最新 shell APK 和 OCR 运行时文件同步到 Web 启动器静态目录：

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
```

安装控制端 App：

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
```

## 启动 Shell 进程

### 方式一：浏览器启动器

上游线上启动器：[https://android-notes.github.io/ShadowAuto/](https://android-notes.github.io/ShadowAuto/)。该站点由上游维护，不代表已部署本分支新增功能；使用本分支请构建并部署本地生成的产物。

连接前请先完成手机准备：

- 打开手机开发者选项，并开启 USB 调试。
- 部分小米手机还需要开启 USB 调试（安全设置）。
- 首次连接手机可能会弹出允许调试提示，请在手机上点击允许。
- 安装控制端 App 时，如果手机弹出允许安装提示，请点击允许安装。
- 安装完成后需要先在隐控 App 中配置 API KEY 才能使用手机自动化。
- 开发者选项参考：[小米](https://jingyan.baidu.com/article/ce436649ca6c877773afd3e2.html)、[华为](https://jingyan.baidu.com/article/a378c960e87118b3282830bc.html)、[OPPO](https://jingyan.baidu.com/article/cb5d6105b0936a005d2fe052.html)、[VIVO](https://jingyan.baidu.com/article/335530da406f4358cb41c3b4.html)。

也可以在本地运行：

```sh
cd web-launcher
npm install
npm run dev
```

用 Chrome 打开终端输出的本地地址，点击 **启动隐控Assistant**，选择 Android 设备，并按 Android 弹窗授权 ADB。

如果浏览器提示设备已被其他程序占用，请关闭 Android Studio 并杀死 adb 后再次重试：

```sh
adb kill-server
```

### 方式二：手动 ADB

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
adb push web-launcher/static/silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell "rm -rf /data/local/tmp/shadowauto/ocr && mkdir -p /data/local/tmp/shadowauto/ocr"
adb push web-launcher/static/ocr/. /data/local/tmp/shadowauto/ocr/
adb shell "if [ -f /data/local/tmp/silent-auto.pid ]; then kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi"
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup sh -c 'exec app_process /system/bin com.silentauto.shell.Main --port=43110' >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

OCR 文件是 `get_screen_ocr` tool call 必需的。只推 `silent-shell.apk` 时，普通自动化可以启动，但遇到无障碍节点缺失的页面时 OCR 兜底会失败。

如果启动后 logcat 出现 `ClassNotFoundException: com.silentauto.shell.Main`，通常是 `silent-shell.apk` 没有成功推送到设备。先检查：

```sh
adb shell "ls -lh /data/local/tmp/silent-shell.apk"
```

连接多个设备时，建议所有命令都加 `-s <serial>`，例如 `adb -s emulator-5554 push ...`。

查看 shell 日志：

```sh
adb logcat -v time -s ShadowAutoShell
```

停止 shell 进程：

```sh
adb shell "kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid"
```

## 使用 Android App

1. 通过 ADB 启动后台后，在手机打开隐控，分别填写 executor（执行）、verifier（视觉验收）和 intake（语音理解，使用语音时配置）的 API Key、URL 与模型，并测试连接。
2. 输入文字目标，例如：`在美团帮我选一家适合约会的餐厅，只浏览，不预订、不下单、不支付`，点击执行。
3. 在任务页查看后台投屏与进度；点击加号可创建其他任务，调度器最多同时运行两个任务。
4. 使用语音时，在权限引导中启用悬浮窗和麦克风。按住小球约 1 秒，**等短震后再说话**，松手提交，上滑取消；实际就绪时间也取决于上下文读取速度。
5. 高置信度语音理解有 3 秒取消窗口；低置信度任务需确认，10 秒无确认自动取消。可在设置中关闭屏幕上下文读取。
6. 遇到验证码、登录或支付边界，点“去处理”并手动完成；受保护页面按提示转到主屏处理。
7. 需要查看的结果验收后显示“去查看”。点击后尝试把原页面转到主屏；若回退后预览可用，可以操作预览、重试切到主屏或结束查看。未查看的结果仍占用任务槽位。
8. 在“历史”查看结果、截图和统计，在“技能”管理已学习流程；停止按钮可结束当前任务，顶部停止按钮可停止全部任务。

## 核心原理

隐控采用 shell 侧 ReAct 风格自动化循环：

1. shell 进程通过 `adb shell` + `app_process` 启动，因此可以在 shell 权限下访问 Android 系统服务和 hidden API。
2. 每个自动化任务启动时，shell 创建一个 VirtualDisplay，并把目标 App 启动到该虚拟屏中。
3. 控制端 App 通过 `127.0.0.1:43110` 向 shell 发送 JSON-RPC `startTask` 请求，请求中包含用户目标和大模型配置。
4. shell 请求大模型，让模型根据目标和当前 UI 状态进行推理，并通过 tool call 选择下一步动作。
5. tool call 会执行真实 UI 操作：获取布局、点击目标节点、点击坐标、聚焦输入框、输入文本、全选、删除、粘贴、滚动、拖拽、返回、回车/搜索、等待或完成任务。
6. UiAutomation 负责读取虚拟显示屏上的 UI 节点树。shell 可以返回简化后的可操作布局，也可以返回完整布局。
7. 当 UI 节点为空、错误或不足以决策时，AI 可以调用 `get_screen_ocr`，shell 通过 Paddle Lite OCR 读取屏幕文字、置信度和坐标框。
8. InputManager 把触摸和按键事件注入到目标 display，而不是手机主屏幕。
9. VirtualDisplay 直接渲染到 MediaCodec 输入 Surface。shell 推送 H.264 配置和 sample，Android App 解码到 TextureView；进度日志通过同一本机 JSON-RPC 事件通道发送。
10. 模型声明完成后，视觉验收器核对当前结果；需要人工处理或查看时进入等待交接，交付完成后才清理对应虚拟屏并归档。

这种架构可以让目标应用在虚拟屏里运行，用户则在控制端 App 中观察进度，不需要把自动化操作暴露在主屏幕上。

## Paddle Lite 离线 OCR

`paddler-ocr` 模块负责 PaddleOCR Android 集成，用于对不可访问、自绘页面做视觉兜底。shell 进程会直接调用该模块，controller-app 不再包含 OCR 代码。OCR 默认开启；构建或复制运行时文件执行：

```sh
./gradlew :paddler-ocr:preparePaddleLiteOcr
./gradlew :android-shell:copyPaddleLiteOcrRuntime
```

该任务会下载 PaddleLite v2.10、OpenCV Android SDK、PP-OCRv2 中文移动端模型和字典。生成文件位于 `paddler-ocr/PaddleLite`、`paddler-ocr/OpenCV`、`paddler-ocr/PaddleOcrAssets`，这些目录不会提交到 git。

Java 入口是 `com.silentauto.paddlerocr.PaddleOcrEngine`，调用 `init()` 后可通过 `recognize(Bitmap)` 获取文本、置信度和坐标框。运行时文件会复制到 `android-shell/build/ocr-runtime`，并由浏览器启动器上传到 `/data/local/tmp/shadowauto/ocr`。

## JSON-RPC 概览

shell 只监听本机回环地址：

```text
127.0.0.1:43110
```

主要方法：

- `startTask` / `stopTask` / `listTasks`：启动、停止和查看任务；启动支持分角色 `models` 配置，并保留原有 `apiKey`、`apiBase`、`model` 参数兼容。
- `submitVoiceTask` / `confirmTask`：一次语音理解，并确认或取消新的办事任务。
- `amendTask {taskId, kind, text}`：向活动任务追加 `modify`、`add` 或 `cancel`。
- `resolveVoice {voiceId, taskId}`：选择列出的追加目标或 `"new"`；10 秒未选择则作为新任务处理。
- `captureContext` / `discardContext` / `setContextReading`：读取、丢弃或关闭屏幕上下文。
- `resumeTask` / `injectTouch` / `takeResultOnMain`：人工交接、预览触摸和完成结果主屏转移。
- `listHistory` / `getTask`：任务历史与详情。
- `listSkills` / `setSkillStatus` / `deleteSkill`：技能列表、状态及删除。
- `status`：返回当前 shell 状态。
- `logs`：返回最近日志。
- `ping`：健康检查。

当前 JSON-RPC 没有鉴权，因为它只绑定本机回环地址，并且设计上只给本机控制端 App 使用。不要把该端口暴露到外部网络。


查询 API：`startTask` 可传 `type="query"`（默认 `action`）；查询终态为 `ANSWERED` / `NOT_FOUND`。状态通知使用 `event="status"` 标识事件，`type` 保留任务类型；控制端兼容原 `type="status"` 通知。`openQuerySource` 只在用户点击“去看看”后打开来源 App。

## 注意事项和限制

- 大模型服务商会收到任务目标和自动化所需的 UI 上下文。请不要在不可信模型服务中处理敏感内容。
- 受 Android 安全限制，部分受保护页面在投屏时可能黑屏，例如支付密码页面。
- 已有同 App 冲突暂停及剪贴板保护，但不承诺所有厂商和 App 都完全无干扰；目标 App 被移到主屏时可能暂停或需要恢复观察。
- 未查看的结果保存在运行中的虚拟屏里；强制停止后台进程或重启手机会丢失该实时页面，历史截图不等于可继续操作的会话。
- 语音录音需麦克风和悬浮窗权限；实际录音就绪以短震为准，其他 App 的音频恢复取决于其自身实现。
- hidden API 和 shell 权限行为可能因 Android 版本、厂商系统而变化。
- 浏览器启动器需要独占 WebUSB 设备。Android Studio 或正在运行的 adb server 可能会占用设备。

## 开发测试

构建与静态检查：

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug :android-shell:lintDebug :controller-app:lintDebug
```

连接专用测试设备或模拟器后，可运行基础 mock 测试（会操作测试设备）：

```sh
node scripts/emulator-mock-ai-test.mjs
```

本地工作区的阶段任务、指南、报告和回归产出集中在仓库同级 `../docs/`（`tasks/`、`guides/`、`reports/`、`validation/`）。该目录不属于当前 Git 仓库，单独克隆仓库不会包含这些资料。

## 开源协议

本二次开发版本沿用上游 [Apache License 2.0](LICENSE)，保留原许可证和适用的原始署名。原项目：[android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto)。出处和修改范围见 [NOTICE](NOTICE) 与 [二次开发改动说明](docs/DERIVATIVE_CHANGES.md)。
