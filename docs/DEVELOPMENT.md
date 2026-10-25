# 开发与技术方案

维护日期：2026-10-26。基线：`cf017b7` 提交及本次发布准备中的锁屏保护增量，包含 `DeviceLockState`、`AutomationEngine`、`MainAppConflictGuard` 和 `ScreenLockRegression`。Step6 原有 Android 验证基线为 `cedab19`；锁屏增量的验证单独见[锁屏报告](evidence/screen-lock/report.md)。本次仅同步开发文档，未改运行代码。

本文是本分支的开发维护基线，回答“目前实现了什么、如何实现、修改时要保持哪些约束、哪些结论尚未验证”。面向接手开发、排查问题和评审实现的人。安装、权限与日常操作见[中文使用手册](USER_GUIDE.zh-CN.md)；演示素材见[视频索引](evidence/demo-path/VIDEO.md)；阶段验收明细见[Step6 状态](STEP6-STATUS.md)。使用手册中的旧轮次记录按当时范围理解，当前阶段状态以本文和 Step6 状态为准。

## 1. 实现范围与验证状态

系统是同一台 Android 手机上的自动化原型：控制端接收任务，shell 进程在虚拟屏操作真实 App，完成后返回答案或将原页面交给用户。上游基线 `611e5eb0e1c94befda3c17c97438912dfd30e8a8` 提供 shell/app_process、虚拟屏、UI/OCR、输入与预览底座；本分支扩展验收、交接、技能、语音、保护与归档，具体归属见[二次开发说明](DERIVATIVE_CHANGES.md)。

下表的“实现”表示存在可执行代码；“验证”限定在已归档样本，不代表通用适配。

| 能力 | 当前实现 | 已验证的范围与缺口 |
| --- | --- | --- |
| 后台任务与预览 | 每任务虚拟屏、目标 App 启动、UI/OCR、输入、H.264 预览 | Mate 40 Pro 有实际任务记录；微信启动退出，部分页面根节点不可读 |
| 调度与主屏优先 | 最多两个活动槽位；同 App 串行；与主屏同 App 冲突时等待/暂停 | 具备保护逻辑；跨设备和长期并发稳定性未覆盖 |
| 锁屏暂停与解锁续跑 | 熄屏/Keyguard 门禁；新任务排队、运行中暂停保留；解锁重新观察；不消耗锁屏期间的冲突超时预算 | Mate 40 Pro 同一计算器任务第 5 步暂停，解锁后第 8 步 ANSWERED 579；仅证明解锁续跑，不能锁屏持续操作或跨进程恢复 |
| 输入与焦点保护 | 显示屏定向输入、无焦点触摸、危险键门禁、节点归属检查、剪贴板保护 | 三轮中文输入采样四项均 0、机主反馈正常；原生独立焦点和本地 IME 能力不足，不认证全程隔离 |
| 办事验收与交付 | 独立验收、文档完整性核对、原任务转主屏、转移失败回退 | 备忘录同页交接有证据；不能用启动另一个页面替代原页面交付 |
| 查询 | 只读动作约束、短答案、原文引文、独立提取与比较 | 计算器和公共地址有成功样本；公开时间查询另有不确定/截断答案，地址复测亦有失败 |
| 重复动作退出 | 有限窗口检测，第一次反馈，第二次进入独立验收 | 7 步 ANSWERED 156；另一次 16 步重复验收失败，均保留 |
| 技能学习与复用 | 参数化编译、语义定位、页面指纹、有限救援、模型回退、状态管理 | 备忘录探索/复用/救援三段均 VERIFIED；调用 10→8→14，耗时约 27.7→49.4→63.3 秒，未证明提速 |
| 语音与执行中追加 | 悬浮球录音、intake 理解、任务路由、modify/add/cancel 队列 | 真语音修改大杯；另一次大杯冰美式到支付前 WAITING_USER 并交接，机主确认规格；成功流程连续双屏录像缺失 |
| 主屏上下文 | 录音前采集、密码节点遮蔽、短时上下文、开关与丢弃 | 公开酒店名→备忘录成功；来源未显示完整地址，未验证地址传递或微信发送 |
| 历史、指标与证据 | 步骤来源、角色调用数、耗时、截图、隔离诊断、原始干扰采样、任务归档 | 有脱敏仓库证据；缺失/不可用观测不能计作零 |
| 录制与演示 | READY 识别、双路 scrcpy、虚拟屏销毁收尾、可重建剪辑 | 已有 02:57 五段证据版；含失败、静态证据和归档说明，非一次连续全成功录像 |

