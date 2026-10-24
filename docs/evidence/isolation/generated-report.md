# 并行隔离采样报告

周期采样可能遗漏短事件；候选词是否消失、人工是否持续输入与滚动必须结合录像核验。IME 未激活不等于目标确定保持在主屏。

## step6-round1-1792814354

设备：HUAWEI NOH-AN01 SDK 31；结果：FAILED

隔离配置：`{"selectedFlags":331,"selectedFlagsHex":"14b","candidateIndex":2,"ownFocusSupported":false,"noStealSupported":false,"ownFocusRequested":false,"noStealRequested":false,"localImeVerified":false,"configurationAvailable":false,"behaviorValidated":false,"experimentalOverride":true,"backgroundAllowed":true,"status":"experimental_unverified","message":"兼容实验模式：隔离能力不足，不保证主屏输入和焦点不受影响","touchNoFocusChangeFlag":64,"touchNoFocusChangeAvailable":true,"mitigations":{"noFocusChangeTouch":true,"dangerousKeyGate":true,"accessibilityTextWrites":true,"clipboardSnapshotRestore":true,"localImePolicy":false},"capture":{"mode":"layer_stack_mirror","fallback":"","requests":5,"misses":0,"surfaceSwitches":0,"frames":968},"creationAttempts":[{"candidateIndex":0,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":1,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":2,"flags":331,"accepted":true}],"platformDisplayFlags":8,"localImeSetSucceeded":false,"imePolicyBefore":1,"imePolicyReadback":1,"sdkInt":31,"manufacturer":"HUAWEI","model":"NOH-AN01","nativeBackgroundKeysAllowed":false}`

采样 292 次，覆盖 146556 ms，最大间隔 1277 ms。未通过完整并行验收

| 指标 | 次数 | 未知采样 |
| --- | ---: | ---: |
| foreground | 0 | 0 |
| focus | 0 | 0 |
| ime | 0 | 0 |
| clipboard | 0 | 0 |

事件时间线（归因仅为时间关联）：

未观察到事件；不代表未采样时段无事件。
## step6-round2-1792814611

设备：HUAWEI NOH-AN01 SDK 31；结果：CANCELLED

隔离配置：`{"selectedFlags":331,"selectedFlagsHex":"14b","candidateIndex":2,"ownFocusSupported":false,"noStealSupported":false,"ownFocusRequested":false,"noStealRequested":false,"localImeVerified":false,"configurationAvailable":false,"behaviorValidated":false,"experimentalOverride":true,"backgroundAllowed":true,"status":"experimental_unverified","message":"兼容实验模式：隔离能力不足，不保证主屏输入和焦点不受影响","touchNoFocusChangeFlag":64,"touchNoFocusChangeAvailable":true,"mitigations":{"noFocusChangeTouch":true,"dangerousKeyGate":true,"accessibilityTextWrites":true,"clipboardSnapshotRestore":true,"localImePolicy":false},"capture":{"mode":"layer_stack_mirror","fallback":"","requests":0,"misses":0,"surfaceSwitches":0,"frames":1569},"creationAttempts":[{"candidateIndex":0,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":1,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":2,"flags":331,"accepted":true}],"platformDisplayFlags":8,"localImeSetSucceeded":false,"imePolicyBefore":1,"imePolicyReadback":1,"sdkInt":31,"manufacturer":"HUAWEI","model":"NOH-AN01","nativeBackgroundKeysAllowed":false}`

采样 330 次，覆盖 165059 ms，最大间隔 818 ms。采样四项为0；人工候选词与视频仍需核验

| 指标 | 次数 | 未知采样 |
| --- | ---: | ---: |
| foreground | 0 | 0 |
| focus | 0 | 0 |
| ime | 0 | 0 |
| clipboard | 0 | 0 |

事件时间线（归因仅为时间关联）：

未观察到事件；不代表未采样时段无事件。
## step6-round3-1792814867

设备：HUAWEI NOH-AN01 SDK 31；结果：CANCELLED

隔离配置：`{"selectedFlags":331,"selectedFlagsHex":"14b","candidateIndex":2,"ownFocusSupported":false,"noStealSupported":false,"ownFocusRequested":false,"noStealRequested":false,"localImeVerified":false,"configurationAvailable":false,"behaviorValidated":false,"experimentalOverride":true,"backgroundAllowed":true,"status":"experimental_unverified","message":"兼容实验模式：隔离能力不足，不保证主屏输入和焦点不受影响","touchNoFocusChangeFlag":64,"touchNoFocusChangeAvailable":true,"mitigations":{"noFocusChangeTouch":true,"dangerousKeyGate":true,"accessibilityTextWrites":true,"clipboardSnapshotRestore":true,"localImePolicy":false},"capture":{"mode":"layer_stack_mirror","fallback":"","requests":0,"misses":0,"surfaceSwitches":0,"frames":1535},"creationAttempts":[{"candidateIndex":0,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":1,"flags":1355,"accepted":false,"error":"SecurityException"},{"candidateIndex":2,"flags":331,"accepted":true}],"platformDisplayFlags":8,"localImeSetSucceeded":false,"imePolicyBefore":1,"imePolicyReadback":1,"sdkInt":31,"manufacturer":"HUAWEI","model":"NOH-AN01","nativeBackgroundKeysAllowed":false}`

采样 387 次，覆盖 193081 ms，最大间隔 676 ms。采样四项为0；人工候选词与视频仍需核验

| 指标 | 次数 | 未知采样 |
| --- | ---: | ---: |
| foreground | 0 | 0 |
| focus | 0 | 0 |
| ime | 0 | 0 |
| clipboard | 0 | 0 |

事件时间线（归因仅为时间关联）：

未观察到事件；不代表未采样时段无事件。
