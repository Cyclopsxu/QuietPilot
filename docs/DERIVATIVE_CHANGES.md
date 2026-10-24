# 二次开发出处与改动说明 / Derivative Changes

更新日期：2026-10-23。

## 原项目出处

本分支基于 **[android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto)** 二次开发。原项目及原作者、贡献者的署名予以保留；本分支的新增功能和修复不代表上游官方版本或上游背书。

- 上游许可证：[Apache License 2.0](../LICENSE)。本分支继续采用该许可证，未改写原许可证正文。
- 二次开发基线：[`611e5eb0e1c94befda3c17c97438912dfd30e8a8`](https://github.com/android-notes/ShadowAuto/commit/611e5eb0e1c94befda3c17c97438912dfd30e8a8)，提交说明为 `Fix shell main looper and fatal logging`。
- 本地二次开发首个提交：`09a0af2`，随后逐步实现执行扩展、验收交接、技能与语音等功能。
- 随源码分发的出处摘要见 [NOTICE](../NOTICE)。所用上游基线没有独立 NOTICE 文件；本分支新增的 NOTICE 是二次开发说明。

## 沿用的上游能力

以下基础架构来自 ShadowAuto 原项目，不能作为本分支独立原创功能声明：

- 通过 ADB、`app_process` 启动 shell 自动化进程。
- 在 VirtualDisplay 中启动真实 App，并通过模型工具调用操作 UI。
- UiAutomation 节点读取、Paddle Lite OCR、指定显示屏的输入注入。
- H.264 虚拟投屏、Android 控制端、浏览器 WebUSB 启动器及其原始部署流程。
- 仓库已有的技术文章与 `shadowauto.mp4` 演示素材。

## 本分支的主要新增与修改

| 范围 | 相对上游基线的改动 | 主要位置 |
| --- | --- | --- |
| 执行扩展 | 从主循环提取执行上下文、动作执行器、步骤监听与来源扩展；复用上游动作逻辑并继续扩展 | `android-shell/.../ext/`、`AutomationEngine.java` |
| 验收与人工交接 | 独立视觉验收、任务状态、证据截图、验证码等边界等待、触摸预览、原任务临时转主屏人工处理与交回后台继续，人工期间暂停自动恢复 | `ext/verify/`、`ext/handoff/`、控制端任务视图与 RPC |
| 异常路径复查（2026-10-23） | 人工转移失败后重新确认位置并保留归属，位置未知不自动恢复；主屏人工期间暂停等待超时；控制端前台恢复刷新、旧 socket 隔离清理与连接超时，不自动重放操作。构建/Lint及模拟器回归通过，相关后台修复已部署实体手机，完整美团验收仍对应此前版本 | `ManualTaskTransfer`、`HandoffManager`、`AutomationEngine`、`MainActivity`、`RpcClient` |
| 隔离能力与指标归因（2026-10-23） | 记录实际 flags/候选回退/IME 回读，能力不足默认阻止后台，显式兼容实验标为未验证；保留原始切换，区分交接/触摸关联及未归因；掉帧注明采样 App，合成并行双画面测试不替代真机中文候选词验收 | `DisplayIsolation`、`VirtualDisplaySession`、`DisturbanceMonitor`、`DisturbanceCounters`、任务历史 |
| 高德交互与焦点安全（2026-10-23） | 起终点只读输入限定、规划控件身份复核与触摸、拒绝导航/打车；长文档原文锚点；发现全局焦点干扰锁定暂停，禁止自动重启丢失页面；预览返回入口与幂等取消。真机备忘录同页转移通过，Mate 40 Pro 隔离不足仍默认阻止后台，不宣称零干扰 | `ObservedTarget`、`ReadOnlyGuard`、`PageIdentity`、`BackgroundFocusGuard`、执行循环与控制端 |
| RPC慢连接隔离（2026-10-23） | 每连接独立有界输出队列，慢读者断开而不阻塞任务日志/状态/视频发布；真实socket回归通过。真机重试主屏交接和停止恢复；后台全局焦点隔离仍不足，转移后回退首次证据未确认仍保留限制 | `RpcServer.Client`、`RpcOutputRegression` |
| 事前触摸焦点保护（2026-10-23） | 按手机实际支持的FLAG_NO_FOCUS_CHANGE生成后台触摸，主屏与不支持设备保留原行为，记录运行时能力；三轮高德与备忘录后台打开未触发暂停。真实中文输入反馈正常，但前台夹具短暂无焦点/IME不活跃记录及候选词机器核验仍有证据限制，不认证全程零干扰 | `InputBridge`、`VirtualDisplaySession`、`TouchFlagsDeviceRegression` |
| 调度与输入保护 | 两任务并发上限、同 App 排队、前台冲突暂停/恢复、剪贴板保护及任务收尾隔离 | `ext/guard/`、`ClipboardBridge.java` |
| 技能学习 | 轨迹录制、参数化技能编译与存储、语义控件匹配、重放、有限救援与 suspect 状态 | `ext/skill/` |
| 执行中追加 | 线程安全追加队列、有效目标及旧规划失效、语音归属选择、暂停/排队/交接恢复、追加历史与技能编译排除 | `ext/amend/`、`ext/voice/VoiceRouting.java`、执行循环与胶囊 |
| 真机校准 | 修复查询规则未进入请求消息、验收原文被改写、遗漏“不需要保留”否定偏好；Mate 40 Pro 真实模型基础查询/追加/取消通过，新增有界重复视口恢复与截图/OCR 原文验证，增加系统版本字段检查，拒绝内核信息和不完整主版本；完整系统版本查询已在真机连续三轮通过；办事结果默认保留等待用户交接，最新版已通过计算器真实入口交接、真实转移后故障回退及同任务重试，释放虚拟屏后结果仍在 | 查询提示、`AnswerVerifier`、`VersionEvidence`、`ResultRetention` |
| 查询带回（5.8） | 只读动作保护、独立答案提取与原文核对、查询技能、胶囊答案与本机历史；用户主动复制或打开来源 App | `ext/query/`、`ReadOnlyGuard.java`、验收与胶囊模块 |
| 语音与上下文 | executor/verifier/intake 分角色配置、音频理解与确认、临时屏幕上下文、隐私开关与密码区域遮盖 | `ext/config/`、`ext/context/`、`ext/voice/`、模型配置页 |
| 悬浮入口 | 语音悬浮球、状态胶囊、录音就绪震动、上滑取消、曲面屏边距与交接入口 | `controller-app/.../ext/overlay/`、`AndroidManifest.xml` |
| 历史与观测 | 任务归档、技能管理、调用/耗时/动作来源统计，以及前台、焦点、输入法、剪贴板、掉帧观测 | `ext/history/`、`ext/metrics/`、控制端历史页 |
| 真机兼容与结果交付 | 华为 AVC 参数回退、音频 Data URI、视觉模型 JSON 解析、弹窗/截图竞争修复、结果保留、原任务转主屏、实体校验及失败回退 | `VideoStreamer.java`、`ScreenCapture.java`、虚拟屏与交接模块 |
| 文档与回归 | 功能和验证状态说明、修改声明、基础 mock 测试适配；工作区另保存阶段任务和真机证据 | 两份 README、`scripts/`，以及仓库外的 `../docs/` |

`ext/ActionExecutor.java` 的初始实现提取自上游 `AutomationEngine.java` 的动作执行逻辑，保留其来源说明；新文件路径不意味着其中复用的上游逻辑是独立原创。

可用以下命令复核已提交的完整差异，后续提交继续进入该记录：

```sh
git diff --stat 611e5eb0e1c94befda3c17c97438912dfd30e8a8 HEAD
git diff 611e5eb0e1c94befda3c17c97438912dfd30e8a8 HEAD
git log --oneline 611e5eb0e1c94befda3c17c97438912dfd30e8a8..HEAD
```

## 许可证与修改标记

依据 [Apache-2.0 第 4 条](https://www.apache.org/licenses/LICENSE-2.0#redistribution)，分发时附带许可证，保留适用的原始版权与署名声明，并在修改文件中标明修改。README 的二次开发声明和已修改源码中的 `Modified by ... Original: ShadowAuto (Apache-2.0)` 注释用于说明本分支修改；这些注释不会替代原始版权归属。

原始 LICENSE 保持不变，现有署名继续保留。第三方依赖及其素材仍遵循各自的许可；本说明不重新授予其权利。上游网站、演示和技术文章的链接标为上游内容，不能据此推断它们已经包含本分支的改动。

## 验证与分发范围

功能实现和测试状态以 [中文 README](../README.zh-CN.md) / [English README](../README.md) 为准。上一版已有美团真机结果交付记录；最新五项交接修复 `d1df15e` 通过构建、Lint 与本地边界回归，后续 `7e9d205` 已用计算器验证真实入口、转移后故障回退和重试成功；后续真实美团选店已通过主屏人工确认往返、最终完成提示及“去查看”后同页保留；重跑未再触发验证码。

工作区同级 `../docs/` 的阶段资料不在本 Git 仓库内。此文件和根目录 NOTICE 随本仓库分发，单独克隆时也能查到出处与改动摘要。

## English Summary

This is an independently modified distribution of [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto), based on commit `611e5eb0e1c94befda3c17c97438912dfd30e8a8`. The original Apache-2.0 LICENSE and upstream attribution are retained. This branch continues under Apache-2.0.

Upstream provides the shell/VirtualDisplay automation architecture, UI/OCR and input bridges, streaming, controller, launcher, original articles, and demo. Downstream work adds execution extensions, independent verification and handoff, scheduling and input protection, parameterized skills and recovery, multimodal voice/context entry, floating controls, history and metrics, Huawei fixes, and retained-result delivery. The initial extracted action executor reuses upstream action logic.

See [NOTICE](../NOTICE), the change table above, and Git history for provenance and scope. Modified upstream source files carry modification notices. This derivative is not presented as an official upstream release or as independently authoring the inherited architecture. Verification limits are recorded in both READMEs.

- 2026-10-23 川西重测：兼容虚拟屏原生键盘注入前拦截，Enter/Search 优先可见提交控件；保留高德ANR、引用失败及焦点暂停记录。补齐模型工具观察多轮对话的流式 reasoning_content 回传（不写任务日志），实际部署/验收见本轮验证记录。

- 同轮轨迹发布改为同目录原子替换，修复历史读取半份JSON；手机并发读写检查通过。川西4段路线及新备忘录重开/同页交接有实证，2段与美团商家仍待核实；高德ANR、空控件及写入长文验收失败均保留。

- 2026-10-24 续测：新增根子树缺失诊断及有限无障碍重连、仅查询一次重启与保留页面交接；执行前重新绑定控件、重连后丢弃旧动作；新增预订/订座/预约查询拦截。华为长文按完整标题/正文、真实列表与重开序列确定性验收，兼容卡片标题截短；六段路线补齐，真实去查看同一任务229由109转0、全文一致且次屏释放。美团商家仍受本人验证阻塞，不能算全流程通过。控制端新增避开目标端口的源端口绑定，修复实测RPC自连接；构建与真实socket回归通过，控制端此项尚未覆盖安装。

- 2026-10-24 后续排查：为美团MSC公开城市导航和地点输入、高德匿名搜索表单增加严格上下文识别，继续拒绝私人地址、交易及密码字段；查询不再自动推断总目标词灌入当前字段。构建/Lint与六组回归通过；固定Surface诊断探针可选康定并读公开页面，但餐饮返回B3001_1401，酒店新验证等待本人，生产抓图稳定性及商家核验均未通过。未把诊断对照或未验收候选作为完成证据。

- 2026-10-24 抓图稳定性：新增CaptureMirror，从已拥有的次屏图层栈生成独立非安全合成输出；原VirtualDisplay保持原Surface，读帧持续排空并保留最新帧，异常不切回原屏。新增模式/失败/切换诊断与三轮资源故障设备回归。控制端连接修复已覆盖安装；60轮抓图读取和两个短查询通过，首轮计算模型重复清除失败仍保留。美团完整商家、其他Android版本与全程无打扰不在通过结论内。