当前没有实现：RPC 应用级鉴权、免电脑 Shizuku 启动、云账号与计费、跨重启恢复原 UI 会话、通用设备隔离认证。微信发送验收未通过。私人订单查询由机主选择跳过，不属于已验收功能。

## 2. 系统分层与部署边界

```mermaid
flowchart TD
    U[用户文字 / 悬浮球语音] --> C[controller-app：配置、任务页、预览、交接]
    C -->|本机 TCP 逐行 JSON-RPC| R[RpcServer]
    R --> E[AutomationEngine：任务编排]
    E --> G[调度 / 锁屏门禁 / 主屏冲突 / 输入保护]
    G --> D[VirtualDisplaySession：任务虚拟屏]
    D --> A[真实目标 App]
    A --> O[UiBridge / CaptureMirror / OCR]
    O --> P[技能或模型规划]
    P --> X[ActionExecutor / InputBridge]
    X --> D
    O --> V[ResultVerifier / 查询核对]
    V --> H[原页面交付 / 答案 / 任务归档]
    H --> C
    E --> M[已配置模型服务：executor / verifier / intake]
```

| 模块 | 职责与入口 |
| --- | --- |
| `android-shell` | shell 权限执行进程；[Main](../android-shell/src/main/java/com/silentauto/shell/Main.java) 启动，[RpcServer](../android-shell/src/main/java/com/silentauto/shell/RpcServer.java) 分发，[AutomationEngine](../android-shell/src/main/java/com/silentauto/shell/AutomationEngine.java) 编排 |
| `controller-app` | 用户界面与连接；[MainActivity](../controller-app/src/main/java/com/silentauto/controller/MainActivity.java)、[AutomationTaskView](../controller-app/src/main/java/com/silentauto/controller/AutomationTaskView.java)、[RpcClient](../controller-app/src/main/java/com/silentauto/controller/RpcClient.java) |
| `android-stubs` | 编译期系统接口声明；不是运行时系统实现，也不提供额外权限 |
| `paddler-ocr` | OCR 库、模型与字典；运行资产需部署到设备 |
| `web-launcher` | 浏览器部署入口；不承担手机上的自动化循环 |
| `scripts` | 主机回归、设备探针、采样汇总与录像整理 |

shell APK 通过 ADB / `app_process` 作为 shell 进程运行。普通控制端 APK 的安装不能替代后台进程启动。代码使用系统服务与隐藏接口，API 声明存在不等于某个厂商系统允许调用。两 Android 模块当前 `minSdk=29`、`compileSdk/targetSdk=36`，Java 源码级别 17；最低 SDK 不等于完成该版本设备适配。

## 3. 任务编排与状态契约

一次任务主要经过：解析目标/角色配置 → 应用选择与技能匹配 → 调度准入 → 创建虚拟屏 → 观察与执行循环 → 验收或人工等待 → 交付/清理 → 归档。具体顺序与分支以 `AutomationEngine` 为准。

[TaskScheduler](../android-shell/src/main/java/com/silentauto/shell/ext/guard/TaskScheduler.java) 同步维护活动槽位，最多两个、同 App 不重入；`force` 不突破两个槽位和同 App 串行约束。[MainAppConflictGuard](../android-shell/src/main/java/com/silentauto/shell/ext/guard/MainAppConflictGuard.java) 与前台观测处理主屏冲突。资源释放要覆盖成功、异常、停止、等待超时与转移后的收尾。

