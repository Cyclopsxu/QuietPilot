# Contributing / 参与开发

欢迎提交能复现的问题报告、文档修正和范围明确的代码修改。当前项目为 Android 自动化原型，实测范围有限；请保留失败和未验证的结果。

## 开始前

阅读 [README](README.md)、[开发文档](docs/DEVELOPMENT.md)及 [NOTICE](NOTICE)。查阅已有问题后再提交新问题；涉及密钥、个人页面或安全漏洞时按 [SECURITY.md](SECURITY.md) 处理。

## 提交修改

1. 说明具体问题、预期行为与实际行为；设备问题注明机型、系统/API 版本和代码提交。
2. 保持修改范围清晰，保留上游署名；功能或接口变更同步更新开发文档。
3. 根据影响执行构建、Lint、主机回归及必要真机测试，说明哪些已执行、哪些未执行。
4. 提交脱敏日志或最小复现，避免订单、账号、定位、私人笔记、音频、密钥及未检查的原始录像。

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
./gradlew :android-shell:lintDebug :controller-app:lintDebug
./scripts/test-interaction-regressions.sh
```

Web 修改另在 `web-launcher` 执行 `npm ci` 与 `npm run build`。主机测试不替代系统隐藏接口、厂商行为或真实输入法验收。

## 不提交的内容

本地 IDE 配置、Gradle/Node 缓存、签名与密钥文件、原始私人证据、生成的 APK/OCR 部署产物。部署资源通过 `./gradlew :android-shell:syncWebLauncherArtifacts` 生成。Git 历史中的既有上游产物仍保留，不通过重写历史清理。

新功能不能绕过主屏保护、只读约束或本人验证边界。API 配置占位符不得替换为真实凭据。提交内容继续遵循仓库 [Apache-2.0](LICENSE) 许可证。
