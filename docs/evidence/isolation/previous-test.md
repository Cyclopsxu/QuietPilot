# Mate 40 Pro 事前防抢焦点排查与修复

日期：2026-10-23。设备：NOH-AN01 / HarmonyOS 4.2.0.192 / SDK31。

## 结论

找到可在这台手机上生效的事前措施：后台注入的触摸增加 `MotionEvent.FLAG_NO_FOCUS_CHANGE`，其实际值为64。统一输入桥此前仅设置 displayId，没有设置该标志；指定屏幕并不等于不改变全局焦点。

修改后，高德三轮真实模型路线查询均 ANSWERED，备忘录后台搜索/打开及结果交接 VERIFIED；都没有触发后台焦点安全暂停。三轮高德均记录前台切换0、焦点丢失0、输入法被抢0、剪贴板变化0。第三轮包含用户实际中文输入及滚动，用户确认候选词与滚动正常。

**这证明已验证路径不必依赖“抢焦点后暂停”才能运行。并不证明系统获得原生独立焦点能力，也不认证所有App、任意输入法或长期运行全程无打扰。** 第三轮前台夹具存在3条无焦点采样与8条IME不活跃记录，不能改写成严格零失焦；详见下文。

此前把可行性过早归结为系统缺少独立焦点能力，判断不完整：高级虚拟屏能力确实缺失，但触摸层还有可用的防抢焦点机制。

## 系统事实与排查

- 全局资源 config_perDisplayFocusEnabled=false；OWN_FOCUS 与 STEAL_TOP_FOCUS_DISABLED 字段缺失；shell没有 ADD_TRUSTED_DISPLAY 权限，IME本地策略读回fallback。这些事实未改变。
- 手机实际存在 MotionEvent.FLAG_NO_FOCUS_CHANGE=64。其语义是触摸未聚焦窗口时不改变焦点，包括ACTION_DOWN；已在手机对照验证，而非仅凭字段存在推断有效。
- 系统存在 ActivityOptions.setAvoidMoveToFront 和 WindowContainerTransaction.setFocusable，以及任务管理权限。没有修改系统资源、桌面模式或ROM，没有root。