### 3.1 锁屏保护与续跑

[DeviceLockState](../android-shell/src/main/java/com/silentauto/shell/DeviceLockState.java) 通过 `!PowerManager.isInteractive() || KeyguardManager.isKeyguardLocked()` 判断阻塞。熄屏与亮屏但 Keyguard 未解除都不能执行自动输入；不自动唤醒或绕过系统锁屏。

- **准入：**`awaitAdmission` 约每 2 秒检查一次，锁屏期间保持 `QUEUED`，不申请新调度槽位、不启动目标 App；`forceStart` 也不绕过锁屏门禁。应用选择/技能匹配等准入前准备仍可能调用模型，不能宣称锁屏排队零调用。
- **运行：**`MainAppConflictGuard` 约每秒观察；首次发现锁屏时增加 epoch、设置恢复标记并转 `PAUSED`，保留任务与虚拟屏。`inputAllowed()` 另外直接检查锁屏状态，输入门禁不依赖下一次定时观察。重复观察同一次锁屏不会反复增加 epoch。
- **超时：**锁屏期间持续刷新 `pausedAt`，解除锁屏时也重置计时起点；若解锁后主屏仍使用同一 App，则继续暂停并重新计算十分钟冲突超时。当前实现不是保留锁屏前已消耗的冲突预算。
- **续跑：**解锁且主屏冲突解除后返回 `RUNNING`，由 `recoverAfterConflict` 重新观察。循环内遇到锁屏或冲突 epoch 变化相关异常，等待后丢弃本轮规划再重试；被打断输入不消耗执行步数。首次启动 App 因锁屏失败也等待后重试。用户停止/线程中断仍正常退出，不归入自动重试。
- **边界：**任务和虚拟屏资源保留不代表厂商 App 页面永远不变；没有进程强杀、重启或 App 自行销毁页面后的原会话恢复。锁屏分支只将 `RUNNING` / `PAUSED` 转换，不能外推为 `WAITING_USER` 等所有等待状态的超时均被挂起。

Mate 40 Pro（API 31）锁屏会用 SystemUI 时钟覆盖虚拟屏，目标 Activity 进入 STOPPED；局部唤醒锁不能阻止此行为。独立显示组创建因缺少 `ADD_TRUSTED_DISPLAY` 被拒，`ALWAYS_UNLOCKED` 标志不存在；这些探针未改变正式虚拟屏 flags。因此方案采用解锁后续跑，不能宣称锁屏持续操作。

2026-10-25 的已有实机记录：同一计算器任务在 display 158 第 5 步锁屏暂停，68 秒检查仍保持步骤、display 和 executor 8 次；机主解锁后第 8 步 `ANSWERED` 579、`finalized=true`，executor 12 次、verifier 1 次，总耗时 642,079ms（含等待）。另一个锁屏新任务保持第 0 步排队后取消。详见[锁屏验证记录](evidence/screen-lock/report.md)。本次文档同步未重新执行这些设备测试。

### 3.2 对外状态与归档契约

[TaskProgress](../android-shell/src/main/java/com/silentauto/shell/ext/TaskProgress.java) 维护对外状态与指标；[TaskStatus](../android-shell/src/main/java/com/silentauto/shell/ext/TaskStatus.java) 定义终态。

| 状态 | 转换约束 |
| --- | --- |
| `QUEUED` | 准入后进入 `RUNNING` |
| `RUNNING` | 可进入 `PAUSED`、`WAITING_USER`，或 `VERIFIED` / `ANSWERED` / `NOT_FOUND` |
| `PAUSED` | 解除保护后回到 `RUNNING` |
| `WAITING_USER` | 本人完成后回 `RUNNING`，或 `HANDOFF_TIMEOUT` |
| 取消/失败 | 非终态允许进入 `CANCELLED` / `FAILED`；终态不再普通转换 |

`WAITING_USER` 不是办事成功；支付前暂停可属于正确边界处理，但不能写成已付款或整单完成。`VERIFIED` 是办事验收终态，`ANSWERED` 是查询答案终态。验收还应读取 `uncertain`、引文与证据，不单看状态名。

