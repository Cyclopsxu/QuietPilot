# ShadowAuto — Derived Edition

English | [中文](README.zh-CN.md)

> This project is an independently modified distribution of [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto). Upstream authorship and contributor attribution are retained, and this branch continues under [Apache-2.0](LICENSE). The upstream baseline is `611e5eb`; see [Derivative Changes](docs/DERIVATIVE_CHANGES.md) and [NOTICE](NOTICE) for downstream additions and fixes.

ShadowAuto is an open-source phone-assistant prototype targeting Android 10 and later, with background automation, reusable skills, and voice task entry. It starts an AI-driven shell process through `adb shell` and `app_process`, launches real Android apps inside an isolated VirtualDisplay, and lets automation run quietly in the background while the physical screen remains usable.

ShadowAuto is not just a click script. It gives the AI a phone-assistant-style loop for understanding and operating the screen: the shell process can read the UiAutomation UI node tree for the virtual display, fall back to offline OCR when accessibility nodes are missing or incomplete, and inject touch, key, clipboard, and text input events into the target display.

The Chinese product name is **隐控**.

The capabilities below include inherited upstream features and downstream extensions. Their provenance is separated in [Derivative Changes](docs/DERIVATIVE_CHANGES.md).

## Implemented Features

| Feature | Current behavior |
| --- | --- |
| Background execution and live preview | Each task uses an isolated VirtualDisplay, UI node reading, Paddle Lite offline OCR fallback, and an H.264 screen stream |
| Automated actions and independent verification | Tap, input, search, scroll, and navigate; an executor plans actions while a visual verifier checks visible completion evidence |
| Task scheduling | Up to two active tasks; tasks for the same app queue, and foreground use of the target app triggers waiting or pausing before fresh observation and recovery |
| Skill learning and reuse | Record verified trajectories without manual handoffs, compile parameterized skills, locate controls from current UI features, and replay, disable, or delete skills |
| Skill recovery | Attempt up to three recovery actions on a page mismatch, then fall back to model exploration; repeatedly abandoned skills become suspect and stop matching |
| Voice floating ball | A 64dp touch area with curved-screen spacing and edge docking; hold for about one second, speak after the ready vibration, release to submit, or swipe up to cancel |
| Screen-aware requests | Optionally use the current screen's image and text to resolve references such as “this restaurant”; includes a screen-reading toggle, password masking, and temporary context expiry |
| Status capsule and manual handoffs | Show execution, queue, confirmation, and waiting states; notify the user at CAPTCHA, login, or payment boundaries; move the existing page to the main screen for human input, then return it to the background on request, with preview fallback |
| Query answers on the main screen | Read-only QUERY tasks independently extract and ground answers in visible quotes; the capsule shows the answer/source for eight seconds, with explicit Copy and Open-source-app actions, not-found and uncertainty states |
| Amend running tasks | A single voice intake can modify, add to, or cancel an active task; ambiguous requests ask for a target, queued/paused/manual-wait tasks resume with the effective goal, and amended trajectories are excluded from skill learning |
| Retained results and main-screen takeover | Retain results that need viewing; “Go view” moves the existing task, checks result-specific text such as its name/address, then releases the virtual display; failed transfers check rollback and offer a preview or an explicit recovery message |
| History and efficiency statistics | Inspect outcomes, evidence screenshots, model calls, duration, skill matches, and model/skill/rescue step sources |
| Disturbance metrics and input protection | Observe foreground, focus, IME, clipboard, and frame metrics, with same-app conflict handling and clipboard protection; unavailable measurements remain unknown |
| Per-role model configuration | Configure executor, verifier, and intake separately; keys are stored in the phone app's private preferences, with no centralized backend key distribution |

### Verification Status — October 22, 2026

- **Native human handoff:** Added temporary main-screen handling and an explicit return-to-background action. Build/Lint and roundtrip/failure probes passed. The actual floating entry moved the same Meituan restaurant task to the phone main screen while automation stayed paused; user interaction, return, and final result transfer are still being checked.

