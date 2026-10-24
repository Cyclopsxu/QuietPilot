# 指定演示路径验收

2026-10-24。真实结果与代码能力分开记录。

| 检查项 | 实测结果 | 缺口 |
| --- | --- | --- |
| 微信虚拟屏与文件传输助手发测试1 | 第1步控件持续不可读，WAITING_USER，随后测试取消；未发出消息 | [等待归档](wechat-waiting.json)、[控件诊断](wechat-ui.json)显示rawWindowCount=0，13秒录制抽帧为黑屏。安装结束后第二次仍自行退出，虚拟屏rootTasks=0；[重试等待归档](wechat-retry-waiting.json)和[系统退出摘要](wechat-exit-summary.json)保留EXIT_SELF及iAware CrashClean。未取得具体堆栈，不认定所有设备永久不兼容 |
| 大众点评边看边办 | 尚未验证 | 公开店铺页、语音任务理解和消息真实发出；微信路径未走通 |
| 淘宝物流/微信历史地址查询 | 尚未验证 | 已安装淘宝；需要只读、可核验原文，未伪造查询结果 |
| 瑞幸/美团追加大杯与支付前交接 | 尚未验证 | 未安装瑞幸，按指定替代方案可选已安装美团；规格、语音及边界未通过完整实测 |
| 双路scrcpy | 主屏和虚拟屏录像已生成，参数为scrcpy5.0.1、no-control/no-audio、独立forward端口 | 三轮中两轮控件不可读并重启/等待人工，不能宣称双路连接无影响；首轮因UTF-8截断中止，已修复；销毁后scrcpy冻结改为显示屏存活轮询结束，CLI回归通过，真机显示屏130销毁后已由监控结束对应scrcpy，见 [退出核验](recorder-teardown-check.json) |
| 三角色模型连接 | 实际已安装控制端APK的ModelConnectionProbe均成功 | [响应模型名](model-probe.json)：deepseek-flash、qwen3-vl-plus、qwen3.8-omni-flash均返回同名别名；不是快照证明，也不是UI自动点击或全模态能力验证 |

[录像清单](recording-inventory.json)记录两路文件时长与分辨率，原片留在机主本地，不随仓库发布。文件存在不代表全时段有动态画面；本机虚拟屏销毁后的尾部可能冻结，不能用文件时长凑任务覆盖。timeline仅提供启动时间近似对齐，仍需用共同可见事件核验。最终版本部署摘要见 [deployment](deployment.json)；三轮采样来自较早版本，不声明节点归属保护修正后已重跑。

已有公开查询样本见 [证据索引](../README.md)，不替代这些指定演示。新保护和录制修正之后的完整五段视频、真实时间点仍未产生，README不会使用上游视频替代。
