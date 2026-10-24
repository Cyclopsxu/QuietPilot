# 隐控 / ShadowAuto — derivative edition

English | [中文完整手册](README.zh-CN.md)

An Android automation prototype that runs real apps on task-owned virtual displays, plans actions with language models, and exposes previews, voice input, human handoffs, result delivery, reusable skills, and task history through an Android controller.

**Documentation baseline: 2026-10-24.** Different apps can run concurrently on the same device; foreground use of the same app takes priority. Creating a virtual display does not establish focus isolation. Experimental compatibility mode is not a certification of uninterrupted background operation.

This is an independent derivative of [android-notes/ShadowAuto](https://github.com/android-notes/ShadowAuto), based on commit `611e5eb0e1c94befda3c17c97438912dfd30e8a8`, under [Apache-2.0](LICENSE). Upstream supplies the shell/app_process architecture, VirtualDisplay automation, UI/OCR interaction, display-targeted input, H.264 preview, Android controller, and browser launcher. This branch extends verification, handoffs, scheduling, skills, voice/context, history, metrics, retained results, and device compatibility. See [NOTICE](NOTICE) and [derivative changes](docs/DERIVATIVE_CHANGES.md). No upstream endorsement is implied.

The [Chinese manual](README.zh-CN.md) contains the full operational reference, including field-level diagnostics, exact memo-proof formatting, source navigation, and troubleshooting. This English guide covers the architecture, setup, implementation, interfaces, and evidence boundaries.

## Reviewer overview

Operate real Android apps on a background virtual display while the owner uses the main screen. This is a prototype with the measured scope below.

**Demo:** [five-segment runbook and recording procedure](docs/DEMO.md). The full downstream demo has not been recorded; its real URL and timestamps remain pending. The upstream video is not downstream validation.

**Design choices:** a virtual display keeps the task on the same phone and app/account environment, and allows handing the original page back to its owner. A cloud or second phone adds account/device cost; app clones depend on vendors and separate data; main-screen accessibility automation occupies the user's UI; iPhone does not expose the shell/display capabilities used here. Apache-2.0 ShadowAuto supplies the shell, UI/OCR, input and streaming foundation. Attribution and downstream scope are in [Derivative Changes](docs/DERIVATIVE_CHANGES.md).

| Downstream addition | Source entry |
| --- | --- |
| Move the original task between displays for assistance and delivery | [handoff](android-shell/src/main/java/com/silentauto/shell/ext/handoff/) |
| Learn parameterized skills, replay and recover | [skill](android-shell/src/main/java/com/silentauto/shell/ext/skill/) |
| Independent verification by another provider; read-only grounded answers | [verify](android-shell/src/main/java/com/silentauto/shell/ext/verify/), [query](android-shell/src/main/java/com/silentauto/shell/ext/query/) |
| Voice amendments during execution and current-screen context | [amend](android-shell/src/main/java/com/silentauto/shell/ext/amend/), [context](controller-app/src/main/java/com/silentauto/controller/ext/context/) |
| Mirror capture, bounded recovery and repetition detection | [CaptureMirror](android-shell/src/main/java/com/silentauto/shell/CaptureMirror.java), [guard](android-shell/src/main/java/com/silentauto/shell/ext/guard/) |
| Disturbance metrics and timestamped raw samples | [metrics](android-shell/src/main/java/com/silentauto/shell/ext/metrics/) |

| Mate 40 Pro measurements, Oct 23–24, 2026 | Result and scope |
| --- | --- |
| Repeated capture and UI reads | 60/60 captures, no missing roots or source-surface swaps; three page types |
| Calculate 12×13 / public place address in Amap | ANSWERED in 6 / 4 steps, grounded evidence; Amap search results only |
| Clear-then-calculate task | Original 25-step failure; a 7-step ANSWERED 156 retest and a separate 16-step repetition failure are both retained |
| Memo page delivery | VERIFIED; same root task, unchanged body hash, virtual display released |
| Memo skill exploration / reuse / injected deviation | All VERIFIED, 8 / 10 / 13 steps; reuse calls 10→8 but time 27.7→49.4s, with model fallback; [report](docs/evidence/skills/report.md) |
| Meituan voice amendment and delivery | Large cup VERIFIED in 9 steps and delivered; another real voice task reached payment boundary in 20 steps and moved to main (WAITING_USER); continuous video missing; [evidence](docs/evidence/demo-path/README.md) |
| WeChat sending | App exited; no message sent |

[Repository evidence](docs/evidence/README.md) includes failures. No-focus-change touches, key gating, accessibility text and clipboard restoration mitigate interference. This phone lacks confirmed native independent focus/local IME; compatibility mode is not certified isolation. Three new rounds sampled zero events and the owner reported normal input, but the first recording has a gap and the owner confirmed that matching text on the main screen was typed manually. This is not proof against cross-display text effects. Historical tests also included brief unfocused/inactive-IME samples; see [capability boundaries](docs/ISOLATION.md) and [acceptance status](docs/evidence/isolation/report.md).

**Models and deployment:** DeepSeek executes, Alibaba Cloud vision verifies independently, and Omni handles intake. Mainland endpoint restrictions limit data routing; service compliance and personal-data consent still matter. Current deployment uses ADB/app_process. Shizuku plus wireless debugging is a proposed startup path, not an implemented user flow; commercial isolation needs vendor cooperation and platform/privacy review.

**Coding Agent collaboration:** product/design documents → task specification → implementation → phone measurement → correction. Actual iterations: unstable UI after surface swaps → independent capture mirror; successful accessibility clicks without activation in Amap/Meituan → re-resolve targets and touch their bounds; whole-goal text entered into city fields → remove broad correction; repeated clearing after reaching 156 → repetition detection with independent verification. [Reports](docs/evidence/capture-stability/report.md) retain the limits of these observations.

**Limits:** full isolation needs three human/video rounds; some apps and login/payment boundaries require the owner; memo skills still fall back to model planning; WeChat/voice paths lack complete phone evidence; loopback RPC is unauthenticated and can be called by other local apps (planned 32-byte token); other Android versions and long-running stability remain untested.

## Contents

- [Implemented capabilities](#implemented-capabilities)
- [Architecture](#architecture)
- [Build and deployment](#build-and-deployment)
- [Model configuration and data handling](#model-configuration-and-data-handling)
- [Using the controller](#using-the-controller)
- [Execution and recovery](#execution-and-recovery)
- [Isolation and scheduling](#isolation-and-scheduling)
- [Verification and handoffs](#verification-and-handoffs)
- [Voice, context, and amendments](#voice-context-and-amendments)
- [Reusable skills](#reusable-skills)
- [Read-only queries](#read-only-queries)
- [History and metrics](#history-and-metrics)
- [RPC and connection reliability](#rpc-and-connection-reliability)
- [Development and validation](#development-and-validation)
- [Current results and limitations](#current-results-and-limitations)
- [Troubleshooting and source map](#troubleshooting-and-source-map)

## Implemented capabilities

| Capability | Behavior |
| --- | --- |
| Real-app execution | A task-owned VirtualDisplay hosts the target app; the controller receives H.264 previews. |
| Model roles | Executor plans actions, verifier checks evidence independently, intake interprets voice. |
| Scheduling | At most two concurrent tasks; same-app work is serialized and foreground use takes priority. |
| Actions | Current-node taps, targeted touch, text editing, scrolling, dragging, back/key operations, waits, handoffs, and completion requests. |
| Queries | Restricted browsing/search, short answers, page quotations, independent extraction, source opening on user request. |
| Voice | A 64dp floating ball, approximately one-second hold, readiness vibration, release to submit, swipe up to cancel. |
| Context | Optional pre-recording screen context, password-node masking, protected-screen handling, 120-second expiry. |
| Amendments | Modify, add, or cancel active work, with explicit task routing when ambiguous. |
| Human processing | Move the original task to the main display, let the user act, then return it to the background. |
| Result delivery | Retain actionable results; “Go view” moves the original task and verifies identity before releasing its virtual display. |
| Fallback | Validate transfer and rollback separately; preserve the preview when available. |
| Skills | Compile eligible successful trajectories into parameterized workflows; relocate targets in current UI. |
| Skill rescue | Up to three recovery actions; abandon to model planning, mark repeatedly abandoned skills suspect. |
| Capture stability | Separate layer-stack capture output; no source-surface switching in mirror mode. |
| UI stability | Read diagnostics, bounded reconnection/recovery, stale-target rejection. |
| Observability | History, evidence, action sources, role calls, timings, capture counters, and disturbance observations. |
| Connection reliability | Avoid loopback self-connections, unsafe replay, old-reader cleanup races, and global blocking by slow clients. |

These are implemented mechanisms, not a promise that every app, task, model, or Android build works reliably. See the validation scope below.

## Architecture

```text
Text / floating voice control
             │
Android controller: configuration, previews, handoffs, history, skills
             │ loopback TCP, newline-delimited JSON-RPC
Android shell process: adb shell + app_process
  ├─ scheduler, lifecycle, conflict and focus guards
  ├─ current UI → model/skill → guarded action → observe again
  ├─ VirtualDisplay → actual third-party app
  ├─ UiAutomation, OCR, task-display screenshots
  └─ independent verification → delivery/answer → archive
             │
             └─ configured external model services over HTTPS
```

| Module | Purpose |
| --- | --- |
| `android-shell` | Shell execution, system-service bridges, automation engine, local RPC. |
| `controller-app` | Installed Android UI, microphone input, configuration, preview and handoff controls. |
| `android-stubs` | Compile-only declarations for hidden APIs. |
| `paddler-ocr` | Paddle Lite OCR wrapper and runtime preparation. |
| `web-launcher` | Svelte/Vite deployment UI using TangoADB/WebUSB. |
| `scripts` | Host regressions, device probes, mock-model testing. |

Install the controller APK normally. The shell APK is loaded as an `app_process` classpath by ADB shell; installing the controller alone neither grants shell privileges nor starts that process. Root is not required, but authorized ADB shell and compatible system APIs are required.

There is no hosted customer-account, API-key distribution, billing, or model-proxy backend. The execution backend is local to the phone; model inference is external. Local OCR does not make the complete system offline.

## Build and deployment

### Requirements

- Android 10/API 29 is the minimum SDK, not a compatibility guarantee.
- JDK 17, Android SDK Platform 36, a configured SDK path, and ADB.
- USB debugging enabled, a working data cable, and accepted computer authorization.
- Network access for initial dependencies/OCR assets and for model requests.
- A Node.js version compatible with the launcher's Vite version, npm, and a WebUSB-capable browser when using browser deployment.

Check the device:

```sh
adb devices -l
```

It must show `device`, not `unauthorized`. With multiple devices, use `adb -s <serial>` throughout. Huawei installers can require an on-device confirmation or lock-screen authentication; enter credentials only on the phone.

### Build artifacts

From the repository root:

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
./gradlew :android-shell:syncWebLauncherArtifacts
```

| Output | Use |
| --- | --- |
| `controller-app/build/outputs/apk/debug/controller-app-debug.apk` | Installed controller. |
| `android-shell/build/outputs/apk/debug/android-shell-debug.apk` | Shell code. |
| `android-shell/build/ocr-runtime/` | OCR libraries, models, dictionary. |
| `web-launcher/static/silent-shell.apk` | Shell artifact staged for deployment. |
| `web-launcher/static/controller-app.apk` | Controller staged for deployment. |
| `web-launcher/static/ocr/` | OCR upload directory. |

OCR is enabled by default. Its preparation downloads PaddleLite v2.10, OpenCV Android SDK, PP-OCRv2 Chinese mobile models, and a dictionary. Generated dependency directories are not committed. Standalone preparation:

```sh
./gradlew :paddler-ocr:preparePaddleLiteOcr
./gradlew :android-shell:copyPaddleLiteOcrRuntime
```

### ADB installation and startup

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
adb push web-launcher/static/silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell 'mkdir -p /data/local/tmp/shadowauto/ocr'
adb push web-launcher/static/ocr/. /data/local/tmp/shadowauto/ocr/
```

An update with the same package/signature normally preserves controller configuration. Uninstalling or clearing app data is a separate operation.

Restarting the backend interrupts existing tasks and live retained pages. Finish receiving results first:

```sh
adb shell 'if [ -f /data/local/tmp/silent-auto.pid ]; then kill "$(cat /data/local/tmp/silent-auto.pid)" 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi'
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup sh -c 'exec app_process /system/bin com.silentauto.shell.Main --port=43110' >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

Open the controller, confirm connection, and configure models. Rebooting or killing the shell requires starting it again. Controller foreground services do not guarantee indefinite shell lifetime.

Diagnostics:

```sh
adb shell cat /data/local/tmp/silent-auto.log
adb logcat -v time -s ShadowAutoShell
```

To stop the backend, run the PID-based stop command above without the subsequent startup command.

### Browser deployment

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
cd web-launcher
npm install
npm run dev
```

Open the Vite URL and follow the USB/upload/install/start workflow. Browser WebUSB and the ADB server may compete for the device. Only use `adb kill-server` to resolve an actual conflict; it interrupts other local ADB sessions.

The [upstream hosted launcher](https://android-notes.github.io/ShadowAuto/) distributes upstream resources. Use locally built artifacts to deploy this branch's changes.

### Experimental compatibility mode

The default gate requires independent focus, no-focus-stealing configuration, and local IME capability. Insufficient capability blocks background work.

For explicitly accepted, controlled experiments only, append this to the shell startup arguments after `--port=43110`:

```text
--allow-unverified-isolation=true
```

It bypasses the gate; it does not add missing isolation capabilities or grant additional privileges. Status/history retain the unverified designation. Do not enable it as a silent production fallback.

## Model configuration and data handling

Configure each role in the controller and test connectivity:

| Role | Purpose | Current source defaults |
| --- | --- | --- |
| executor | Planning, app selection, skill matching/compilation. | DeepSeek, `deepseek-flash`. |
| verifier | Visual evidence and independent result/answer checking. | Alibaba compatible endpoint, `qwen3-vl-plus`. |
| intake | Voice intent and routing. | Alibaba compatible endpoint, `qwen3.8-omni-flash`. |

Checked on 2026-10-24: [DeepSeek official documentation](https://api-docs.deepseek.com/api/responses) maps `deepseek-flash` to DeepSeek-V4.1-Flash-1017. Aliases may change. The controller connection test displays the response `model`; an alias is not proof of a specific weight snapshot.


These are code defaults, not guarantees of current provider availability, pricing, or suitability. Select models with the required image/audio capabilities; a successful basic connection does not test every capability.

Remote endpoint validation currently allows HTTPS on `api.deepseek.com` and `dashscope.aliyuncs.com`, plus loopback HTTP/HTTPS for local testing. Arbitrary compatible proxies are not accepted. Default bases:

```text
https://api.deepseek.com/v1
https://dashscope.aliyuncs.com/compatible-mode/v1
```

URLs cannot contain user information, query parameters, or fragments. Per-task `models` profiles override role settings; legacy `apiKey`, `apiBase`, and `model` fields remain supported.

Shell configuration also accepts `--name=value` parameters and environment variables:

| Setting | Parameter / environment |
| --- | --- |
| Port | `--port` / `SILENT_AUTO_PORT` |
| Role key | `--executor-api-key` / `SHADOWAUTO_EXECUTOR_API_KEY`; likewise verifier/intake. |
| Role base | `--executor-api-base` / `SHADOWAUTO_EXECUTOR_API_BASE`; likewise other roles. |
| Role model | `--executor-model` / `SHADOWAUTO_EXECUTOR_MODEL`; likewise other roles. |
| Provider defaults | `DEEPSEEK_API_KEY`, `DASHSCOPE_API_KEY`. |
| Legacy common model | `--api-key`, `--api-base`, `--model` / `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`. |

Role overrides are applied over initial defaults or legacy common configuration. RPC profiles may override them further. Exporting a variable on a Mac does not automatically transfer it into `adb shell` or update controller preferences. Command-line secrets can appear in process information.

Controller settings use private `ai_config` SharedPreferences with `<role>.apiKey`, `.apiBase`, and `.model`. This is not a Keystore-encrypted credential store. No automatic key distribution or APK-time environment-file import exists.

Configured providers may receive task text, UI content, screenshots, audio, and captured context as needed. Do not commit real credentials or log complete RPC model profiles. Shell evidence in `/data/local/tmp` is not controller-private storage.

## Using the controller

1. Start the backend and connect the controller.
2. Configure executor/verifier, plus intake for voice.
3. Submit a clear goal specifying the app, object, restrictions, and desired delivery.
4. Monitor steps and preview; add tasks within scheduler limits or stop current/all work.
5. Complete human boundaries yourself and receive retained results when prompted.

Useful short test goals include calculating 12×13, reading a museum address in Amap without starting navigation, or saving supplied text into a test memo. Start with short tasks before cross-app research and long documents.

“Go handle” means the task needs human intervention. “Go view” means a retained result is ready for transfer. Query capsules show an answer and quotation, with user-triggered copy/source actions. Archived screenshots are evidence, not resumable live sessions.

## Execution and recovery

`AutomationEngine` owns lifecycle and scheduling. `ActionDecision` describes one action and `ActionExecutor` performs it. The loop checks guards/amendments, reads fresh UI, chooses a skill/model action, validates it, executes, observes again, records the trajectory, and independently verifies completion.

The current main-loop limit is 25 steps; observation-tool requests per decision are capped at four. Reaching a limit is not success. Action sources distinguish `model`, `skill`, `rescue`, and `query_recovery`.

`ObservedTarget` checks current path, class, resource ID, label, and bounds. Connection-generation changes invalidate old plans. Some Amap/Meituan accessibility clicks acknowledge without activating the page, so those paths use fresh-target bound touches. Search submission prefers an actual visible button over potentially focus-stealing key injection.

### Capture and UI health

VirtualDisplay renders into a MediaCodec surface. `CaptureMirror` creates a separate non-secure compositor output from the owned secondary display's layer stack, keeping that original encoder surface attached. It rejects primary/invalid display sources, continuously drains a three-buffer RGBA ImageReader, retains one current image, and copies only when requested. Release closes images, listener, display, and drain thread.

If mirror creation is unsupported, the recorded mode can fall back to `legacy_surface_swap`. If an established mirror subsequently fails, capture returns failure: it does not switch the source surface or return an image from a closed mirror. A valid latest frame can be reused for a static page.

`isolation.capture` records mode, fallback, requests, misses, source-surface switches, and frames. Mirror mode is not a focus-isolation certificate.

`UiBridge` serializes entry points, records connection generations, clears accessibility caches on empty-window reads, and retries after roughly 180ms. Diagnostics distinguish missing roots, incomplete traversal, null children, genuine leaf pages, and read latency. Native ANR detection uses actual system dialog resource IDs rather than matching the word “unresponsive” in arbitrary text.

Recovery is bounded: two missing reads wait; the third triggers one reconnect; persistent failure can trigger one app restart only for queries; further failure requests human handling. Editing tasks are not restarted to recover unsaved work. A page moved to the main display is not repeatedly relaunched in the background.

On-device Paddle Lite OCR supplies text, confidence, and bounds when accessibility is insufficient. OCR coordinates remain subject to action and query guards.

## Isolation and scheduling

The chosen virtual-display flags, candidate index, focus requests, and IME policy are recorded. `configurationAvailable`, `behaviorValidated`, `experimentalOverride`, and `backgroundAllowed` are separate facts. Accepted configuration does not automatically mark behavior validated.

Secondary-display touches request `FLAG_NO_FOCUS_CHANGE` (64). `BackgroundKeyPolicy` blocks risky native key events without confirmed isolation. These measures reduce disturbance but do not add missing vendor privileges.

`ForegroundWatcher`, `MainAppConflictGuard`, and `BackgroundFocusGuard` prioritize user foreground work. Same-app tasks serialize/pause; resumption requires fresh observation. Unexpected focus changes latch protection instead of immediately continuing after one normal sample.

There are at most two concurrent tasks, not two independent sessions/accounts within one app. Waiting handoffs and retained results can consume resources. Clipboard snapshots/restoration and digest observations do not prove the clipboard was never temporarily touched.

## Verification and handoffs

A model's completion declaration is checked by `ResultVerifier` against the goal and page evidence. Failure leads to further observation, correction, or human handling.

Deterministic long-document `DocumentProof` currently targets Huawei Notepad (`com.huawei.notepad`) and requires a specific goal format. It checks exact title/body through resource IDs, then a saved-list transition and reopening with the same content. Generic goals and other memo apps do not automatically obtain this exact proof. Use the exact Chinese markers below; do not repeat the body delimiter within the body:

```text
把以下内容写入华为备忘录，保存并重新打开核对。
标题：测试｜参观信息
完整正文开始：
The exact complete body, including final lines and source links.
完整正文结束。
```

Visual checking is still limited by available evidence and model judgment; it is not a transaction proof for every app database.

Human boundaries pause the task. An explicit user action moves the original app task to the main display for login/verification, then returns it to the virtual display for fresh observation. Default handoff timeout management uses a ten-minute budget, distinguishing main-screen manual processing from subsequent waiting.

For retained results, “Go view” moves the original task, checks package/page/entity identity, and releases the virtual display only after success. Transfer failure and rollback are checked separately. A usable preview remains available for retry or ending the view. Launching a replacement home page is not successful original-page delivery.

Stop/cancel must coordinate with transfers, waits, and cleanup. Killing the process or rebooting can destroy live pages; history does not restore them.

## Voice, context, and amendments

The floating ball is 64dp with a default 28dp edge margin and gesture-inset consideration. Hold threshold is 1000ms; an approximately 80ms vibration follows actual recording readiness. Swipe up about 80dp to cancel, release to submit, or drag before recording to reposition.

Recording uses 16kHz mono 16-bit PCM in WAV with an approximately 30-second safety limit. Transient audio focus is acquired and released; other apps control their own audio resumption.

Context capture starts before recording/focus changes to reduce capturing the assistant's own UI. Context expires after 120 seconds, can be discarded, and can be disabled. Password nodes are masked and protected screens have checks; this does not remove every possible private detail from normal page text.

High-confidence action intents offer a three-second cancellation window. Lower-confidence actions require confirmation and cancel after ten seconds without it. Ambiguous amendment routing asks for a task; after ten seconds without selection it becomes a new task. Queries have their own routing branch.

`modify`, `add`, and `cancel` are queued at controlled execution points, updating the effective goal while preserving amendment history. Amended tasks are excluded from automatic new-skill compilation.

## Reusable skills

Eligible verified trajectories compile into parameterized workflows with metadata, references, actions, and page fingerprints. Failed, amended, or human-handoff trajectories do not become ordinary successful skills. Temporary-file publication/atomic replacement reduces partially written records.

Matching considers active skills of the same task type, validates current-task parameters, and rejects ambiguous IDs. Execution relocates targets in the current UI rather than replaying old target indices or coordinates.

Deviations trigger at most three rescue actions. Unsuccessful rescue abandons the skill for model planning. Three consecutive abandonments mark a skill `suspect`. Users can enable, disable, or delete skills; user-disabled skills remain disabled during ordinary learning. Successful fresh exploration can rehabilitate suspect skills.

Statistics include uses, successes, mean calls/time, and comparison with initial exploration. These are sample statistics, not general speed guarantees. Query skills store retrieval procedures, not reusable factual answers; each run rechecks current evidence.

## Read-only queries

`startTask.type` is `action` by default or `query`. Query completion uses independently checked `report_answer`/`report_not_found` paths, producing `ANSWERED` or `NOT_FOUND` where supported by evidence.

`ReadOnlyGuard` permits browsing/search/scrolling and restricts ordering, payment, booking, sending, sharing/favoriting, and navigation start. Input must be a recognized current search field or role-bound Amap route endpoint. Narrow Amap anonymous-search and Meituan MSC city-picker exceptions do not generally unlock address or transaction controls. This is not a formally verified adversarial sandbox.

Candidate short answers are limited to 60 Unicode code points and require a continuous page quotation. `AnswerVerifier` extracts independently before comparison. Version queries distinguish OS version from build/kernel numbers. Stalled queries can use controlled screenshot/OCR recovery, not hardcoded answers.

`NOT_FOUND` describes this run's search scope, not universal absence. The answer capsule lasts about eight seconds. Copy and source opening require user action; opening the source app does not promise restoration of the same query virtual-display session.

## History and metrics

Lifecycle statuses are `QUEUED`, `RUNNING`, `PAUSED`, `WAITING_USER`, `VERIFIED`, `ANSWERED`, `NOT_FOUND`, `FAILED`, `CANCELLED`, and `HANDOFF_TIMEOUT`. Tests needing final evidence should wait for `finalized`, not merely a terminal notification before archival has finished.

| Phone path | Data |
| --- | --- |
| Controller private `ai_config` preferences | Role credentials/configuration. |
| `/data/local/tmp/silent-shell.apk` | Shell runtime. |
| `/data/local/tmp/silent-auto.pid`, `.log` | Process marker and startup log. |
| `/data/local/tmp/shadowauto/ocr/` | OCR runtime. |
| `/data/local/tmp/shadowauto/skills/` | Per-package skill JSON. |
| `/data/local/tmp/shadowauto/trajectories/` | Execution trajectories. |
| `/data/local/tmp/shadowauto/history/` | Task archives. |
| `/data/local/tmp/shadowauto/evidence/` | Screenshots. |
| `/data/local/tmp/shadowauto/context/`, `no-screen` | Temporary context and context-disabled setting. |

There is no cloud synchronization or cross-reboot restoration of live UI sessions. Do not erase shell data as part of an ordinary controller update.

Metrics include model calls, elapsed time, action sources, skill comparisons, capture stats, foreground switches, focus, IME, clipboard digests, and scoped gfxinfo. User-handoff attribution is correlation, not causal proof. Preserve raw counters. Desktop jank cannot represent continuous scrolling in another app; unavailable metrics are not zero or passing results.

## RPC and connection reliability

The default endpoint is `127.0.0.1:43110`, a TCP stream of newline-delimited JSON-RPC, not an HTTP REST endpoint. Correlate responses by ID; unsolicited events can arrive between them.

| Methods | Purpose |
| --- | --- |
| `ping`, `status`, `logs` | Health and diagnostics. |
| `subscribeStatus` | Status-only subscription without video. |
| `startTask`, `stopTask`, `listTasks` | Task lifecycle. |
| `dumpUi`, `forceStart` | UI diagnostics and explicit task control. |
| `submitVoiceTask`, `confirmTask`, `resolveVoice` | Voice intent, confirmation, routing. |
| `amendTask` | Task ID, modify/add/cancel kind, text. |
| `captureContext`, `discardContext`, `setContextReading` | Context lifecycle. |
| `resumeTask`, `injectTouch` | Human response and preview interaction. |
| `openTaskOnMain`, `takeManualOnMain`, `takeResultOnMain` | Main-display viewing/manual/result transfer. |
| `noteUserHandoff`, `openQuerySource` | Attribution and user-triggered source opening. |
| `listHistory`, `getTask` | History and evidence. |
| `listSkills`, `setSkillStatus`, `deleteSkill` | Skill management. |

Example health request:

```json
{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
```

`startTask.params` includes `taskId`, `type`, `goal`, and `models`, whose role objects contain `apiBase`, `apiKey`, and `model`. See the Chinese manual for a placeholder request and `RpcServer`/`AutomationEngine` for authoritative parameters. Never store actual keys in shared request examples.

Status events use `event="status"`; `type` remains action/query. The controller accepts legacy `type="status"` events.

`RpcConnection` binds an ephemeral source port before connecting and rejects the server-port collision, with up to eight attempts. `RpcClient` does not replay potentially delivered mutations after write failure; old readers only clean up their own socket. Overlay requests use correlated responses and reconnect. Each server client has an independent 64-entry outbound queue; slow readers disconnect instead of blocking task execution.

There is no application-level RPC authentication. Loopback binding does not establish isolation from all other local processes. Do not expose this port to untrusted networks.

## Development and validation

Build and static checks:

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug :android-shell:lintDebug :controller-app:lintDebug
```

After Android compilation resolves the local Gson cache:

```sh
./scripts/test-interaction-regressions.sh
```

The six groups are connection, UI recovery, interaction guards, transfer, query, and RPC output regressions. Host results do not validate vendor system APIs.

`CaptureMirrorDeviceRegression.java` and `TouchFlagsDeviceRegression.java` require Android runtime deployment; they are not run by the host script. A separate mock-model test operates on a dedicated connected emulator/device:

```sh
node scripts/emulator-mock-ai-test.mjs
```

Its default serial is `emulator-5554`; override with `ADB_SERIAL` when appropriate. It changes device state. Mock responses do not substitute for real-app/model tests.

Recommended sequence: connection/refusal checks; repeated capture→UI reads; short finalized queries with screenshots; human main-display transfer/return; forced transfer/capture/root failures and exit; real parallel Chinese composition/scrolling; only then cross-app research and long documents. Obtain user authorization before recording/exporting primary-screen evidence.

## Current results and limitations

Primary device: Mate 40 Pro NOH-AN01, HarmonyOS 4.2.0.192, API 31. This is not a general device-support list.

### Latest short tests, 2026-10-24

Controller connection fix installed; mirror capture deployed; build/Lint and six host regression groups passed. The installed controller separately reconnected to the real backend.

| Test | Result | Scope |
| --- | --- | --- |
| Calculator capture→UI | 20/20, no unreadable roots | Median/max UI read 7/170ms. |
| Meituan public-page capture→UI | 20/20, no unreadable roots | 9/15ms; verification dialog closed. |
| Amap home capture→UI | 20/20, no unreadable roots | 33/393ms. |
| Capture fault/release | Three rounds passed | Closed mirror returns failure, source surface unchanged, UI readable. |
| Simple 12×13 query | ANSWERED, 156, six steps | Screenshot and independent extraction checked. |
| Museum address query | ANSWERED, 姑苏区东北街204号, four steps | Correct search-result entry; no separate detail-page visit. |
| Initial “clear first, then calculate” | **FAILED after 25 steps** | Reached 156, then repeatedly cleared; planning issue remains. |

All 60 repeated captures used `layer_stack_mirror`, with zero misses and source-surface switches. The two successful queries each had one successful capture. Formal-task foreground/focus/IME/clipboard observations were zero; jank was unavailable, and continuous Chinese composition was not tested in this round. This is not a zero-disturbance certification.

Closing Meituan's verification dialog did not verify merchants, city selection, or hotels. An initial temporary test loading the installed APK was killed and not counted as passing; a subsequent Android test compiled from the same connection source passed, separately from actual installed-controller reconnection.

Earlier rounds exercised voice/vibration/amendments, human main-screen processing and return, retained-page delivery, full Huawei memo content/list/reopen proof, and portions of multi-leg route research. They do not establish a completed latest-version travel/merchant acceptance test. Prior user feedback confirmed normal Chinese candidates/scrolling, but rotation/reopening changed window identities and raw focus/IME samples were not all zero.

Remaining issues:

1. This Huawei fallback configuration lacks confirmed complete focus/IME isolation. Explicit experimental mode remains necessary; no-focus touch and key guards do not turn it into certified isolation.
2. Planning loops and repeated clearing/waiting still occur; the failed short run is preserved.
3. Meituan login/channel verification can recur. Merchant/hotel data not actually checked must remain unverified.
4. Historical Amap ANRs involved vendor window/layout paths. Capture changes remove one disturbance source, not a proven universal ANR fix.
5. Hidden capture/isolation APIs and legacy surface-swap fallback need validation on other systems.
6. Metrics require raw counters, user-action attribution, and process/time scope; missing measurements cannot pass.
7. Process death/reboot loses live pages; screenshots do not restore sessions.
8. No hosted customer accounts, key distribution, billing, release updater, or universal device certification exists.

Redacted reports, cycle data, task JSON and a visually reviewed screenshot are available in the [repository evidence index](docs/evidence/README.md) and [capture report](docs/evidence/capture-stability/report.md). Raw private artifacts remain outside the repository.

## Troubleshooting and source map

| Symptom | Check |
| --- | --- |
| No ADB device | Cable, USB debugging, authorization; Huawei charging-only ADB option. |
| Install waits | Phone installer/lock-screen confirmation, not just host command output. |
| Controller disconnected | Shell process/log and real ping; controller installation does not start shell. |
| Floating ball missing | Overlay permission, service, notification and power restrictions. |
| Voice not ready | Wait for actual vibration; check microphone, audio focus, context and intake. |
| Tasks queue | Two-task cap, same-app conflict, retained results/handoffs occupying resources. |
| Background blocked | Read isolation diagnostics; do not silently enable experimental mode. |
| Main screen flickers | Preserve foreground/focus/IME and handoff timeline; stop affected work. |
| Empty UI | Root/traversal diagnostics, connection generation and bounded recovery; no blind stale taps. |
| Screenshot fails | Capture mode/misses/fallback; protected pages require main-screen human handling. |
| Search key blocked | Use a real search control; do not bypass the key guard. |
| Result transfer fails | Inspect moved/rollback/preview availability; retry available preview or end viewing. |
| Memo verification fails | Check complete body, saved list and reopened exact object, not only title. |
| OCR unavailable | Upload libraries, models and dictionary to the runtime directory. |

Core source is in `android-shell/src/main/java/com/silentauto/shell/`: `AutomationEngine`, `RpcServer`, `VirtualDisplaySession`, `CaptureMirror`, `UiBridge`, and `InputBridge`. Extensions are grouped under `ext/guard`, `handoff`, `skill`, `query`, `verify`, `context`, `voice`, `amend`, `history`, and `metrics`.

Controller code is under `controller-app/src/main/java/com/silentauto/controller/`, with overlay/history/context extensions under `ext/`. Deployment lives in `web-launcher/`; probes and regression logic live in `scripts/`.

Per the workspace convention, task specifications, guides, reports, and raw validation outputs are organized under sibling `../docs/{tasks,guides,reports,validation}/`. Repository `docs/` contains upstream articles and derivative attribution; it is a different directory.

Upstream architecture articles: [English](docs/android-virtual-display-ai-automation.en.md) / [中文](docs/android-virtual-display-ai-automation.md). They do not cover all derivative extensions or latest test results.

Upstream demonstration video, not an acceptance recording for this branch:

<video src="shadowauto.mp4" controls width="100%"></video>

Redistributions must preserve applicable attribution, [LICENSE](LICENSE), and modification notices. See [NOTICE](NOTICE) and [DERIVATIVE_CHANGES](docs/DERIVATIVE_CHANGES.md).
