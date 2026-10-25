# 锁屏任务保护（2026-10-25）

设备：华为 Mate 40 Pro（NOH-AN01），Android API 31。

## 设备观察

- 原执行进程与控制端仍在，不能将现象简单归因为控制端进程被杀。
- 临时计算器虚拟屏在锁屏时仍有图像，但控件来自 `com.android.systemui:id/presentation` / `clock_view`，不再是计算器。ActivityManager 报告计算器 `state=STOPPED`。
- 持有 shell UID 的 `PARTIAL_WAKE_LOCK` 时仍出现锁屏覆盖，因此保持 CPU 运行不等于目标 App 可继续交互。
- 系统没有 `VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED`；尝试 `OWN_DISPLAY_GROUP` 被 `SecurityException: Requires ADD_TRUSTED_DISPLAY permission` 拒绝。上述能力探针未改变正式虚拟屏 flags。

## 修复行为

- 新任务在熄屏或 Keyguard 显示期间排队，提示解锁后自动开始。
- 运行中任务暂停并保留虚拟屏；锁屏不计入主屏冲突十分钟超时。
- 锁屏即阻止输入，不依赖下一次定时采样；规划代际变化后重新观察，不能执行旧坐标。
- 循环内锁屏相关读屏/模型异常等待解锁后重新观察；锁屏打断输入不消耗执行步骤。
- 首次启动目标 App 时遇到锁屏会等待后重试；仍允许取消。
- 不提供进程强杀、设备重启或外部 App 自行销毁页面后的会话恢复。

## 验证范围

- Android 两模块构建与 Lint 通过；最终补充修改后 shell 构建与 Lint 再次通过。
- `scripts/test-interaction-regressions.sh` 全部通过，新增 `ScreenLockRegression` 覆盖输入即时拦截、模拟锁屏一小时、解锁重观察、真实同 App 冲突仍超时、锁屏期间取消。
- 手机上真实模型计算器任务 `screen-lock-calculator-1792917848` 在锁屏时停于 QUEUED，第 0 步，提示“手机已锁屏，解锁后自动开始，任务已保留”。排队期间未启动计算器；排队前应用选择调用 executor 两次。随后主动取消该测试并部署最终版本。
- 最终版本真实模型任务 `screen-lock-calculator-1792918984` 已运行至第 5 步、display 158；运行中熄屏后进入 PAUSED。26.2 秒、41.9 秒及68.0 秒状态检查均保持第 5 步、display 158，executor 调用保持 8 次，`finalized=false`。运行中锁屏保留已实测通过。
- 机主正常解锁后，同一任务自动恢复并在第 8 步进入 `ANSWERED`，答案 `579`（123 + 456），`finalized=true`，总耗时 642,079 ms（包含锁屏等待），executor 12 次、verifier 1 次。原任务未重建，最终结果已归档；“运行中锁屏→保留→解锁→继续完成”实机验证通过。

原始测试脚本、任务状态事件留在工作区 `../docs/validation/screen-lock-2026-10-25/`（相对于仓库根目录）。模型配置仅驻留内存，不写入证据。已部署最终 shell APK，旧版备份保留为设备 `/data/local/tmp/silent-shell-before-lock-fix.apk`。