`status` 到达终态与归档完成不是同一时刻。读取最终截图、指标和技能比较时应等待 `finalized=true`。接口集成同时保留 `taskId`、`displayId`、`originalGoal`、有效 `goal`、`amendments`、`transferState`、`resultReady`、`protectedScreen`，不能从单一字段推断页面仍可用。

## 4. 观察、规划与动作执行

每轮先等待锁屏/主屏冲突解除，再检查后台安全与追加目标，之后读取当前 display 的 UI。根节点不可用、遍历不完整与真实叶节点分开判断；[UiRecoveryBudget](../android-shell/src/main/java/com/silentauto/shell/ext/guard/UiRecoveryBudget.java) 限制等待、无障碍重连及查询重启，耗尽后转人工。查询重启仅在确认目标 App 仍在虚拟屏且未位于主屏时尝试。

动作优先取自技能和扩展策略，不能生成时由 executor 规划；最终由 [ActionExecutor](../android-shell/src/main/java/com/silentauto/shell/ext/ActionExecutor.java)、`InputBridge` 等落实。`StepListener` 贯穿观察和动作记录，动作来源用于区分 model、skill、rescue、query_recovery 等路径。

规划期间记录 UI 连接代际、冲突 epoch 与目标版本。执行前发生变化则丢弃旧规划、重新观察；[ObservedTarget](../android-shell/src/main/java/com/silentauto/shell/ext/guard/ObservedTarget.java) 和 [NodeWritePolicy](../android-shell/src/main/java/com/silentauto/shell/ext/guard/NodeWritePolicy.java) 校验当前目标及窗口/包名归属。节点索引和坐标是当次观察的结果，不能跨重连或页面变化直接复用。

[LoopDetector](../android-shell/src/main/java/com/silentauto/shell/ext/guard/LoopDetector.java) 保留最近 6 次观察，检测相同屏幕/动作三次或屏幕 ABAB 交替。第一次向 executor 提供反馈；再次触发则不继续 executor 规划，直接独立验收/提取答案，无可核验证据则失败。技能回放不参与重复计数；目标追加后清空检测窗口。这是有限模式检测，不能识别所有业务循环。

## 5. 显示屏、抓图与输入隔离方案

[VirtualDisplaySession](../android-shell/src/main/java/com/silentauto/shell/VirtualDisplaySession.java) 负责屏幕、输出面及隔离能力诊断。优先尝试系统支持的独立焦点/禁止抢焦点等 flags，并记录候选、拒绝原因与最终 flags；兼容回退必须保持可见诊断。

截图优先采用 [CaptureMirror](../android-shell/src/main/java/com/silentauto/shell/CaptureMirror.java) 的独立图层栈镜像与持续图像排空，避免为抓图切换原视频输出面，减少“抓图后控件不可读”的触发因素。隐藏接口不可用时仍可能回退旧路径；应分别报告 capture mode、misses、surfaceSwitches，不把回退当作相同保证。

输入保护分多层：

- 显式 displayId、无焦点触摸及 `BackgroundKeyPolicy` 限制可能影响主屏的按键。
- 当前节点与目标包名检查；搜索提交由 `SubmitTargetPolicy` 排除语音/相机图标等误目标。
- `ClipboardGuard` 处理需要剪贴板的输入与恢复；剪贴板恢复不等于从未发生瞬态变化。
- 主屏同 App 冲突优先让用户操作；不能靠强行切回后台掩盖冲突。

[DisturbanceMonitor](../android-shell/src/main/java/com/silentauto/shell/ext/metrics/DisturbanceMonitor.java) 约 500ms 采样焦点、IME、前台和剪贴板摘要等，原始时间线有上限，未知值、间隔和截断单独报告。Mate 40 Pro 当前 `behaviorValidated=false`，三轮正常反馈与零采样不改变这个结论。能力与实验模式说明见 [ISOLATION](ISOLATION.md)。

