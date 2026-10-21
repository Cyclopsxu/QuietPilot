// Modified by Codex for ShadowAuto: delegate actions, verify results and coordinate user handoff. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import com.silentauto.shell.ext.*;
import com.silentauto.shell.ext.verify.ResultVerifier;
import com.silentauto.shell.ext.verify.EvidenceFrame;
import com.silentauto.shell.ext.handoff.HandoffManager;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silentauto.paddlerocr.PaddleOcrEngine;
import com.silentauto.paddlerocr.PaddleOcrResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AutomationEngine {
    private static final int MAX_STEPS = 25;
    private static final int MAX_OBSERVATION_TOOL_CALLS = 4;
    private static final int MAX_UI_CHARS = (int) (1_000_000 * 0.6);
    private static final int LOGCAT_LAYOUT_CHUNK = 3500;
    private static final String TOOL_SYSTEM_PROMPT = """
            You operate Android only by calling exactly one provided tool. Do not describe the action.
            """.trim();

    private final Config baseConfig;
    private final LogHub logs;
    private final AppCatalog appCatalog;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, TaskState> tasks = new LinkedHashMap<>();
    private final AtomicInteger nextTaskId = new AtomicInteger(1);
    private final List<StepListener> listeners = new ArrayList<>();
    private final List<ActionSource> sources = new ArrayList<>();
    private final com.silentauto.shell.ext.guard.TaskScheduler scheduler = new com.silentauto.shell.ext.guard.TaskScheduler();
    private final com.silentauto.shell.ext.guard.ForegroundWatcher foreground = new com.silentauto.shell.ext.guard.ForegroundWatcher();
    private final com.silentauto.shell.ext.skill.TrajectoryRecorder recorder = new com.silentauto.shell.ext.skill.TrajectoryRecorder();
    private final com.silentauto.shell.ext.skill.SkillStore skills = new com.silentauto.shell.ext.skill.SkillStore();
    private UiBridge ui;
    private PaddleOcrEngine ocr;

    AutomationEngine(Config config, LogHub logs) {
        this.baseConfig = config;
        this.logs = logs;
        this.appCatalog = new AppCatalog(logs);
        listeners.add(recorder);
    }

    synchronized String startTask(TaskRequest request) {
        if (Config.empty(request.goal)) {
            throw new IllegalArgumentException("goal is required");
        }
        String taskId = Config.empty(request.taskId) ? "task-" + nextTaskId.getAndIncrement() : request.taskId;
        if (!taskId.matches("[A-Za-z0-9_.-]{1,100}") || ".".equals(taskId) || "..".equals(taskId))
            throw new IllegalArgumentException("taskId must contain safe filename characters");
        stopTask(taskId);
        TaskState task = new TaskState(taskId, request.goal, logs);
        tasks.put(taskId, task);
        recorder.prepare(taskId, request.goal);
        task.progress.publish();
        task.future = executor.submit(() -> runTask(task, request));
        return taskId;
    }

    synchronized void stopTask() {
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            stopTask(taskId);
        }
        logs.task("automation stopped");
    }

    synchronized void stopTask(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status().terminal()) {
            return;
        }
        task.stop.set(true);
        transition(task, TaskStatus.CANCELLED, "automation cancelled", "", "");
        if (task.ai != null) task.ai.cancel();
        if (task.verifierAi != null) task.verifierAi.cancel();
        if (task.future != null) {
            task.future.cancel(true);
        }
        releaseTask(task);
        scheduler.release(task);
        recorder.snapshotOutcome(taskId, task.progress.outcome());
        logs.task(taskId, "automation stopped");
    }

    synchronized void forceStart(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status() != TaskStatus.QUEUED) throw new IllegalStateException("task is not queued");
        task.forceStart.set(true);
        logs.task(taskId, "WARNING: forceStart may conflict with the user on the main display");
    }

    synchronized void openTaskOnMain(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.handoff == null || !task.progress.protectedScreen())
            throw new IllegalStateException("task is not waiting on a protected page");
        task.handoff.resume("open_main");
    }

    synchronized void injectTouch(String taskId, String action, float x, float y) throws Exception {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status() != TaskStatus.WAITING_USER || task.display == null)
            throw new IllegalStateException("touch is only allowed while waiting for user");
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("finite coordinates required");
        task.touch.inject(action, Math.max(0, Math.min(task.display.width - 1, x)),
                Math.max(0, Math.min(task.display.height - 1, y)));
    }

    synchronized void resumeTask(String taskId, String outcome) {
        TaskState task = tasks.get(taskId);
        if (task == null || !"waiting_user".equals(task.state) || task.handoff == null) {
            throw new IllegalStateException("task is not waiting for user");
        }
        task.handoff.resume(outcome);
        if (task.verifierAi != null) task.verifierAi.cancel();
    }

    synchronized JsonArray listTasks() {
        JsonArray array = new JsonArray();
        for (TaskState task : tasks.values()) {
            JsonObject item = task.progress.json();
            item.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
            array.add(item);
        }
        return array;
    }

    private void transition(TaskState task, TaskStatus status, String message, String evidence, String boundary) {
        task.progress.transition(status, message, evidence, boundary);
        task.state = task.progress.status().name().toLowerCase(Locale.ROOT);
    }

    synchronized JsonObject statusJson() {
        JsonObject object = new JsonObject();
        boolean running = false;
        int displayId = -1;
        for (TaskState task : tasks.values()) {
            if (!"running".equals(task.state) && !"waiting_user".equals(task.state)) {
                continue;
            }
            running = true;
            if (displayId < 0 && task.display != null) {
                displayId = task.display.displayId;
            }
        }
        object.addProperty("state", running ? "running" : "idle");
        object.addProperty("displayId", running ? displayId : -1);
        JsonArray array = new JsonArray();
        for (TaskState task : tasks.values()) {
            JsonObject item = task.progress.json();
            item.addProperty("state", task.state);
            item.addProperty("status", task.progress.status().name());
            item.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
            array.add(item);
        }
        object.add("tasks", array);
        return object;
    }

    synchronized JsonObject dumpUiJson(int displayId, int width, int height, String mode) {
        String value = getUi().dump(displayId, width, height, mode);
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private void runTask(TaskState task, TaskRequest request) {
        Config config = baseConfig.withOverrides(request.apiKey, request.apiBase, request.model).withProfiles(request.models);
        String doneMessage = null;
        ExecutionContext ctx = null;
        try {
            config.executor.validate();
            config.verifier.validate();
            AiClient ai = new AiClient(config, logs, task.taskId, () -> task.progress.call("executor"));
            task.ai = ai;
            UiBridge bridge = getUi();
            InputBridge input = new InputBridge();
            ClipboardBridge clipboard = new ClipboardBridge();
            task.progress.action("正在选择应用", "model");
            task.searchText = searchTextFromGoal(request.goal);
            if (!Config.empty(task.searchText)) {
                logs.task(task.taskId, "search text hint: " + task.searchText);
            }

            logs.task(task.taskId, "listing installed apps");
            List<AppCatalog.AppInfo> apps = appCatalog.installedApps();
            ActionDecision app = chooseApp(ai, request.goal, apps);
            if (Config.empty(app.packageName)) {
                app.packageName = appCatalog.localPackageGuess(request.goal, apps);
            }
            if (Config.empty(app.packageName)) {
                throw new IllegalStateException("AI did not choose an app package");
            }

            task.packageName = app.packageName;
            task.appName = app.packageName;
            for (AppCatalog.AppInfo installed : apps) {
                if (app.packageName.equals(installed.packageName)) { task.appName = installed.name; break; }
            }
            task.progress.app(app.packageName, task.appName);
            awaitAdmission(task);
            if (task.stop.get()) throw new InterruptedException("task cancelled before launch");
            transition(task, TaskStatus.RUNNING, "开始执行", "", "");
            DisplaySpec displaySpec = DisplaySpec.current(logs);
            task.videoStreamer = new VideoStreamer(logs, task.taskId, displaySpec.width, displaySpec.height);
            task.display = VirtualDisplaySession.create(task.videoStreamer.width(), task.videoStreamer.height(), displaySpec.dpi, task.videoStreamer.inputSurface());
            task.videoStreamer.start();
            logs.task(task.taskId, "virtual display created: " + task.display.displayId + " " + task.display.width + "x" + task.display.height + " dpi=" + task.display.dpi);
            logs.task(task.taskId, "virtual display local IME " + (task.display.localImeEnabled() ? "enabled" : "unavailable"));
            appCatalog.launchPackage(task.taskId, app.packageName, task.display.displayId);
            logs.task(task.taskId, "launched " + app.packageName);
            sleep(task, 1800);

            ctx = new ExecutionContext(task.taskId, task.display.displayId, task.display.width, task.display.height,
                    bridge, input, clipboard, logs);
            for (StepListener listener : listeners) listener.onTaskStart(ctx, request.goal, app.packageName);
            task.touch = new com.silentauto.shell.ext.handoff.TouchSession(input, ctx.displayId);
            ActionExecutor actionExecutor = new ActionExecutor(logs);
            AiClient verifierAi = new AiClient(config.forRole(config.verifier), logs, task.taskId, () -> task.progress.call("verifier"));
            task.verifierAi = verifierAi;
            ResultVerifier verifier = new ResultVerifier(verifierAi::completeVision, () -> screenOcrJson(task),
                    () -> EvidenceFrame.from(task.display.captureBitmap(8, 120)));
            for (int step = 1; step <= MAX_STEPS && !task.stop.get(); step++) {
                String dump = bridge.dump(task.display.displayId, task.display.width, task.display.height, UiBridge.MODE_SIMPLE);
                rememberUiObservation(task, dump);
                for (StepListener listener : listeners) listener.beforeStep(ctx, step, dump);
                ActionDecision action = firstNonNull(ctx, task, step, dump);
                if (action == null) action = nextAction(task, ai, bridge, request.goal, app.packageName, step, task.display.displayId, dump);
                if (task.stop.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("task cancelled");
                action = applyAutoCorrections(task, action);
                task.progress.action(action.reason, action.source);
                logs.task(task.taskId, "step " + step + ": " + action.action + " " + safe(action.reason) + " source=" + action.source);
                if ("request_user".equals(action.action) || "done".equals(action.action)) {
                    for (StepListener listener : listeners) listener.afterAction(ctx, step, action, new StepResult(null, null), dump);
                }
                if ("request_user".equals(action.action)) {
                    if (!HandoffManager.valid(action.kind, action.message, action.boundary)) {
                        task.lastAction = "handoff_failed";
                        task.lastReason = "request_user requires a valid kind, message and visible boundary";
                        continue;
                    }
                    ResultVerifier.Result boundary = verifier.verifyBoundary(ctx, action.boundary);
                    logs.task(task.taskId, "handoff boundary verified=" + boundary.verified + ": "
                            + (boundary.verified ? boundary.evidence : boundary.missing));
                    boolean protectedScreen = boundary.frame != null && boundary.frame.protectedScreen;
                    if (!boundary.verified && !protectedScreen) {
                        task.lastAction = "handoff_failed";
                        task.lastReason = boundary.missing;
                        continue;
                    }
                    HandoffManager handoff = new HandoffManager();
                    task.handoff = handoff;
                    recorder.handedOff(task.taskId);
                    task.progress.protectedScreen(protectedScreen);
                    transition(task, TaskStatus.WAITING_USER, protectedScreen
                            ? "该页面受系统保护，请在 " + task.appName + " 中完成。" + action.message
                            : action.message, boundary.evidence, action.boundary);
                    logs.task(task.taskId, "HANDOFF " + action.kind + ": " + action.message);
                    logs.handoff(task.taskId, action.kind, action.message, action.boundary);
                    String outcome = handoff.awaitResponse(ctx, verifier, action.boundary, task.progress, verifierAi::cancel);
                    task.handoff = null;
                    if (!"done".equals(outcome)) {
                        task.resultStatus = "handoff";
                        if ("open_main".equals(outcome)) {
                            appCatalog.openOnMain(task.taskId, task.packageName);
                            task.lastReason = "已交给你在主屏完成";
                        } else {
                            task.lastReason = "已停在 " + action.boundary + "，等待用户: " + action.message + " (" + outcome + ")";
                        }
                        transition(task, "timeout".equals(outcome) ? TaskStatus.HANDOFF_TIMEOUT : TaskStatus.CANCELLED,
                                task.lastReason, "", action.boundary);
                        logs.result(task.taskId, "handoff", task.lastReason);
                        break;
                    }
                    transition(task, TaskStatus.RUNNING, "用户已处理，继续执行", "", "");
                    task.lastAction = "user_resumed";
                    task.lastReason = "User handled the boundary; inspect the fresh UI before continuing";
                    continue;
                }
                if ("done".equals(action.action)) {
                    ResultVerifier.Result verification = verifier.verify(ctx, request.goal, action.expectedEvidence);
                    if (verification.verified) {
                        String evidencePath = verifier.saveEvidence(ctx, verification);
                        logs.task(task.taskId, "evidence saved: " + evidencePath);
                        task.completed = true;
                        task.resultStatus = "verified";
                        transition(task, TaskStatus.VERIFIED, "任务已验收", verification.evidence, "");
                        logs.task(task.taskId, "VERIFIED: " + verification.evidence);
                        logs.result(task.taskId, "verified", verification.evidence);
                        break;
                    }
                    task.lastAction = "verification_failed";
                    task.lastReason = verification.missing;
                    logs.task(task.taskId, "verification failed: " + verification.missing);
                    if (++task.verificationFailures >= 3) {
                        task.resultStatus = "unverified";
                        transition(task, TaskStatus.FAILED, verification.missing, verification.evidence, "");
                        logs.result(task.taskId, "unverified", verification.missing);
                        break;
                    }
                    continue;
                }
                StepResult result = null;
                try { result = actionExecutor.execute(ctx, task, action); }
                finally { for (StepListener listener : listeners) listener.afterAction(ctx, step, action, result, dump); }
                rememberAction(task, action, result.clickResult, result.focusResult);
                sleep(task, 900);
            }

            if (task.stop.get()) {
                doneMessage = "automation cancelled";
            } else if ("handoff".equals(task.resultStatus)) {
                doneMessage = "automation handoff: " + task.lastReason;
            } else if ("unverified".equals(task.resultStatus)) {
                doneMessage = "automation unverified: " + task.lastReason;
            } else if (task.completed) {
                doneMessage = "automation finished";
            } else {
                throw new IllegalStateException("max automation steps reached before finish");
            }
        } catch (Throwable e) {
            if (task.stop.get()) {
                transition(task, TaskStatus.CANCELLED, "automation cancelled", "", "");
                doneMessage = "automation cancelled";
            } else {
                transition(task, TaskStatus.FAILED, e.getMessage(), "", "");
                logs.error(task.taskId, "automation failed", e);
            }
        } finally {
            JsonObject recorded = recorder.trajectory(task.taskId);
            if (recorded != null) recorded.add("outcome", new com.google.gson.Gson().toJsonTree(task.progress.outcome()));
            if (ctx != null) {
                for (StepListener listener : listeners) {
                    try { listener.onTaskEnd(ctx, task.progress.outcome()); }
                    catch (Throwable recordingError) { logs.error(task.taskId, "task listener failed", recordingError); }
                }
            }
            if (ctx == null) recorder.finish(task.taskId, task.progress.outcome());
            if (recorded != null && task.ai != null && !task.stop.get()) {
                try {
                    JsonObject metrics = task.progress.json();
                    int calls = 0;
                    for (JsonElement count : metrics.getAsJsonObject("llmCalls").asMap().values()) calls += count.getAsInt();
                    JsonObject learned = new com.silentauto.shell.ext.skill.SkillCompiler(skills).compile(recorded,
                            task.ai::complete, metrics.get("elapsedMs").getAsLong(), calls);
                    if (learned != null) task.progress.learned(learned.get("name").getAsString());
                } catch (Exception compileError) {
                    logs.task(task.taskId, "skill compile skipped: " + compileError.getMessage());
                }
            }
            task.ai = null;
            task.verifierAi = null;
            releaseTask(task);
            scheduler.release(task);
            logs.task(task.taskId, "task metrics " + task.progress.json());
            if (doneMessage != null) {
                logs.task(task.taskId, doneMessage);
            }
        }
    }

    private void awaitAdmission(TaskState task) throws Exception {
        String previous = "";
        while (!task.stop.get()) {
            boolean force = task.forceStart.get();
            boolean conflict = false;
            String reason;
            try {
                if (!force) conflict = task.packageName.equals(foreground.foregroundPackage());
                reason = scheduler.tryAcquire(task, task.packageName, conflict, force);
            } catch (Exception e) {
                reason = "暂时无法确认主屏 App，等待安全启动";
            }
            if (reason == null) return;
            reason = reason.replace(task.packageName, task.appName);
            if (!reason.equals(previous)) {
                transition(task, TaskStatus.QUEUED, reason, "", "");
                logs.task(task.taskId, reason);
                previous = reason;
            }
            Thread.sleep(2000);
        }
    }

    private ActionDecision firstNonNull(ExecutionContext ctx, TaskState task, int step, String dump) {
        for (ActionSource source : sources) {
            ActionDecision decision = source.next(ctx, task, step, dump);
            if (decision != null) return decision;
        }
        return null;
    }

    private synchronized UiBridge getUi() {
        if (ui == null) {
            ui = new UiBridge(logs);
            ui.connect();
        }
        return ui;
    }

    private void clearSearchSubmitIfExhausted(TaskState task) {
        if (task.searchSubmitAttempts >= 2 && task.pendingSearchSubmit) {
            task.pendingSearchSubmit = false;
            task.searchInputActive = false;
            logs.task(task.taskId, "search submit fallback exhausted");
        }
    }

    private ActionDecision chooseApp(AiClient ai, String goal, List<AppCatalog.AppInfo> apps) throws Exception {
        String prompt = String.format(Locale.US, """
                Choose the Android app for this user goal.
                Return only JSON: {"packageName":"...","reason":"..."}
                Goal: %s
                Apps:
                %s""", goal, appCatalog.appsText(apps));
        return ActionDecision.from(ai.complete(prompt));
    }

    private ActionDecision nextAction(TaskState task, AiClient ai, UiBridge bridge, String goal, String packageName, int step, int displayId, String uiDump) throws Exception {
        String prompt = String.format(Locale.US, """
                You control an Android app on a %dx%d virtual display.
                Rules:
                1. Call exactly one tool, never answer with prose.
                2. Coordinates are display-local in this %dx%d virtual display. Never use preview/screenshot pixels.
                3. Prefer tap_target using targetIndex from targets. Use raw tap only when no targetIndex exists, using a node center value.
                4. Use focus_input before input_text. input_text sets the focused field to the exact value, not an append operation.
                5. If the field should be empty, use clear_text. If existing text must be replaced, use input_text directly after focus_input.
                6. Use press_key with search or enter to submit focused search/input fields when appropriate.
                7. Use scroll_ui when more content is needed. direction means content direction: down reveals lower content, up reveals upper content.
                8. Use get_ui_layout mode=simple after a UI-changing action if the next target is uncertain; use mode=full only when simple lacks needed nodes.
                9. Use get_screen_ocr when UI layout is sparse, empty, wrong, or the visible page has text without accessibility nodes. OCR bounds are display-local.
                10. Use finish only when the visible UI proves the user's goal is complete.
                11. Search text candidate is the text to enter after opening a search field. If it is non-empty and the previous action clicked or focused a search/input field, call input_text with exactly that text; do not wait.
                12. Do not call wait repeatedly on sparse or empty UI. After two waits, call get_ui_layout, get_screen_ocr, input_text, press_back, scroll_ui, or another concrete tool.
                13. If a search was submitted and the UI dump has windows but inputs=[] and targets=[], the page may be visually rendered but accessibility-opaque. Do not press_back or restart the search only because nodes are missing. Prefer get_screen_ocr, get_ui_layout(full), wait once, or scroll_ui.
                14. For payment or payment passwords, login, captcha or sliders, face/fingerprint biometrics, and irreversible deletion/refunds without explicit user authorization, you MUST call request_user. Never attempt these yourself.
                Goal: %s
                Package: %s
                Step: %d
                Search text candidate: %s
                Previous action: %s
                Previous reason: %s
                Consecutive waits: %d
                Last UI stats: windows=%d inputs=%d targets=%d sparseAfterSearch=%d source=%s
                UI:
                %s""", task.display.width, task.display.height, task.display.width, task.display.height,
                goal, packageName, step, safe(task.searchText), safe(task.lastAction), safe(task.lastReason), task.consecutiveWaits,
                task.lastUiWindows, task.lastUiInputs, task.lastUiTargets, task.sparseUiAfterSearch, safe(task.lastUiWindowSource), truncate(uiDump, MAX_UI_CHARS));
        JsonArray messages = new JsonArray();
        messages.add(AiClient.message("system", TOOL_SYSTEM_PROMPT));
        messages.add(AiClient.message("user", prompt));
        JsonArray tools = ActionTools.tools();
        for (int observationCalls = 0; observationCalls <= MAX_OBSERVATION_TOOL_CALLS; observationCalls++) {
            AiClient.ToolResult result = ai.callTools(messages, tools);
            if (result.toolCalls.isEmpty()) {
                return ActionDecision.from(result.content);
            }
            AiClient.ToolCall call = result.toolCalls.get(0);
            logs.task(task.taskId, "tool_call " + call.name + " " + call.arguments);
            if (task.pendingSearchSubmit && task.searchSubmitAttempts < 2 && ActionTools.isObservationTool(call)) {
                logs.task(task.taskId, "auto submit search before observation");
                return ActionDecision.submitSearch("submit focused search before reading more layout");
            }
            if (!ActionTools.isObservationTool(call)) {
                return ActionDecision.fromTool(call);
            }
            if (observationCalls == MAX_OBSERVATION_TOOL_CALLS) {
                logs.task(task.taskId, "observation tool limit reached");
                return ActionDecision.wait(300);
            }
            String observation;
            if (ActionTools.isOcrTool(call)) {
                observation = screenOcrJson(task);
                logs.task(task.taskId, "ocr returned to AI");
            } else {
                String mode = ActionTools.layoutMode(call.arguments);
                observation = bridge.dump(displayId, task.display.width, task.display.height, mode);
                logLayoutDump(task.taskId, displayId, mode, observation);
                logs.task(task.taskId, "layout returned to AI");
            }
            messages.add(ActionTools.toolCallMessage(call, observationCalls));
            messages.add(ActionTools.toolResultMessage(ActionTools.toolCallId(call, observationCalls), truncate(observation, MAX_UI_CHARS)));
        }
        return ActionDecision.wait(1000);
    }

    private synchronized String screenOcrJson(TaskState task) {
        JsonObject object = new JsonObject();
        object.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
        object.addProperty("width", task.display == null ? DisplaySpec.FALLBACK_WIDTH : task.display.width);
        object.addProperty("height", task.display == null ? DisplaySpec.FALLBACK_HEIGHT : task.display.height);
        JsonArray array = new JsonArray();
        object.add("results", array);
        Bitmap bitmap = null;
        try {
            if (task.display == null) {
                object.addProperty("error", "virtual display is not available");
                return JsonRpc.GSON.toJson(object);
            }
            logs.logcat(task.taskId, "ocr_dump_begin displayId=" + task.display.displayId);
            bitmap = ScreenCapture.latestBitmap(task.display, 8, 120);
            if (bitmap == null) {
                object.addProperty("error", "no latest frame available");
                logs.logcat(task.taskId, "ocr_dump_error no latest frame available");
                return JsonRpc.GSON.toJson(object);
            }
            List<PaddleOcrResult> results = getOcrEngine().recognize(bitmap);
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                PaddleOcrResult result = results.get(i);
                if (!Config.empty(result.getText())) {
                    if (combined.length() > 0) {
                        combined.append('\n');
                    }
                    combined.append(result.getText());
                }
                array.add(ocrResultJson(i, result));
            }
            object.addProperty("text", combined.toString());
            logOcrResults(task.taskId, results);
            logs.logcat(task.taskId, "ocr_dump_end displayId=" + task.display.displayId);
        } catch (Throwable e) {
            object.addProperty("error", e.getMessage());
            logs.error(task.taskId, "ocr dump failed", e);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return JsonRpc.GSON.toJson(object);
    }

    private PaddleOcrEngine getOcrEngine() throws Exception {
        if (ocr == null) {
            logs.logcat(null, "ocr_engine_init_begin");
            ocr = new PaddleOcrEngine();
            ocr.init();
            logs.logcat(null, "ocr_engine_init_done");
        }
        return ocr;
    }

    private JsonObject ocrResultJson(int index, PaddleOcrResult result) {
        JsonObject object = new JsonObject();
        Rect bounds = result.getBounds();
        object.addProperty("index", index);
        object.addProperty("text", result.getText());
        object.addProperty("confidence", result.getConfidence());
        JsonObject rect = new JsonObject();
        rect.addProperty("left", bounds.left);
        rect.addProperty("top", bounds.top);
        rect.addProperty("right", bounds.right);
        rect.addProperty("bottom", bounds.bottom);
        rect.addProperty("centerX", bounds.centerX());
        rect.addProperty("centerY", bounds.centerY());
        object.add("bounds", rect);
        JsonArray points = new JsonArray();
        for (Point point : result.getPoints()) {
            JsonObject item = new JsonObject();
            item.addProperty("x", point.x);
            item.addProperty("y", point.y);
            points.add(item);
        }
        object.add("points", points);
        return object;
    }

    private void logOcrResults(String taskId, List<PaddleOcrResult> results) {
        logs.logcat(taskId, "ocr_result_count=" + results.size());
        for (int i = 0; i < results.size(); i++) {
            PaddleOcrResult result = results.get(i);
            Rect bounds = result.getBounds();
            logs.logcat(taskId, "ocr[" + i + "] text=" + result.getText()
                    + " confidence=" + result.getConfidence()
                    + " bounds=" + bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom
                    + " cls=" + result.getClsIndex()
                    + " clsConfidence=" + result.getClsConfidence());
        }
    }

    private void logLayoutDump(String taskId, int displayId, String mode, String layout) {
        String value = layout == null ? "" : layout;
        int total = value.isEmpty() ? 1 : (value.length() + LOGCAT_LAYOUT_CHUNK - 1) / LOGCAT_LAYOUT_CHUNK;
        logs.logcat(taskId, "layout_dump_begin displayId=" + displayId + " mode=" + mode + " chars=" + value.length() + " chunks=" + total);
        if (value.isEmpty()) {
            logs.logcat(taskId, "layout_dump_part 1/1 <empty>");
        } else {
            for (int i = 0; i < value.length(); i += LOGCAT_LAYOUT_CHUNK) {
                int part = i / LOGCAT_LAYOUT_CHUNK + 1;
                int end = Math.min(value.length(), i + LOGCAT_LAYOUT_CHUNK);
                logs.logcat(taskId, "layout_dump_part " + part + "/" + total + " " + value.substring(i, end));
            }
        }
        logs.logcat(taskId, "layout_dump_end displayId=" + displayId + " mode=" + mode);
    }

    private ActionDecision applyAutoCorrections(TaskState task, ActionDecision action) {
        if ("wait".equals(action.action) && !Config.empty(task.pendingInputText)) {
            String text = task.pendingInputText;
            task.pendingInputText = "";
            logs.task(task.taskId, "auto input after search focus: " + text);
            return ActionDecision.inputText(text, "auto input after focused search field");
        }
        if (task.pendingSearchSubmit && task.searchSubmitAttempts < 2 && blocksSearchSubmit(action)) {
            logs.task(task.taskId, "auto submit search instead of " + action.action);
            return ActionDecision.submitSearch("submit focused search after entering text");
        }
        if (task.searchSubmitted && task.sparseUiAfterSearch > 0 && "back".equals(action.action)) {
            logs.task(task.taskId, "block back on sparse post-search accessibility tree");
            return sparsePostSearchFallback(task);
        }
        return action;
    }

    private ActionDecision sparsePostSearchFallback(TaskState task) {
        if (task.sparseUiAfterSearch <= 1) {
            return ActionDecision.wait(1200, "wait without leaving sparse post-search page");
        }
        String direction = task.sparseUiAfterSearch % 2 == 0 ? "down" : "up";
        return ActionDecision.scroll(direction, 0, "try to reveal accessible content without leaving sparse post-search page");
    }

    private boolean blocksSearchSubmit(ActionDecision action) {
        return "wait".equals(action.action)
                || "back".equals(action.action)
                || "done".equals(action.action);
    }

    private void rememberAction(TaskState task, ActionDecision action, UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        String previousAction = task.lastAction;
        String previousReason = task.lastReason;
        if ("wait".equals(action.action)) {
            task.consecutiveWaits++;
        } else {
            task.consecutiveWaits = 0;
        }
        if (shouldInputSearchText(task, action, clickResult, focusResult)) {
            task.pendingInputText = task.searchText;
            task.searchInputActive = true;
            task.pendingSearchSubmit = false;
            task.searchSubmitAttempts = 0;
            task.searchSubmitted = false;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "will input search text next step: " + task.pendingInputText);
        }
        if ("text".equals(action.action) && isSearchTextEntry(task, action, previousAction, previousReason)) {
            task.pendingSearchSubmit = true;
            task.searchInputActive = false;
            task.searchSubmitAttempts = 0;
            task.searchSubmitted = false;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "will submit search next step");
        }
        if ("key".equals(action.action) && task.pendingSearchSubmit && isSubmitKey(action.key)) {
            task.searchSubmitAttempts++;
            task.searchSubmitted = true;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "model submitted search by key attempt " + task.searchSubmitAttempts);
            clearSearchSubmitIfExhausted(task);
        }
        if ("submit_search".equals(action.action)) {
            task.searchSubmitted = true;
            task.sparseUiAfterSearch = 0;
        }
        if ("text".equals(action.action) || "clear_text".equals(action.action) || "back".equals(action.action)) {
            task.pendingInputText = "";
        }
        if ("clear_text".equals(action.action) || "back".equals(action.action)) {
            task.searchInputActive = false;
        }
        task.lastAction = action.action;
        task.lastReason = action.reason;
    }

    private void rememberUiObservation(TaskState task, String dump) {
        UiStats stats = uiStats(dump);
        task.lastUiWindows = stats.windows;
        task.lastUiInputs = stats.inputs;
        task.lastUiTargets = stats.targets;
        task.lastUiWindowSource = stats.source;
        task.lastUiWindowError = stats.error;
        if (task.searchSubmitted && stats.sparse()) {
            task.sparseUiAfterSearch++;
            logs.task(task.taskId, "sparse post-search UI tree: windows=" + stats.windows
                    + " inputs=" + stats.inputs
                    + " targets=" + stats.targets
                    + " streak=" + task.sparseUiAfterSearch
                    + " source=" + safe(stats.source));
        } else if (!stats.sparse()) {
            task.sparseUiAfterSearch = 0;
        }
    }

    private UiStats uiStats(String dump) {
        UiStats stats = new UiStats();
        try {
            JsonObject object = JsonParser.parseString(safe(dump)).getAsJsonObject();
            stats.windows = arraySize(object.get("windows"));
            stats.inputs = arraySize(object.get("inputs"));
            stats.targets = arraySize(object.get("targets"));
            stats.source = stringValue(object, "windowSource");
            stats.error = stringValue(object, "windowError");
        } catch (Throwable ignored) {
            stats.error = "parse failed";
        }
        return stats;
    }

    private int arraySize(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return 0;
        }
        return element.getAsJsonArray().size();
    }

    private String stringValue(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return "";
        }
        return object.get(name).getAsString();
    }

    private boolean isSearchTextEntry(TaskState task, ActionDecision action, String previousAction, String previousReason) {
        if (Config.empty(task.searchText) || Config.empty(action.text)) {
            return false;
        }
        if (!sameText(action.text, task.searchText)) {
            return false;
        }
        return task.searchInputActive
                || looksLikeSearchInput(previousAction + " " + previousReason + " " + action.reason);
    }

    private boolean sameText(String left, String right) {
        return safe(left).trim().equalsIgnoreCase(safe(right).trim());
    }

    private boolean isSubmitKey(String key) {
        String value = safe(key).toLowerCase(Locale.US);
        return "search".equals(value) || "enter".equals(value);
    }

    private boolean shouldInputSearchText(TaskState task, ActionDecision action, UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        if (Config.empty(task.searchText)) {
            return false;
        }
        if (!"tap_target".equals(action.action) && !"focus_input".equals(action.action) && !"tap".equals(action.action)) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        appendSearchSignal(text, action.reason);
        appendSearchSignal(text, action.query);
        if (clickResult != null) {
            appendSearchSignal(text, clickResult.label);
            appendSearchSignal(text, clickResult.path);
        }
        if (focusResult != null) {
            appendSearchSignal(text, focusResult.label);
            appendSearchSignal(text, focusResult.path);
        }
        return looksLikeSearchInput(text.toString());
    }

    private void appendSearchSignal(StringBuilder out, String value) {
        if (!Config.empty(value)) {
            out.append(' ').append(value);
        }
    }

    private boolean looksLikeSearchInput(String value) {
        String text = safe(value).toLowerCase(Locale.US);
        return text.contains("search")
                || text.contains("query")
                || text.contains("keyword")
                || text.contains("input")
                || text.contains("edit")
                || text.contains("搜索")
                || text.contains("搜")
                || text.contains("输入")
                || text.contains("查找");
    }

    private String searchTextFromGoal(String goal) {
        if (Config.empty(goal)) {
            return "";
        }
        String[] markers = {"搜索", "搜一下", "搜", "查找", "查询"};
        for (String marker : markers) {
            int index = goal.indexOf(marker);
            if (index >= 0) {
                String text = cleanSearchText(goal.substring(index + marker.length()));
                return genericSearchTarget(text) ? "" : text;
            }
        }
        return "";
    }

    private String cleanSearchText(String raw) {
        String value = safe(raw).trim();
        while (value.startsWith(":") || value.startsWith("：") || value.startsWith("，") || value.startsWith(",") || value.startsWith(" ")) {
            value = value.substring(1).trim();
        }
        String[] stops = {"，", ",", "。", ".", "；", ";", "\n", "然后", "并", "再", "点杯", "点一杯", "买", "购买", "下单"};
        int end = value.length();
        for (String stop : stops) {
            int index = value.indexOf(stop);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return value.substring(0, end).trim();
    }

    private boolean genericSearchTarget(String value) {
        String text = safe(value).trim().toLowerCase(Locale.US);
        return text.isEmpty()
                || text.startsWith("入口")
                || text.startsWith("框")
                || text.startsWith("栏")
                || text.startsWith("按钮")
                || text.startsWith("图标")
                || text.startsWith("icon")
                || text.startsWith("button");
    }

    private void releaseTask(TaskState task) {
        if (task.videoStreamer != null) {
            task.videoStreamer.stop();
            task.videoStreamer = null;
        }
        if (task.display != null) {
            task.display.release();
            task.display = null;
        }
    }

    private void sleep(TaskState task, long ms) {
        if (!task.stop.get()) {
            SystemClock.sleep(ms);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    static final class TaskRequest {
        final String taskId;
        final String goal;
        final String apiKey;
        final String apiBase;
        final String model;
        JsonObject models;

        TaskRequest(String goal, String apiKey, String apiBase, String model) {
            this("", goal, apiKey, apiBase, model);
        }

        TaskRequest(String taskId, String goal, String apiKey, String apiBase, String model) {
            this.taskId = taskId;
            this.goal = goal;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.model = model;
        }
    }

    public static final class TaskState {
        public final String taskId;
        public final AtomicBoolean forceStart = new AtomicBoolean(false);
        public final AtomicBoolean stop = new AtomicBoolean(false);
        public volatile String state = "queued";
        public final TaskProgress progress;
        public String packageName = "";
        public String appName = "";
        public volatile AiClient verifierAi;
        public volatile Future<?> future;
        public volatile VirtualDisplaySession display;
        public volatile VideoStreamer videoStreamer;
        public volatile AiClient ai;
        public boolean completed;
        public volatile HandoffManager handoff;
        public com.silentauto.shell.ext.handoff.TouchSession touch;
        public String resultStatus = "";
        public int verificationFailures;
        public String agentClipboard = "";
        public String searchText = "";
        public String pendingInputText = "";
        public boolean searchInputActive;
        public boolean pendingSearchSubmit;
        public int searchSubmitAttempts;
        public String lastAction = "";
        public String lastReason = "";
        public int consecutiveWaits;
        public boolean searchSubmitted;
        public int sparseUiAfterSearch;
        public int lastUiWindows;
        public int lastUiInputs;
        public int lastUiTargets;
        public String lastUiWindowSource = "";
        public String lastUiWindowError = "";

        TaskState(String taskId, String goal, LogHub logs) {
            this.taskId = taskId;
            this.progress = new TaskProgress(taskId, goal, logs);
        }
    }

    private static final class UiStats {
        int windows;
        int inputs;
        int targets;
        String source = "";
        String error = "";

        boolean sparse() {
            return windows > 0 && inputs == 0 && targets == 0;
        }
    }

}
