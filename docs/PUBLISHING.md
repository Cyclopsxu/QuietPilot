# GitHub 发布准备

项目名称：**隐航 / QuietPilot**（原称隐控），建议新仓库名 `quietpilot`（项目名称已由机主确认）。名称占用及商标状态未核查。本文说明发布准备，不表示已经创建远程仓库、推送代码或发布 APK。

## 仓库边界

- 当前本地目录仍为 `ShadowAuto/`；不重命名正在使用的工作目录。
- 当前 `origin` 指向上游 `android-notes/ShadowAuto`，不能作为本项目推送目标。
- 保留上游完整 Git 历史、Apache-2.0、NOTICE 与来源声明，不将上游基础能力称为本分支原创。
- 显示名与启动器使用 QuietPilot；`com.silentauto.*` 包名、设备目录、RPC 端口、WebUSB 凭据命名空间与历史证据继续兼容。
- 旧证据与原演示保留采集时名称；不能将更名前录像称作更名后 APK 的验收。

## 源码与构建产物

当前源码树不再跟踪 `web-launcher/static/*.apk` 与 `web-launcher/static/ocr/`；本地文件仍保留。首次运行启动器前必须执行：

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
```

Gradle Wrapper JAR 是构建引导文件，继续跟踪。历史提交中的旧 APK/OCR 不因当前树移除而消失；没有重写历史。真实发布 APK 应由明确提交构建，注明版本、哈希与测试范围，不能将旧静态包当作最新发布。

`.env`、签名、缓存和新 IDE 状态已忽略。已有被跟踪的 `.idea` 文件本次保持原状，用户的两项本地 IDE 修改不纳入本次提交。仓库外原始验证数据不上传；公开证据继续按来源哈希和脱敏副本管理。

## 发布前检查

1. Android 两模块 assemble/Lint、主机回归，以及 Web build。
2. 本地 Markdown 文件链接、Git diff 格式、当前文件和可达历史的常见凭据模式检查。
3. 检查待提交文件，排除密钥、个人配置、未经检查的截图/录像及旧生成安装包。
4. 确认 README、开发文档和证据没有把计划、模拟测试或失败结果写成已通过。

检查结果及局限见 [发布准备记录](evidence/repository-preparation.md)。凭据模式扫描不是完整安全审计；历史证据沿用各自验收范围。

## 连接自己的远程仓库

先在自己的 GitHub 账号下创建空仓库（不初始化 README/许可证，避免与现有历史冲突），并确认可见性。下面命令中的 `OWNER` 是占位符，必须替换；不要直接向当前上游 origin 推送。

```sh
git remote rename origin upstream
git remote add origin https://github.com/OWNER/quietpilot.git
git push -u origin main
```

这些命令尚未自动执行。仓库 URL 确定后，将启动器当前标为“上游 ShadowAuto”的仓库入口改为本项目入口，并保留独立上游出处链接。文档继续使用仓库相对链接，无需为未创建的远程地址编造链接。

本次准备不创建 GitHub Release，也不自动开启 GitHub Pages。`web-launcher` 的本地启动流程与静态 Pages 部署是不同交付；后者需额外核对资源、base path 与 WebUSB 安全上下文。