- **Local and emulator coverage:** Steps 2–5 are implemented, with build, Lint, logic-probe, mock integration, and native UI regression records. This does not establish compatibility with every real app.
- **New query/amendment features (5.8/5.9):** Dedicated emulator fixtures and local model mocks cover answer grounding, read-only blocking, skill replay, active-task amendments, chooser timeout, history, and native capsule interactions. Mate 40 Pro real-model smoke tests passed calculator queries, active-task modification/cancellation, and native answer display. Repeated-scroll recovery and OS-field validation passed three consecutive full-version phone queries, and the user confirmed vibration, recording, and voice amendment. Calculator tests passed default result retention, the actual Go-view entry, preview fallback after a real transfer failure, and retry to the main screen with the same task/result surviving virtual-display release. Broader app compatibility still needs testing. The older Settings baseline retains one failing typed-text UI assertion.
- **Mate 40 Pro / HarmonyOS 4.2.0.192:** ADB deployment, real model endpoints, calculator tasks, and Meituan restaurant selection have been tested. The preceding version passed retained-preview and same-task main-screen transfer checks, including page survival after virtual-display release. Backend rollback also has a separate native fault-injection record.
- **Latest handoff fixes (`d1df15e`):** Entity-text verification, explicit rollback states, semantic result retention, unified viewing entry points, and transfer progress are implemented and pass build/Lint/local boundary regressions. The later `7e9d205` passed real calculator task transfer and injected-failure rollback tests; the full Meituan flow after its CAPTCHA was not rerun in this round. Real speech accuracy, long-running background survival, and other vendors still need further validation.

## Task Flow

Type a goal or hold the floating ball to speak → optionally capture screen context → interpret and confirm → match a skill or explore with the model → independently verify → retain action results for “Go view” unless explicitly declined, or return query answers in the capsule. Manual boundaries pause for user handling; viewable results transfer their existing page to the main screen for continued use.

## Upstream Demo Video

This video is inherited from the original project and shows the upstream version, rather than all downstream additions.

<video src="shadowauto.mp4" controls="controls" style="max-width: 100%;"></video>

If the current page cannot play the embedded video, open [shadowauto.mp4](shadowauto.mp4).

## Upstream Technical Deep Dive

- [Android Virtual Display and AI Background Automation](docs/android-virtual-display-ai-automation.en.md)
- [Android 虚拟投屏与 AI 后台静默自动化实践](docs/android-virtual-display-ai-automation.md) (Chinese)

## Project Layout

- `android-shell`: shell-side automation process started by `app_process`. It owns VirtualDisplay creation, app launching, UI node reading, OCR fallback, input injection, clipboard operations, AI tool-call loop, JSON-RPC, logs, and H.264 virtual screen streaming.
- `controller-app`: Android controller with model configuration, text tasks, a voice floating ball, a status capsule, live preview and handoff, task history, and skill management.
- `web-launcher`: Svelte + TangoADB WebUSB launcher. It shows one launch button, lets the user select a device in the browser, uploads the shell APK, OCR files, and controller APK, then starts `app_process`.
- `android-stubs`: compile-only Android hidden API stubs used by the shell module.
- `scripts`: local test helpers.

## Requirements

- Android 10 or later; VirtualDisplay, hidden API, and task-transfer behavior must be verified for the vendor OS.
- ADB debugging enabled on the target device.
- Shell permission is enough; root is not required.
- JDK 17.
- Node.js and npm for the web launcher.
- Chrome or another WebUSB-capable browser for `web-launcher`.
- An executor chat-completions endpoint with streaming and tool calls; a verifier with image input; and, for voice entry, an intake model with audio and image input. Roles may use different providers.

## Build

Build the shell and controller app:

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
```

Sync the latest shell APK and OCR runtime files into the web launcher static directory:

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
```