## 6. 完成验收、查询与人工交接

executor 负责规划；[ResultVerifier](../android-shell/src/main/java/com/silentauto/shell/ext/verify/ResultVerifier.java) 用当前证据独立判断结果与人工边界。模型角色通过 [ModelConfig](../android-shell/src/main/java/com/silentauto/shell/ext/config/ModelConfig.java) 分开配置。备忘录另有 [DocumentProof](../android-shell/src/main/java/com/silentauto/shell/ext/verify/DocumentProof.java) 的完整正文/保存与重开核对路径，因此某些 VERIFIED 样本 verifier 调用为 0，不能称为“另一模型视觉验收”。

查询使用 [ReadOnlyGuard](../android-shell/src/main/java/com/silentauto/shell/ext/guard/ReadOnlyGuard.java) 限制交易、发送等写动作；候选答案与连续原文引文由 [AnswerVerifier](../android-shell/src/main/java/com/silentauto/shell/ext/query/AnswerVerifier.java) 及独立证据提取核对。答案上限 60 个 Unicode 码点。`NOT_FOUND` 只表示本次搜索范围；只读门禁不是对任意页面的形式化安全证明。

[HandoffManager](../android-shell/src/main/java/com/silentauto/shell/ext/handoff/HandoffManager.java) 处理登录、验证码、支付等本人边界；[ManualTaskTransfer](../android-shell/src/main/java/com/silentauto/shell/ext/handoff/ManualTaskTransfer.java) 与 [TaskDisplayTransfer](../android-shell/src/main/java/com/silentauto/shell/ext/handoff/TaskDisplayTransfer.java) 移动原任务；[PageIdentity](../android-shell/src/main/java/com/silentauto/shell/ext/handoff/PageIdentity.java) 校验页面身份；[ResultRetention](../android-shell/src/main/java/com/silentauto/shell/ext/handoff/ResultRetention.java) 保留可交付结果。

维护时要检查转移后的实际 display/root task、原页面身份及回退结果，再决定是否释放虚拟屏。响应中的 `moved` 与 `previewAvailable` 表示不同事实；不能提前关闭仍供预览/重试的资源。进程强杀后无法恢复相同活页面。

## 7. 技能学习、复用与救援

[TrajectoryRecorder](../android-shell/src/main/java/com/silentauto/shell/ext/skill/TrajectoryRecorder.java) 记录动作及前后页面；[SkillCompiler](../android-shell/src/main/java/com/silentauto/shell/ext/skill/SkillCompiler.java) 只将符合条件的成功轨迹编译为参数化技能；含人工交接、失败或目标追加等轨迹不作为普通成功学习样本。

[SkillMatcher](../android-shell/src/main/java/com/silentauto/shell/ext/skill/SkillMatcher.java) 选择 active、类型相符且覆盖目标的技能，并校验本次参数。[SkillRunner](../android-shell/src/main/java/com/silentauto/shell/ext/skill/SkillRunner.java) 用当前语义目标定位；旧 bounds 仅作参考，不是坐标回放。[ScreenFingerprint](../android-shell/src/main/java/com/silentauto/shell/ext/skill/ScreenFingerprint.java) 排除时钟/相对时间、隐藏节点和列表条目等易变信息，以稳定控件描述页面。

偏离时最多 3 个救援动作，救援用于恢复流程，不能直接跳过最终验收。仍不能继续则弃用本次技能，回到模型；连续弃用 3 次将技能标为 suspect。[SkillStore](../android-shell/src/main/java/com/silentauto/shell/ext/skill/SkillStore.java) 管理启停、删除、统计与落盘；用户禁用不应被普通学习覆盖。

查询技能保存重新查找的方法，每次重新读答案，不复用旧答案。技能表现同时比较成功率、调用数、耗时和救援/回退比例；当前三个备忘录样本只证明路径可运行。[实测报告](evidence/skills/report.md) 中的脱敏技能副本不是可导入资产。

## 8. 语音、主屏上下文与追加

