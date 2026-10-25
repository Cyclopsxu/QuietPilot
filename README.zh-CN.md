# 隐航 / QuietPilot

[English](README.md) | 中文 | [使用手册](docs/USER_GUIDE.zh-CN.md) | [开发文档](docs/DEVELOPMENT.md)

[GitHub: Cyclopsxu/QuietPilot](https://github.com/Cyclopsxu/QuietPilot)

在 Android 虚拟显示屏中运行真实 App，由模型规划并执行操作，通过手机控制端提交任务、查看预览和接收结果。

本项目是基于 [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto) 的独立二次开发原型，增加结果验收、人工交接、技能学习、语音与屏幕上下文等能力。当前通过 ADB / `app_process` 运行，主要实测设备为华为 Mate 40 Pro；不保证所有 Android 设备或第三方 App 兼容。

QuietPilot 中文名「隐航」（原称「隐控」），寓意后台任务自行推进、主屏使用优先。建议 GitHub 仓库名为 `quietpilot`，尚未核查名称占用或商标状态。

## 功能

- **后台执行与预览**：虚拟屏操作真实 App，控制端显示任务进度与视频预览。
- **多任务与主屏优先**：最多两个活动任务，同 App 串行；用户在主屏使用同 App 时暂停后台。
- **语音与上下文**：悬浮球录音、读取当前屏幕上下文、执行中修改或追加要求。
- **技能学习**：从合格成功轨迹学习参数化技能，复用时按当前页面定位，偏离后有限救援并可回退模型。
- **结果验收与只读查询**：核对办事证据，查询返回短答案及页面引文。
- **人工交接与结果交付**：登录、验证码、支付等边界暂停，将原页面交给用户处理。
- **锁屏保护**：新任务在锁屏时排队，运行中任务暂停保留，解锁并解除冲突后重新观察续跑。
- **历史与指标**：记录步骤、模型调用、耗时、截图及隔离诊断。

## 演示与验证

[五段演示视频（02:57）](docs/evidence/demo-path/step6-five-segments.mp4) · [时间索引与限制](docs/evidence/demo-path/VIDEO.md)

视频覆盖并行输入、技能复用、上下文、查询和咖啡语音追加，明确标注实录、静态证据和失败部分，不是一次连续全成功录像。

已归档样本包括计算器查询、备忘录技能三段、公开酒店名转备忘录、咖啡修改规格及支付前交接、锁屏暂停后解锁完成计算。微信发送未通过；完整店铺地址传递和咖啡成功流程连续双屏录像仍缺。详见[证据索引](docs/evidence/README.md)、[Step6 状态](docs/STEP6-STATUS.md)与[锁屏验证](docs/evidence/screen-lock/report.md)。

## 安装预览版

前往 [GitHub Releases](https://github.com/Cyclopsxu/QuietPilot/releases) 下载控制端 APK 或首次部署 ZIP，按[安装说明](docs/INSTALL.md)操作。APK 之外仍需电脑 ADB 启动 shell 后台；当前为开发证书签名的预览版。

## 快速开始

### 环境要求

- Android 10 / API 29 或以上，启用 USB 调试并授权电脑；最低版本要求不代表兼容认证。
- JDK 17 或兼容版本、Android SDK Platform 36、ADB；使用仓库 Gradle Wrapper。
- 使用本地浏览器启动器时，需要兼容仓库 Vite 版本的 Node.js/npm，以及支持 WebUSB 的浏览器。
- 首次构建需要下载依赖和 OCR 资源；模型任务需要网络及相应服务的 API 配置。

### 构建与启动

在仓库根目录执行：

```sh
adb devices -l
./gradlew :android-shell:syncWebLauncherArtifacts
cd web-launcher
npm ci
npm run dev
```

打开终端输出的本地地址，按启动器指引连接手机、上传资源、安装控制端并启动 shell 后台。首次安装可能需要在手机上确认。

打开手机控制端，配置模型并测试连接，然后提交任务。安装控制端本身不会启动 shell 后台；手机重启或后台被杀后需要重新启动。手动 ADB 部署、OCR 路径、日志与故障排查见[使用手册](docs/USER_GUIDE.zh-CN.md#3-安装与启动)。

### 模型配置

在配置页分别填写各角色的 API Key、API Base 和模型名称：

| 角色 | 用途 | 支持的远程接口 |
| --- | --- | --- |
| executor | 目标理解与操作规划 | DeepSeek 官方 HTTPS 接口 |
| verifier | 页面证据与结果核对 | 阿里云百炼兼容接口 |
| intake | 语音理解与任务路由 | 阿里云百炼兼容接口 |

具体模型名称与当前账号能力以连接测试为准。当前远程主机白名单为 `api.deepseek.com` 和 `dashscope.aliyuncs.com`，另支持本机回环测试地址。任务可能把控件文本、截图、音频和上下文发送给已配置服务；OCR 在设备侧运行。不要提交真实密钥或未经脱敏的页面证据。

## 项目结构

```text
android-shell/    shell 执行进程、虚拟屏、自动化与 RPC
controller-app/   Android 控制端、预览、悬浮球与历史
android-stubs/    系统接口编译期声明
paddler-ocr/      OCR 库、模型与运行资源
web-launcher/    浏览器部署工具
scripts/         回归、设备探针与录像工具
docs/            使用手册、开发方案与验证证据
```

## 开发与测试

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
./gradlew :android-shell:lintDebug :controller-app:lintDebug
./scripts/test-interaction-regressions.sh
```

先构建再运行主机回归；设备探针与真实 App 验收需单独进行。模块职责、状态契约、技术方案、测试基线和后续工作集中维护在[开发文档](docs/DEVELOPMENT.md)。

提交变更时说明目的、影响范围和实际验证结果；功能变化同步更新开发文档。公开证据应脱敏，未完成的设备验收不得记为通过。

## 已知限制

- 当前设备的全程焦点/IME 隔离尚未认证；实验兼容模式不能补齐系统能力，不能保证完全不打扰主屏。
- 锁屏时暂停，不能持续自动操作；不支持进程强杀或设备重启后恢复原 UI 会话。
- 第三方 App 的启动、控件可读性、登录与验证可能阻断任务；验证码、支付和身份验证由本人处理。
- 技能仍可能救援或回退模型，已有样本未证明整体提速。
- RPC 默认监听 `127.0.0.1:43110`，尚无应用级鉴权；回环绑定不能隔离同机其他进程。控制端保存的 API 配置未采用专用密钥保险库。

参与开发见[贡献指南](CONTRIBUTING.md)，安全问题见[安全说明](SECURITY.md)，仓库发布流程见[GitHub 发布准备](docs/PUBLISHING.md)。

## 文档

- [详细使用手册](docs/USER_GUIDE.zh-CN.md)：安装、配置、操作和故障排查。
- [开发与技术方案](docs/DEVELOPMENT.md)：实现、模块、约束与维护计划。
- [隔离能力与边界](docs/ISOLATION.md)：设备能力与兼容实验。
- [演示说明](docs/DEMO.md)与[证据索引](docs/evidence/README.md)：实测范围、失败及来源。
- [二次开发说明](docs/DERIVATIVE_CHANGES.md)：上游与本分支改动归属。

## 许可证与致谢

本项目沿用 [Apache License 2.0](LICENSE)。上游基线为 `611e5eb0e1c94befda3c17c97438912dfd30e8a8`，保留原作者署名与适用声明，详见 [NOTICE](NOTICE)。本分支与上游无背书关系。
