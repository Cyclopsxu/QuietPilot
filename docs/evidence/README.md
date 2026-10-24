# 可复核证据索引

公开证据来自工作区原始验证记录的脱敏副本；来源相对路径、原件与脱敏件 SHA256 见 [provenance.json](provenance.json)。原始报告中没有同步入库的日志/附件名称只说明原始记录位置，不表示这些附件已公开。成功、失败、未验证分别列出，不用模拟结果替代真机。

| 日期 | 设备 | 任务 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| 2026-10-24 | Mate 40 Pro / SDK31 | 三类页面反复抓图/读控件 | 60/60成功；无原屏切换 | [摘要](capture-stability/report.md)、[计算器](capture-stability/calculator-cycles.json)、[高德](capture-stability/amap-cycles.json)、[美团当前页](capture-stability/meituan-cycles.json) |
| 2026-10-24 | 同上 | 先清除再计算（Step6重测） | ANSWERED 156，7步；另一次16步失败保留 | [重测JSON](query/step6-calculator.json)、[失败JSON](query/step6-calculator-repeat-failed.json) |
| 2026-10-24 | 同上 | 普通计算12×13 | ANSWERED 156，6步 | [任务JSON](query/calculator-simple-final.json)、[截图](query/calculator-simple-source.png) |
| 2026-10-24 | 同上 | 先清除再计算（旧版本） | FAILED，25步重复清除 | [失败JSON](query/calculator-short-final.json) |
| 2026-10-24 | 同上 | 高德公共地点地址 | ANSWERED，4步；仅搜索结果页 | [脱敏JSON](query/amap-short-final.json) |
| 2026-10-23 | 同上 | 备忘录同页交接 | VERIFIED；正文哈希一致，原任务转主屏 | [交接记录](memo/handoff-check.json)、[DocumentProof任务](memo/document-proof-task.json)、[失败样本](memo/document-proof-failed.json) |
| 2026-10-23 | 同上 | 三次后台高德、其中一次有人输入/滚动 | 任务观测四项0；夹具仍有短暂无焦点/IME不活跃 | [历史完整报告](isolation/previous-test.md)、[异常样本](isolation/human-window-anomalies.json) |
| 2026-10-24 | 同上 | Step6三轮真实拼音输入 | 三轮采样四项0、机主反馈正常；录像/采样间隔及文字来源仍有限制 | [报告](isolation/report.md) |
| 2026-10-24 | 同上 | 微信/上下文/查询/追加指定演示路径 | 待完整验收 | [状态](demo-path/README.md) |
| 2026-10-24 | 同上 | 技能首次/复用/救援 | 待完整真机验收 | [表格](skills/report.md) |

脱敏策略：删除 API Key/Authorization/token、截图内嵌Base64、个人电脑路径、手机号与门牌以下地址；剪贴板原摘要不随公开副本发布；本轮时间序列改为文件内一致的匿名编号，保留变化关系。只纳入测试目标或公开地点材料，没有导入私人聊天和联系人列表。计算器截图已目视确认只有公式/结果；其他截图未完成隐私检查，不入库。所有原文替换均可能使公开引文无法逐字复算，原始副本留在机主本地；脱敏件不冒充未修改原件。

模型服务说明仅对应文档核查日，不替代实际响应模型字段。录制原片留在仓库外，确认时间轴、逐帧脱敏后才发布链接。完整完成状态见 [Step6清单](../STEP6-STATUS.md)。