控制端 [FloatingBallService](../controller-app/src/main/java/com/silentauto/controller/ext/overlay/FloatingBallService.java) / `WavRecorder` 实现按住录音、就绪震动、松手提交和取消。shell [VoiceIntake](../android-shell/src/main/java/com/silentauto/shell/ext/voice/VoiceIntake.java) 理解语音，[VoiceRouting](../android-shell/src/main/java/com/silentauto/shell/ext/voice/VoiceRouting.java) 区分新任务与追加；有多个可能目标时要求选择。

[ScreenContextReader](../android-shell/src/main/java/com/silentauto/shell/ext/context/ScreenContextReader.java) 在录音前读取主屏上下文，[ScreenPrivacy](../controller-app/src/main/java/com/silentauto/controller/ext/context/ScreenPrivacy.java) 参与隐私处理。上下文有开关、丢弃接口与 120 秒有效期。密码节点遮蔽和短时保留不能替代读取页面及发送模型的授权；页面包含的信息可能比用户口述更多。

[AmendmentQueue](../android-shell/src/main/java/com/silentauto/shell/ext/amend/AmendmentQueue.java) 保留原目标、modify/add/cancel 记录和有效目标；循环在受控点消费，目标版本变化后丢弃旧规划并退出旧技能。测试必须区分真实麦克风/intake 路径与直接 RPC 注入，后者不能证明语音链路。

## 9. RPC、配置与数据存储

默认 `127.0.0.1:43110`，TCP 逐行 JSON-RPC。控制端必须按请求 id 关联响应，并处理异步状态/日志/视频事件；不是 HTTP 服务。状态事件使用 `event="status"`，任务 `type` 保留 action/query。方法和参数以 `RpcServer` 分发为准，完整操作示例在使用手册。

| 接口族 | 典型方法 |
| --- | --- |
| 健康与任务 | `ping`、`status`、`startTask`、`stopTask`、`listTasks`、`getTask` |
| 语音/上下文/追加 | `submitVoiceTask`、`confirmTask`、`resolveVoice`、`captureContext`、`discardContext`、`amendTask` |
| 人工与结果交接 | `takeManualOnMain`、`resumeTask`、`takeResultOnMain`、`injectTouch` |
| 历史与技能 | `listHistory`、`listSkills`、`setSkillStatus`、`deleteSkill` |

`RpcConnection` 防回环自连接；`RpcClient` 防止旧读线程清理新 socket，修改请求写入失败不自动重放。`OverlayRpc` 处理悬浮层重连和请求关联。服务端客户端发送队列容量 64，慢客户端断开，避免全体任务被阻塞。

控制端三角色配置保存在私有 `ai_config` SharedPreferences，未实现密钥保险库。shell 的技能、轨迹、历史、截图和上下文位于 `/data/local/tmp/shadowauto/` 下对应子目录，属于 shell 可访问数据，控制端重装不代表清空这些数据。模型请求可能包含控件文本、截图、音频或上下文，按角色和实际路径发送。

当前 RPC 无应用级鉴权；回环监听不隔离同机其他进程。后续方案是随机 token，并同步处理控制端传递、部署、轮换及旧连接行为，尚未实现。[ModelConnectionProbe](../controller-app/src/main/java/com/silentauto/controller/ext/config/ModelConnectionProbe.java) 已显示服务返回的 model；现有三角色实测返回别名，不能据此证明精确权重快照。

## 10. 开发验证与证据维护

