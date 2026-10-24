# 技能三段真机验收

2026-10-24，Mate 40 Pro / HarmonyOS 4.2 / SDK31。微信两次启动后自行退出，改用**华为备忘录新建、保存并重新打开专用测试笔记**；不是微信发消息通过。测试前已备份技能集合，原无备忘录技能；修正指纹后删除的也仅是本轮生成的测试技能。

| 阶段 | 步数 | model / skill / rescue | executor / verifier / intake 调用 | 执行耗时 | 结果 |
| --- | ---: | --- | --- | ---: | --- |
| [首次探索](skill-explore-v2.json) | 8 | 8 / 0 / 0 | 10 / 0 / 0 | 27.661秒 | VERIFIED，实际生成6动作技能 |
| [换参数复用](skill-reuse-v2.json) | 10 | 2 / 5 / 3 | 8 / 0 / 0 | 49.444秒 | VERIFIED，命中技能，末段救援后回退模型 |
| [人为偏离救援](skill-rescue-v2.json) | 13 | 6 / 3 / 4 | 14 / 0 / 0 | 63.328秒 | VERIFIED，出现skill→rescue→skill；后续再次偏离，由模型完成 |

参数为新笔记标题、完整正文和正文显示字数，分别换为测试1/2/3；实际技能元数据见 [脱敏副本](learned-skill-redacted.json)。副本的指纹文字已匿名化，**不能用于生产导入或回放**。成功由确定性的DocumentProof核验标题、完整正文、返回列表及重新打开序列，因此本组三轮verifier模型调用为0，不冒充另一家视觉模型验收。

复用降低了本次模型调用（10→8），但步骤和耗时增加；不能宣称已经提速或全程脱离模型。耗时取任务elapsedMs，结束于VERIFIED，不包含其后的技能编译/归档收尾；调用数按最终归档，首次包含技能编译调用。保存后的列表项缺少唯一语义特征，仍可能产生救援和回退，这是本轮保留的限制。

救援注入见 [记录](rescue-fault.json)：观察第一技能动作打开编辑器后，在**同一虚拟屏138**启动备忘录列表入口；命令与第2步存在并发，不能说执行线程已暂停。系统返回intent交给已有Activity；第3步指纹实测已回到列表，救援点击新建后第4步重新进入编辑器并由skill继续。来源序列为 `skill, skill, rescue, skill, rescue, rescue, rescue, model…`。后续重新补写并保存、重开后才VERIFIED，没有把中途草稿视为成功。

修复与失败保留：

- [最初探索](skill-first.json)：10步VERIFIED，但编译返回空对象，未学到技能。澄清target.bounds仅为参考、预处理可能省略无界面变化的focus_input后，[复测](skill-first-fixed.json)实际学习成功。
- [旧指纹复用](skill-reuse.json)：15步VERIFIED，仅1步skill；时钟“今天17:05→17:10”引发误判。加入时钟/相对时间过滤，并用稳定控件排除集合项、隐藏节点。
- [中间复用](skill-reuse-fixed.json)：仍产生救援回退；[旧救援尝试](skill-rescue.json)最终FAILED，未捕获计划注入窗口，不算人为偏离成功。
- 计算器曾有7步ANSWERED156，但技能编译拒绝未使用的参数b；另一次16步重复后失败，未把计算成功说成技能已学习。

完整数值见 [comparison.json](comparison.json)。本轮三段有真机数据，未使用mock冒充；仍不扩展结论到跨App技能、微信、分支流程或跨重启恢复。
