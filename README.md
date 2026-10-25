# 隐航 / QuietPilot

English | [中文](README.zh-CN.md) | [User guide](docs/USER_GUIDE.en.md) | [Development document (中文)](docs/DEVELOPMENT.md)

Run real Android apps on virtual displays, let models plan and execute actions, and use an Android controller to submit tasks, preview progress and receive results.

This is an independent derivative prototype of [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto), extending result verification, human handoffs, learned skills, voice and screen context. Deployment currently uses ADB / `app_process`. Most device testing uses a Huawei Mate 40 Pro; compatibility with every Android device or third-party app is not established.

QuietPilot (隐航, formerly 隐控) keeps background tasks on course while foreground use takes priority. Suggested repository slug: `quietpilot`. Naming availability and trademark status have not been verified.

## Features

- **Background execution and preview:** task-owned virtual displays, live progress and video previews.
- **Scheduling and foreground priority:** up to two active tasks, serialized per app; foreground use of the same app pauses automation.
- **Voice and context:** floating-ball recording, main-screen context and amendments to running tasks.
- **Learned skills:** parameterized workflows from eligible successful runs, current-page targeting, bounded recovery and model fallback.
- **Verification and read-only queries:** result evidence checks, short answers and supporting page quotes.
- **Human handoffs and delivery:** pause at login, CAPTCHA and payment boundaries, and transfer the original page to its owner.
- **Screen-lock protection:** queue new tasks while locked, pause and retain running tasks, then observe again and resume after unlocking and resolving conflicts.
- **History and metrics:** steps, model calls, timing, screenshots and isolation diagnostics.

## Demo and validation

[Five-segment demo (02:57)](docs/evidence/demo-path/step6-five-segments.mp4) · [Timestamps and limitations](docs/evidence/demo-path/VIDEO.md)

The video covers parallel input, skills, context, queries and coffee voice amendments. Live footage, still evidence and failures are labeled. It is not one continuous successful run.

Archived samples include calculator queries, three memo skill runs, a public hotel name copied to a memo, coffee specification amendments and a pre-payment handoff, and a calculator task completed after locking and unlocking. WeChat sending has not passed. Full shop-address transfer and continuous dual-screen footage of the successful coffee flow remain missing. See the [evidence index](docs/evidence/README.md), [Step6 status](docs/STEP6-STATUS.md) and [screen-lock report](docs/evidence/screen-lock/report.md).

## Quick start

### Requirements

- Android 10 / API 29 or later, USB debugging and computer authorization. The minimum SDK is not a compatibility certification.
- JDK 17 or compatible, Android SDK Platform 36 and ADB; use the repository's Gradle Wrapper.
- For browser deployment: Node.js/npm compatible with the bundled Vite version and a WebUSB-capable browser.
- Network access for initial dependencies/OCR assets and valid model-service API configuration for model tasks.

### Build and launch

From the repository root:

```sh
adb devices -l
./gradlew :android-shell:syncWebLauncherArtifacts
cd web-launcher
npm ci
npm run dev
```

Open the local address printed by the launcher and follow its steps to connect the phone, upload assets, install the controller and start the shell backend. Installation may require confirmation on the phone.

Open the Android controller, configure and test model connections, then submit a task. Installing the controller alone does not start the backend. Restart it after a phone reboot or backend termination. See the [user guide](docs/USER_GUIDE.en.md) for manual ADB deployment, OCR paths, logs and troubleshooting.

### Model configuration

Set each role's API Key, API Base and model name in the controller:

| Role | Purpose | Supported remote service |
| --- | --- | --- |
| executor | Goal interpretation and action planning | Official DeepSeek HTTPS API |
| verifier | Page evidence and result checks | Alibaba Cloud Bailian compatible API |
| intake | Voice interpretation and task routing | Alibaba Cloud Bailian compatible API |

Use connection tests and account capabilities to select model names. Remote hosts are currently restricted to `api.deepseek.com` and `dashscope.aliyuncs.com`; loopback test endpoints are also supported. Tasks may send UI text, screenshots, audio and context to configured services. OCR runs on the device. Keep real credentials and unsanitized evidence out of commits.

## Repository layout

```text
android-shell/    Shell backend, virtual displays, automation and RPC
controller-app/   Android UI, previews, floating ball and history
android-stubs/    Compile-time system interface declarations
paddler-ocr/      OCR libraries, models and runtime assets
web-launcher/    Browser deployment tool
scripts/         Regressions, device probes and recording tools
docs/            Guides, development design and validation evidence
```

## Development and testing

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
./gradlew :android-shell:lintDebug :controller-app:lintDebug
./scripts/test-interaction-regressions.sh
```

Build before running host regressions. Device probes and real-app acceptance tests are separate. The [development document](docs/DEVELOPMENT.md) maintains module responsibilities, state contracts, technical decisions, test baselines and remaining work.

Describe the purpose, scope and actual validation of a contribution. Update the development document when behavior changes, sanitize public evidence and preserve unverified results as unverified.

## Known limitations

- Full focus/IME isolation is not certified on the tested device. Experimental compatibility mode does not supply missing system capabilities or guarantee uninterrupted foreground use.
- Tasks pause while locked. Original UI sessions cannot be restored after process termination or reboot.
- App startup, UI accessibility, login and verification may block execution. CAPTCHA, payment and identity verification require the owner.
- Skills may need recovery or model fallback; existing samples do not establish an overall speedup.
- RPC defaults to `127.0.0.1:43110` without application-level authentication. Loopback binding does not isolate other local processes. Stored controller API configuration is not a dedicated credential vault.

See [Contributing](CONTRIBUTING.md), [Security](SECURITY.md) and the [GitHub publication checklist](docs/PUBLISHING.md) before submitting changes or publishing a repository.

## Documentation

- [User guide](docs/USER_GUIDE.en.md): installation, configuration, operation and troubleshooting.
- [Development document (中文)](docs/DEVELOPMENT.md): implementation, modules, constraints and maintenance plan.
- [Isolation boundaries](docs/ISOLATION.md): device capabilities and compatibility experiments.
- [Demo guide](docs/DEMO.md) and [evidence index](docs/evidence/README.md): measured scope, failures and provenance.
- [Derivative changes](docs/DERIVATIVE_CHANGES.md): upstream attribution and downstream scope.

## License and acknowledgements

Licensed under [Apache License 2.0](LICENSE). The upstream baseline is `611e5eb0e1c94befda3c17c97438912dfd30e8a8`. Preserve applicable attribution and notices; see [NOTICE](NOTICE). No upstream endorsement is implied.
