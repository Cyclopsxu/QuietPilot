# Step6 五段证据演示成片

[播放 / 下载成片](step6-five-segments.mp4) · [中文字幕](step6-five-segments.srt) · [剪辑计划](video-edit-plan.json) · [来源与成片哈希](video-build-manifest.json)

2026-10-24整理，片长 **02:57**，1280×720、15fps、H.264，无音轨。画面内已标注真实录屏、定格、静态验收图或归档说明。五段结构完整，未将未通过项目标为通过，也不表示一次连续成功执行。原始语音未留在本片，语音事实依据机主反馈及任务transcript/intake记录。

| 成片时间 | 段落 | 画面与实际结论 | 可核对证据 |
| --- | --- | --- | --- |
| 00:00–00:08 | 片头 | 设备、素材标签和五段结构 | [完成清单](../../STEP6-STATUS.md) |
| **00:08–00:46** | 01 并行输入与微信启动 | 微信启动录像未到消息页；随后显示另一轮同一时段的主屏拼音输入/后台搜索节选。三轮采样四项0，仍不认证全程隔离 | [隔离报告](../isolation/report.md)、[微信退出](wechat-exit-summary.json) |
| **00:46–01:20** | 02 技能探索、复用、救援 | 三个独立备忘录任务结果并列，各4秒录屏后定格10秒；三段VERIFIED，复用有回退且更慢。救援过程用轨迹说明，不把最终结果录像当作救援现场 | [三段报告](../skills/report.md)、[真实偏离记录](../skills/rescue-fault.json) |
| **01:20–01:45** | 03 边看边办替代 | 公开酒店名与保存后重开的笔记录屏对照；真实语音解析店名，10步VERIFIED，同一任务291交付；没有完整地址，非微信发送 | [上下文报告](context-memo-report.md)、[交付记录](context-memo-delivery.json) |
| **01:45–02:15** | 04 只读查询与重复检测 | 录屏属于最终16步FAILED的计算器重测；156静态验收图来自另一个6步成功任务。另有7步成功归档，公开查询失败也明确列出 | [失败任务](../query/step6-calculator-repeat-failed.json)、[6步成功](../query/calculator-simple-final.json)、[7步成功](../query/step6-calculator.json)、[不确定公开查询](public-hours.json) |
| **02:15–02:47** | 05 咖啡语音追加与交接 | 错误页实录、首轮拿铁大杯静态图、另一次美式双语音支付前归档分别呈现。美式20步WAITING_USER/payment，主屏大杯冰由机主确认；缺支付前连续双路录像，未补造该画面 | [咖啡规格图](meituan-large-cup-evidence.png)、[支付前快照](coffee-voice-checkout.json)、[本人确认](coffee-owner-confirmation.json)、[之后取消](coffee-voice-checkout-final.json) |
| 02:47–02:57 | 限制与收尾 | 微信、完整地址、隔离认证、录像缺口及未鉴权RPC | [能力边界](../../ISOLATION.md) |

MP4已写入上述五段及片头/片尾的章节。字幕时间与剪辑计划一致。不是把15秒结果片段或上游视频扩展为本分支完整成功录像。

## 素材取舍与隐私

- 两组并排录屏按各路启动时间近似对齐，不声称帧精确同步。20秒并行输入片段来自第二轮（主屏114–134秒、虚拟屏3–23秒）；虚拟屏前8秒黑色遮挡地图，随后才展示公开路线输入区；主屏出现的同文已由机主确认手动输入。
- 主屏输入片段只保留键盘/候选栏，后台只保留公共检索输入区，地图、位置距离和其他页面不公开。上下文段只保留公开酒店名局部。
- 技能段只取测试笔记结果页，避开旧笔记列表；定格时长写在画面上。三个独立任务并列展示，未暗示并发。
- 拿铁大杯图与美式支付前归档明确分开。支付前阶段没有录像，不使用拿铁图片冒充美式订单页，也不制作虚构App画面。
- 原片保留在机主本地仓库外；公开视频未嵌入录音、订单账号、联系人或配送地址。全片解码、16场景中帧与起末抽帧目视、固定裁剪复核及2655帧本地OCR筛查结果见[媒体检查](video-qa.json)。OCR为模式筛查，不能替代语义和隐私复核。

## 重建

需要Pillow、ffmpeg/ffprobe、中文字体，以及机主本地原片。仅使用repo中的公开副本无法重建被省略的原片。

```sh
python3 scripts/build-step6-demo.py \
  --source-root ../docs/validation/step6-2026-10-24
```

本次使用工作区捆绑的Python/Pillow；默认字体为macOS的STHeiti Medium，其他环境可传入`--font`。脚本不会连接手机、读取模型配置或合成App界面。输出成片、SRT和构建清单，清单含剪辑计划与所有实际媒体源SHA256。不同ffmpeg/字体可能改变输出像素与哈希。

## 当前完成边界

Step6.5的视频整理和6.7的首页链接/真实时间点已完成；用户允许未通过项目如实标注，因此以此证据版交付。原指定微信消息演示、完整地址与全程无打扰认证仍未通过；咖啡成功阶段的连续双路原片缺失，现有资料无法补回。构建/Lint/交互回归结果沿用受测Android源码记录，本轮未修改Android产品代码；新增媒体脚本以实际渲染、完整解码和产物校验验证。

本地逐帧OCR复核（macOS，模式筛查，不能替代目视复核）：

```sh
clang -fobjc-arc scripts/verify-step6-video.m \
  -framework Foundation -framework AVFoundation -framework Vision \
  -framework CoreVideo -framework CoreMedia -o /tmp/verify-step6-video
/tmp/verify-step6-video docs/evidence/demo-path/step6-five-segments.mp4 /tmp/step6-video-ocr.json
```
