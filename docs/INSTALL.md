# 安装隐航 QuietPilot 预览版

当前版本：`v0.1.0-alpha.1`。下载：[GitHub Releases](https://github.com/Cyclopsxu/QuietPilot/releases)。这是开发预览版，非生产稳定版。

## 选择下载文件

| 文件 | 用途 |
| --- | --- |
| `QuietPilot-v0.1.0-alpha.1-controller.apk` | 可直接安装的控制端 |
| `QuietPilot-v0.1.0-alpha.1-android-arm64.zip` | 控制端、shell 执行包、ARM64 OCR、启动脚本和说明；首次部署推荐下载 |
| `SHA256SUMS.txt` | 校验 APK 与 ZIP 的完整性 |

控制端要求 Android 10 / API 29 或以上；配套 OCR 仅提供 `arm64-v8a`。主要实测设备是 Mate 40 Pro（HarmonyOS 4.2 / API 31），不是所有 Android 10+ 手机都兼容。安装 APK 只安装控制端，完整自动化还需要电脑上的 ADB 启动 shell 后台，不是纯手机免电脑安装方案。

## 1. 安装控制端

下载 APK，在手机文件管理器中打开，按系统提示允许该来源安装应用；安装、身份或密码确认由本人在手机完成。也可在电脑执行：

```sh
adb devices -l
adb install -r QuietPilot-v0.1.0-alpha.1-controller.apk
```

启用开发者选项和 USB 调试，并授权电脑。`adb devices -l` 需显示 `device`；`unauthorized` 需要手机确认。多个设备时为每条 ADB 命令添加 `-s <序列号>`。

包名仍为 `com.silentauto.controller`。本版是非 debuggable 的 release 构建，使用开发证书签名；不是正式生产签名。与旧版签名相同时可覆盖安装保留配置；出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 表示签名不同，不能覆盖。不要直接卸载来解决，先记录所需配置和历史；卸载会丢失控制端私有数据，设备上的 shell 数据与其分开存储。

## 2. 首次部署后台与 OCR

下载并解压 ARM64 ZIP，打开终端，进入解压后的 `QuietPilot-v0.1.0-alpha.1-android-arm64` 目录。先在手机完成或取消旧后台任务；下面操作需要旧后台停止后进行，避免替换仍在运行的执行代码。

如果已有旧后台且确定不再保留实时页面，停止它：

```sh
adb shell 'if [ -f /data/local/tmp/silent-auto.pid ]; then kill "$(cat /data/local/tmp/silent-auto.pid)" 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi'
```

然后上传资源并启动：

```sh
adb install -r QuietPilot-v0.1.0-alpha.1-controller.apk
adb push silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell mkdir -p /data/local/tmp/shadowauto/ocr
adb push ocr/. /data/local/tmp/shadowauto/ocr/
adb push start-quietpilot.sh /data/local/tmp/start-quietpilot.sh
adb shell sh /data/local/tmp/start-quietpilot.sh
```

如果脚本提示后台已存在，先处理旧任务，不要另起第二个后台。查看启动日志：

```sh
adb shell cat /data/local/tmp/silent-auto.log
```

**隔离能力不足会默认阻止执行。**只有本人明确接受兼容实验风险、在专用测试场景下，才在后台停止后改用：

```sh
adb shell sh /data/local/tmp/start-quietpilot.sh --allow-unverified-isolation=true
```

该选项不补齐系统权限或隔离能力，不能保证主屏完全不受影响。

## 3. 配置和使用

打开「隐航 / QuietPilot」，确认已连接后台；按需要授予悬浮窗、麦克风及通知权限。在配置页分别填写 executor、verifier、intake 的 API Key、接口地址和可用模型，点击测试连接。默认远程主机限定为 `api.deepseek.com` 与 `dashscope.aliyuncs.com`，另支持回环测试地址。

先用公开页面和简单查询测试，再使用语音或办事任务。悬浮球按住约一秒，等就绪震动后说话，松手提交；登录、验证码和支付边界需本人处理。任务可能将控件文字、截图、音频与屏幕上下文发送给已配置的模型服务。

手机重启或 shell 进程被杀后，需要电脑重新执行后台启动命令；APK 不会自行获得 shell 权限。锁屏中新任务排队，运行中任务暂停，解锁后重新观察续跑，不是锁屏持续操作。

## 4. 校验下载

将 `SHA256SUMS.txt` 和下载文件放在同一目录。下载了两个完整文件时：

```sh
# macOS
shasum -a 256 -c SHA256SUMS.txt
# Linux
sha256sum -c SHA256SUMS.txt
```

只下载一个文件时，未下载项会报缺失；只核对所下载那一项是否为 OK。Windows PowerShell 可用 `Get-FileHash <文件名> -Algorithm SHA256` 与校验文件比较。签名证书 SHA-256 记录在 Release 校验信息中；SHA-256 文件不是独立的发行者身份认证。

## 已知限制

- 焦点/IME 全程隔离未获通用认证；兼容实验可能影响主屏。不同 App 最多两个活动任务，同 App 串行且用户优先。
- 微信发送未通过；部分 App 启动退出、控件不可读、登录或验证码可能阻断任务。
- 咖啡修改到大杯冰及支付前交接有历史实测，连续成功双屏录像仍缺；不自动付款。
- 公开店名上下文转备忘录有证据，完整地址传递未完成验收；技能可能回退模型，未证明整体提速。
- RPC 监听本机回环 `127.0.0.1:43110`，尚无应用级鉴权；API 配置没有专用密钥保险库。
- 不支持进程强杀或设备重启后恢复原 UI 会话。更名与本版签名产物没有新一轮完整业务真机验收，历史结果不等于此包全流程认证。
- 当前采用开发证书签名。不要把本预览包作为生产部署或通用设备认证版本。

技术方案见[开发文档](DEVELOPMENT.md)，详细操作和排错见[使用手册](USER_GUIDE.zh-CN.md)，实测边界见[证据索引](evidence/README.md)。