官方机制参考：[AOSP 每屏焦点说明](https://source.android.com/docs/core/display/multi_display/displays)、[AOSP MotionEvent 不改变焦点标志](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/MotionEvent.java)、[Android12 ActivityOptions](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android12-release/core/java/android/app/ActivityOptions.java)。字段在该手机是否可用以 phone-capabilities.log 为准。

## 独立对照实验

仅使用受控计算器，不操作私人数据，不安装替代高德。

| 组合 | 结果 |
| --- | --- |
| 普通启动 + 普通触摸 | 计算器收到点击，全局焦点从0转到虚拟屏70 |
| 普通启动 + flag64触摸 | 计算器收到点击、公式改变，全局焦点保持0 |
| 避免前置启动 + flag64触摸 | 点击仍生效，焦点保持0；生产未接入此启动变更，因为触摸修复已可单独生效 |
| 启动前创建不可聚焦根容器 | 容器成功设为不可聚焦，但计算器未呈现可操作控件，方案未采用。探针已释放自身虚拟屏，没有调整主屏任务 |

touch-probe-results.json保存前三组实际数据。第四组在touch-probe.log、ui-container.json保留，不当作通过。早期探针在Instrumentation/调用包身份及控件标签识别上失败，随后校正；不是产品兼容性通过证据。

## 生产改动

- InputBridge在运行时读取实际FLAG_NO_FOCUS_CHANGE，仅对支持它的displayId>0触摸设置，覆盖点击、预览触摸、长按、拖动和滚动的DOWN/MOVE/UP/CANCEL。
- 主屏及不支持该标志的系统保留原事件构造；不猜测隐藏常量值。
- VirtualDisplaySession记录touchNoFocusChangeAvailable及实际标志值。
- 默认严格隔离门禁、兼容模式显式选择、运行时焦点保护保留，不把触摸能力自动包装为原生隔离或全程认证。
- 真机TouchFlagsProbe验证生产类生成的主屏/后台事件标志、坐标、动作和设备ID；仓库保留scripts/tests/TouchFlagsDeviceRegression.java。

## 真机结果

| 任务 | 结果 | 主屏与干扰指标 |
| --- | --- | --- |
| preventive-touch-amap，第9步 | 75公里、1小时45分，过路费未显示 | 桌面保持；前台/焦点/IME/剪贴板均0；未触发安全暂停 |
| preventive-touch-memo，第4步 | 原笔记后台搜索、输入、打开并验收；明确RPC查看后转主屏，正文SHA256不变，虚拟屏释放 | 后台阶段全局焦点0；最终原始切换1次，明确交接关联1次，焦点丢失0，无保护暂停 |
| preventive-touch-amap-repeat，第13步 | 75公里、1小时44分，过路费未显示 | 前台为保留的备忘录；四项均0，无保护暂停。模型自行纠正起终点选择，不能把此轮写成最短路径执行 |
| preventive-touch-amap-ime，第11步 | 方案1为75公里、1小时45分；方案2为88公里、2小时，过路费未显示 | 主屏为空白输入测试App；四项均0，无保护暂停；用户真实输入和滚动参与 |

这些是测试时页面数据，不证明未来实时路况；没有启动导航、打车、订单或预订。未改写旅行备忘录正文。

## 人工输入/滚动并行的证据和限制

用户明确授权安装独立包 com.silentauto.isolationfixture，并回答“已开始输入，候选词和滚动正常”。它没有网络/发送权限，只记录长度、组合区、焦点、滚动位置，未保存输入正文。测试后已卸载。

在任务实际运行区间：1961条记录、8次文本变更、1744次滚动回调（回调数量不等于手势次数）。第一条真实编辑发生在任务开始约54秒后，覆盖后续选择、滚动及答案提取阶段；主屏输入框和IME在后台起终点输入前就已激活。

- 系统采样始终保持TopFocusedDisplay=0、前台为测试页；后台安全保护未触发。
- 测试页仍有3条windowFocused=false采样和8条imeActive=false记录，约在1792752468045–1792752470311ms；同时测试页窗口句柄变化。对应系统快照仍为主屏测试页。**用户随后确认“有转屏或重新打开”。该反馈与窗口句柄变化一致，支持人工界面变化的解释，不作为已证实的高德抢焦点；原始异常仍保留。严格全采样有焦点断言未通过。**
- composing span始终未出现，不能由程序独立证明拼音候选词始终保留；候选词状态依赖用户视觉确认。
- 本轮没有双画面连续录像。系统input_focus事件缓冲未返回本轮记录，不能把无记录当作事件级零失焦证明；周期采样仍可能遗漏极短变化。
- 测试页进程掉帧12/1336，约0.90%，该项达阈值；只代表本次输入/滚动测试页。首次桌面轮28/128不达标，备忘录前台轮无新增帧不可评估，均保留原指标。

## 验证、部署、收尾

final-build.log构建/Lint通过；final-host.log四组回归通过；production-touch-flags.log为真机事件构造检查。最终版本已部署，后台保持本轮明确授权的兼容实验模式，严格默认和焦点安全保护未删除。

两个收尾脚本曾因只等待10秒左右而在异步finalized尚未生成时断言失败；后续读取最终状态核对。并行严格全聚焦断言也失败，异常数据完整保留。测试页清理前已保存覆盖完整任务区间的元数据，清理后由保存数据过滤运行窗口，不冒充清理后重新取到的日志。

主要证据：
- phone-capabilities.log / phone-organizer-capabilities.log：实际系统接口和权限。
- touch-probe-results.json / touch-probe.log：独立因果对照。
- preventive-touch-*-result.json / *-focus.json：真实任务与周期焦点数据。
- preventive-touch-memo-check.json：同页交接、正文及释放。
- human-parallel-check.json / human-input-overlap.jsonl / human-window-anomalies.json：人工输入事实与异常。
- final-deployment.json：最终APK摘要、模式与连接。

## 用户补充确认

用户随后明确回答：测试期间“有转屏或重新打开”。这解释了前台窗口句柄变化及短暂无焦点记录的可能来源。结合系统采样仍在主屏、后台保护未触发，当前没有证据将这3条采样认定为高德抢焦点。没有准确操作时间戳，不声称逐条完成因果证明，也不把原始异常或严格断言改写为通过。候选词仍依据用户视觉反馈，组成区未被夹具捕获的限制保持。
