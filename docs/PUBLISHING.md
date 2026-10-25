# GitHub 发布准备

项目名称：**隐航 / QuietPilot**（原称隐控），建议新仓库名 `quietpilot`（项目名称已由机主确认）。名称占用及商标状态未核查。目标仓库为 [Cyclopsxu/QuietPilot](https://github.com/Cyclopsxu/QuietPilot)，SSH 地址 `git@github.com:Cyclopsxu/QuietPilot.git`。本次发布源码，不创建 Release 或上传安装包。

## 仓库边界

- 当前本地目录仍为 `ShadowAuto/`；不重命名正在使用的工作目录。
- `origin` 用于 `Cyclopsxu/QuietPilot`；原始 `android-notes/ShadowAuto` 保留为 `upstream`。
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

机主已提供空仓库。首次连接保留上游并设置项目远程：

```sh
git remote rename origin upstream
git remote add origin git@github.com:Cyclopsxu/QuietPilot.git
git push -u origin main
```

之后使用 `git push origin main` 更新本项目。推送前确认 `git remote -v`，不向 `upstream` 推送本分支。启动器项目链接已更新为 QuietPilot，上游出处继续保留在 README、NOTICE 和二次开发说明。

本次准备不创建 GitHub Release，也不自动开启 GitHub Pages。`web-launcher` 的本地启动流程与静态 Pages 部署是不同交付；后者需额外核对资源、base path 与 WebUSB 安全上下文。
