# 虚拟屏隔离能力边界

2026-10-24；Mate 40 Pro / NOH-AN01 / HarmonyOS 4.2.0.192 / SDK 31。

| 层级 | 实际情况 | 结论 |
| --- | --- | --- |
| 系统资源 | 本轮 `adb shell cmd overlay lookup android android:bool/config_perDisplayFocusEnabled` 返回 `false` | 系统未开启全局每屏焦点 |
| 独立焦点标志 | 既有设备探针找不到 OWN_FOCUS、STEAL_TOP_FOCUS_DISABLED 字段 | 系统未提供，不能通过反射或猜数值补出能力 |
| Trusted display | 既有权限探针 ADD_TRUSTED_DISPLAY=-1；创建候选带 trusted 时被拒绝 | 权限被拒绝，与字段缺失分别记录 |
| 本地 IME | setDisplayImePolicy 调用及回读写入 isolation；既有实测读回 fallback | 调用返回成功不等于本地策略生效 |
| 触摸防抢焦点 | 本机 FLAG_NO_FOCUS_CHANGE=64；对照普通触摸切到次屏、防抢触摸保持主屏 | 触摸层规避实际生效，不代表拥有原生独立焦点 |
| 运行时保护 | 危险键门禁、无障碍文字写入、临时剪贴板恢复、发现焦点干扰后锁定暂停 | 路径相关保护，不能代替并行输入实测 |

源码核查而非凭记忆：AOSP [Android 13 DisplayManager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android13-release/core/java/android/hardware/display/DisplayManager.java) 无这两个焦点字段，[Android 14 DisplayManager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/hardware/display/DisplayManager.java) 已有。因此 AOSP 正式大版本边界为 Android 14 / API 34；它们是隐藏的 TestApi/SystemApi，不能表述为从 API 34 起任意 App 均有公共 SDK 权限。OWN_FOCUS 还要求 trusted。本机厂商回移能力应以运行时字段和行为探针为准。

创建时日志打印 SDK、失败候选及异常、成功候选和最终 flags、IME 设置结果与回读。任务归档保留 creationAttempts；未实际尝试的候选不声称失败。`isolation.mitigations` 表示已启用的代码保护及本地 IME 是否回读成功；不是对所有输入动作已经验收的声明。`behaviorValidated` 仍为 false，完整三轮测试前不改变含义。

严格门禁能力不足时默认阻止后台。兼容实验模式需用户明确选择；本次部署沿用已有选择，不自动替用户开启。旧结果与局限见 [既有对照与人工输入记录](evidence/isolation/previous-test.md)，本轮验收见 [报告](evidence/isolation/report.md)。
