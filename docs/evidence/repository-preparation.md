# QuietPilot GitHub 发布准备记录

日期：2026-10-26。项目名称「隐航 QuietPilot」已由机主确认。现有提交基线 `cf017b7`；本次准备包括工作区锁屏增量、文档拆分、对外更名与仓库整理。尚未创建远程仓库、推送或发布安装包。

## 实际执行结果

| 项目 | 结果 |
| --- | --- |
| 两模块 assembleDebug | 通过 |
| 两模块 lintDebug | 通过 |
| 八组 Java 交互回归 | 通过：连接、UI恢复、交互保护、转移、查询、RPC输出、锁屏、重复检测 |
| isolation-report JS / record-demo Python | 均通过 |
| Web production build | 通过，Vite 8.1.3；项目包名 quietpilot-web-launcher |
| 常见凭据模式检查 | 当前候选文本与 HEAD 可达历史 746 个文本 blob 未命中私钥头、常见服务 token / API key 格式 |
| 生成产物清理 | 12 个 APK/OCR 文件移出当前 Git 树，本地副本保留，首次部署由 Gradle 生成 |
| 上游声明 | LICENSE 正文保留；NOTICE 与二次开发说明注明 QuietPilot 来源 |

构建使用 JDK 21、仓库 Gradle Wrapper，源码级别仍为 Java 17。仅有 Gradle 既有弃用提示，未导致检查失败。测试输出摘要和本次变更文件哈希见[机器检查记录](repository-preparation.json)。

## 范围和限制

- 本次未在手机上安装更名后的 APK，不将既有锁屏实机证据写成新包复测；已有实机结果见[锁屏报告](screen-lock/report.md)。
- 模式扫描只覆盖选定常见凭据格式，不证明所有私人数据均已识别；现有媒体沿用各自公开前检查记录。
- 从当前树移除生成物不清除历史；上游完整历史保留，没有强制推送或历史重写。
- `.idea/gradle.xml`、`.idea/misc.xml` 的机主本地修改未纳入准备提交。
- 包名、RPC、设备数据目录和 WebUSB 凭据命名空间保持不变。旧证据名称不修改，避免混淆来源。
- `origin` 仍指向上游；新仓库 URL 与可见性未设置。名称占用与商标未核查。

后续实际推送步骤见[发布说明](../PUBLISHING.md)。