Install the controller app:

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
```

## Start The Shell Process

### Option 1: Browser Launcher

Upstream online launcher: [https://android-notes.github.io/ShadowAuto/](https://android-notes.github.io/ShadowAuto/). This upstream-maintained site does not establish deployment of this branch's additions. Build and deploy this branch's local artifacts to use the derivative version.

Before connecting, prepare the phone:

- Enable Developer options and USB debugging.
- Some Xiaomi phones also require USB debugging (Security settings).
- On first connection, the phone may show an Allow USB debugging prompt; tap Allow on the phone.
- When installing the controller app, tap Allow install if the phone shows an install prompt.
- After installation, configure an API key in the ShadowAuto app before using phone automation.
- Developer option references: [Xiaomi](https://jingyan.baidu.com/article/ce436649ca6c877773afd3e2.html), [Huawei](https://jingyan.baidu.com/article/a378c960e87118b3282830bc.html), [OPPO](https://jingyan.baidu.com/article/cb5d6105b0936a005d2fe052.html), [VIVO](https://jingyan.baidu.com/article/335530da406f4358cb41c3b4.html).

You can also run it locally:

```sh
cd web-launcher
npm install
npm run dev
```

Open the printed local URL in Chrome, click **Start ShadowAuto Assistant**, select the Android device, and authorize ADB if Android asks.

If the browser reports that the device is already in use, close Android Studio and kill adb, then retry:

```sh
adb kill-server
```

### Option 2: Manual ADB

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
adb push web-launcher/static/silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell "rm -rf /data/local/tmp/shadowauto/ocr && mkdir -p /data/local/tmp/shadowauto/ocr"
adb push web-launcher/static/ocr/. /data/local/tmp/shadowauto/ocr/
adb shell "if [ -f /data/local/tmp/silent-auto.pid ]; then kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi"
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup sh -c 'exec app_process /system/bin com.silentauto.shell.Main --port=43110' >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

The OCR files are required for the `get_screen_ocr` tool. If you only push `silent-shell.apk`, normal automation can start, but OCR fallback will fail when the target page has no accessibility nodes.

If logcat shows `ClassNotFoundException: com.silentauto.shell.Main` after startup, `silent-shell.apk` was usually not pushed to the device. Check it first:

```sh
adb shell "ls -lh /data/local/tmp/silent-shell.apk"
```

When multiple devices are connected, add `-s <serial>` to every command, for example `adb -s emulator-5554 push ...`.

View shell logs:

```sh
adb logcat -v time -s ShadowAutoShell
```

Stop the shell process:

```sh
adb shell "kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid"
```

## Use The Android App

1. Start the shell through ADB and open ShadowAuto. Configure and test the executor, visual verifier, and optional voice intake roles with their API keys, URLs, and models.
2. Enter a goal, such as `Find a restaurant suitable for a date in Meituan. Browse only; do not book, order, or pay.`, then tap Run.
3. Follow the virtual screen and progress log. Use the plus button for additional tasks; the scheduler runs at most two concurrently.
4. For voice, enable overlay and microphone permissions through the guide. Hold the ball for about one second and **wait for the short vibration before speaking**. Release to submit or swipe up to cancel. Context capture can delay actual recording readiness.
5. High-confidence interpretations have a three-second cancellation window. Low-confidence tasks require confirmation and cancel after ten seconds without it. Screen context reading can be disabled in settings.
6. At CAPTCHA, login, or payment boundaries, open the handoff and handle it manually. Follow the main-screen prompt for protected pages.
7. Viewable results show “Go view.” The app attempts to move the existing page to the main screen. If a usable preview survives rollback, continue there, retry the transfer, or end viewing. Retained results still occupy task slots.
8. Use History for outcomes, screenshots, and statistics, and Skills to manage learned flows. Stop ends the current task; the top stop control stops all tasks.

## Core Principle

ShadowAuto uses a shell-owned ReAct-style automation loop:

1. The shell process starts under `adb shell` by `app_process`, enabling access to shell-level Android services and hidden APIs.
2. For each automation task, the shell creates a VirtualDisplay and launches the target Android app on that display.
3. The controller app sends a JSON-RPC `startTask` request to `127.0.0.1:43110` with the user goal and model config.
4. The shell asks the AI model to reason about the goal and use tools.
5. Tool calls perform real UI operations: dump UI layout, tap targets, tap coordinates, focus inputs, input text, select all, delete, paste clipboard, scroll, drag, press Back, press Enter/Search, wait, or finish.
6. UiAutomation observes the UI node tree for the virtual display. The shell can return a compact actionable layout or a full layout dump.
7. When UI nodes are empty, wrong, or insufficient, the AI can call `get_screen_ocr`; the shell uses Paddle Lite OCR to return visible text, confidence, and bounding boxes.
8. InputManager injects touch and key events into the target display instead of the main phone display.
9. The VirtualDisplay renders into a MediaCodec input surface. The shell streams H.264 config and samples to the Android app, which decodes them into a TextureView; progress logs are sent through the same local JSON-RPC event channel.
10. After the model declares completion, the visual verifier checks the result. Tasks requiring manual handling or viewing wait for handoff; delivery is followed by virtual-display cleanup and archival.

This architecture keeps the automated app off the main display while still allowing the user to monitor progress from the controller app.

## Paddle Lite Offline OCR

The `paddler-ocr` module contains the PaddleOCR Android integration for visual fallback on self-rendered or accessibility-opaque pages. The shell process calls this module directly; the controller app does not contain OCR code. OCR is enabled by default; build or copy its runtime files with:

```sh
./gradlew :paddler-ocr:preparePaddleLiteOcr
./gradlew :android-shell:copyPaddleLiteOcrRuntime
```

The prepare task downloads PaddleLite v2.10, OpenCV Android SDK, the PP-OCRv2 Chinese mobile models, and labels. Generated files live under `paddler-ocr/PaddleLite`, `paddler-ocr/OpenCV`, and `paddler-ocr/PaddleOcrAssets`; they are ignored by git.

The Java entry point is `com.silentauto.paddlerocr.PaddleOcrEngine`. After `init()`, call `recognize(Bitmap)` to get text, confidence, and bounding boxes. Runtime files are copied to `android-shell/build/ocr-runtime` and uploaded to `/data/local/tmp/shadowauto/ocr` by the web launcher.

## JSON-RPC Overview

The shell listens on loopback only:

```text
127.0.0.1:43110
```

Main methods:

- `startTask` / `stopTask` / `listTasks`: start, stop, and inspect tasks. Start supports per-role `models` and retains compatibility with `apiKey`, `apiBase`, and `model`.
- `submitVoiceTask` / `confirmTask`: submit one voice interpretation and confirm or cancel a new action task.
- `amendTask {taskId, kind, text}`: queue `modify`, `add`, or `cancel` on an active task.
- `resolveVoice {voiceId, taskId}`: choose an offered amendment target or `"new"`; an unanswered choice becomes a new task after ten seconds.
- `captureContext` / `discardContext` / `setContextReading`: capture, discard, or disable screen context.
- `resumeTask` / `injectTouch` / `takeResultOnMain`: manual handoff, preview touch input, and result transfer.
- `listHistory` / `getTask`: task history and detail.
- `listSkills` / `setSkillStatus` / `deleteSkill`: list, change status, and delete skills.
- `status`: returns current shell status.
- `logs`: returns recent logs.
- `ping`: health check.

The server is intentionally unauthenticated because it binds to local loopback and is designed for a local controller app. Do not expose this port externally.


Query API: `startTask` accepts `type="query"` (default `action`), with `ANSWERED` / `NOT_FOUND` outcomes. Status notifications use `event="status"`, leaving `type` for task type; the controller still accepts legacy `type="status"` events. `openQuerySource` opens the source app only after an explicit user action.

## Notes And Limitations

- The AI provider receives the task goal and UI context needed for automation. Do not use it with sensitive content unless you trust the configured provider.
- Some protected pages may appear black in screen streaming because of Android security restrictions, such as payment password screens.
- Same-app conflict handling and clipboard protection are implemented, but disturbance-free behavior is not guaranteed across vendors and apps. Moving an automated app to the main screen can pause it or require fresh observation and recovery.
- Retained pages live in the running virtual display. Stopping the shell process or rebooting the phone loses that live page; a historical screenshot is not a resumable session.
- Voice recording requires microphone and overlay permissions. Use the ready vibration as the recording cue; audio resumption in other apps depends on their own implementation.
- Hidden APIs and shell permissions can vary between Android versions and vendors.
- The browser launcher needs exclusive WebUSB access to the device. Android Studio or a running adb server may block it.

## Development Test

Build and static checks:

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug :android-shell:lintDebug :controller-app:lintDebug
```

With a dedicated test device or emulator connected, run the basic mock test (it operates the device):

```sh
node scripts/emulator-mock-ai-test.mjs
```

Phase tasks, guides, reports, and regression artifacts in the local workspace live in the sibling `../docs/` directory (`tasks/`, `guides/`, `reports/`, `validation/`). This directory is outside this Git repository and is not included in a standalone clone.

## License

This derivative continues under the upstream [Apache License 2.0](LICENSE), retaining the original license and applicable attribution. Original project: [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto). See [NOTICE](NOTICE) and [Derivative Changes](docs/DERIVATIVE_CHANGES.md) for provenance and the scope of modifications.