在仓库根目录构建与检查：

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug :android-shell:lintDebug :controller-app:lintDebug
./scripts/test-interaction-regressions.sh
```

先完成 Android 构建，回归脚本复用已解析的 Gson 缓存与 debug classes。目前包含八组 Java 检查：连接、UI 恢复、交互保护、任务转移、查询、RPC 输出、锁屏保护、重复检测；另外运行 isolation-report JS 测试与录制脚本 Python 测试。主机检查不能证明设备接受隐藏 API。设备探针、真实模型任务和中文输入并行测试需要单独进行。Web 修改在 `web-launcher` 执行 `npm run build`；纯文档修改只需检查链接和内容一致性。

Step6 的 Android 构建、两模块 Lint 和当时回归通过，见 [build-checks.json](evidence/build-checks.json)；该 JSON 不覆盖新增锁屏代码。锁屏增量的[验证报告](evidence/screen-lock/report.md)另记两模块构建/Lint、最终补充后的 shell 构建/Lint及完整回归通过。[ScreenLockRegression](../scripts/tests/ScreenLockRegression.java) 覆盖输入即时拦截、模拟一小时锁屏保留、解锁恢复、真实同 App 冲突超时和锁屏取消。模拟一小时不代表一小时真机耐久测试。本次仅核对代码、报告与文档链接，未重跑构建或设备验收。

[证据索引](evidence/README.md) 组织脱敏报告与样本，[provenance.json](evidence/provenance.json) 记录来源和公开产物哈希。原始资料留在仓库同级工作区 `../docs/validation/`，不默认进入 Git；原片可能包含个人信息。公开前检查截图/视频画面与 JSON，保留失败样本、未知指标与采样缺口，不能补造缺失录像。

录像工具为 `scripts/record-demo.sh`；成片由 `scripts/build-step6-demo.py`、编辑计划和来源原片重建，具体命令见[视频索引](evidence/demo-path/VIDEO.md)。`scripts/verify-step6-video.m` 做逐帧 OCR 规则检查，另配解码和画面抽查；无规则命中不能当作完整隐私保证。

## 11. 后续工作与变更记录规则

| 优先级 | 工作 | 完成判据 |
| --- | --- | --- |
| P1 | 微信启动兼容与消息路径 | 定位退出原因，专用测试对象真实发送，记录结果；仍失败则保留设备限制 |
| P1 | 完整店铺上下文 | 公开详情页同时包含店名和完整地址，来源→intake→目标正文逐项核对 |
| P1 | 咖啡成功流程连续双屏录像 | 同一任务覆盖真语音追加、规格确认、支付前暂停与原页面交付，无付款操作 |
| P1 | 隔离复验 | 修正后中文拼音候选词与滚动三轮重跑，联合原始日志、双屏录像和机主反馈；仍缺系统能力则保留实验标记 |
| P2 | RPC token 鉴权 | 未授权请求被拒，控制端正常重连，部署与轮换路径可回归 |
| P2 | 技能稳定性与性能 | 扩大同类任务样本，降低救援/模型回退；同时报告耗时与调用数 |
| P2 | 锁屏保护扩展验收 | 覆盖模型请求中途锁屏、解锁后同 App 冲突、长时待机、人工等待状态与其他设备；保留厂商页面销毁限制 |
| P2 | 跨设备与长期稳定性 | 明确设备/系统矩阵，覆盖长时间任务、退出、重连、资源回收 |

修改运行代码时，同步更新本文受影响的方案与状态；接口/状态字段变化同时更新控制端和回归；新增实测写日期、设备、代码基线、原始结果及限制，再链接到证据。README 只维护面向用户的入口、配置和操作，阶段细节进入状态/证据文档。功能计划在验收前始终标记为待实现或待验证。

| 日期 | 开发文档变更 |
| --- | --- |
| 2026-10-25 | 对照工作区锁屏增量补齐能力表、状态/输入/异常恢复方案、实际计时语义、八组回归清单及真机范围；区分 Step6 与锁屏验证基线 |
| 2026-10-25 | 建立本文件，整理截至 Step6 的实现、模块边界、技术约束、验证状态和后续判据；未改运行代码 |

| 2026-10-26 | README 改为标准项目首页，详细操作手册迁至 `USER_GUIDE.zh-CN.md` / `USER_GUIDE.en.md`，开发文档继续维护技术方案与验证状态 |

| 2026-10-26 | 项目工作名改为隐航 QuietPilot；保留包名、RPC 与设备数据兼容；生成部署资源不再纳入当前源码树，补齐贡献/安全/发布说明 |
