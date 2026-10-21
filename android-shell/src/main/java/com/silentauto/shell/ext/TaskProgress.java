package com.silentauto.shell.ext;

import android.os.SystemClock;
import com.google.gson.JsonObject;
import com.silentauto.shell.LogHub;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskProgress {
    public final String taskId, goal;
    private final LogHub logs;
    private final long started = SystemClock.elapsedRealtime();
    private long ended, waitingSinceMs;
    private final long createdAtMs = System.currentTimeMillis();
    private long finishedAtMs;
    private String skillId = "", comparison = "", evidenceScreenshot = "";
    private JsonObject sourceSteps = new JsonObject();
    private boolean protectedScreen;
    private TaskStatus status = TaskStatus.QUEUED;
    private String appName = "";
    private String app = "", stepText = "", source = "model", message = "", evidence = "", boundary = "";
    private String transcript = "";
    private long intakeElapsedMs, confirmDeadlineMs;
    private boolean pendingConfirmation, requiresConfirmation;
    private final Map<String, Integer> calls = new LinkedHashMap<>();

    public TaskProgress(String taskId, String goal, LogHub logs) {
        this.taskId = taskId; this.goal = goal; this.logs = logs;
        calls.put("executor", 0); calls.put("verifier", 0); calls.put("intake", 0);
        for (String source : new String[]{"model","skill","rescue"}) sourceSteps.addProperty(source,0);
    }
    public synchronized void voice(String original, long elapsed, boolean confirm, long deadline) {
        transcript = original; intakeElapsedMs = elapsed; pendingConfirmation = true; requiresConfirmation = confirm; confirmDeadlineMs = deadline;
        message = confirm ? "请确认任务，10 秒未确认将取消" : "3 秒后开始，可取消";
        publish();
    }
    public synchronized void confirmed() { pendingConfirmation = false; publish(); }
    public synchronized void protectedScreen(boolean value) {
        if (status.terminal()) return;
        if (protectedScreen != value) { protectedScreen = value; publish(); }
    }
    public synchronized boolean protectedScreen() { return protectedScreen; }
    public synchronized TaskStatus status() { return status; }
    public synchronized void app(String value, String name) { app = value; appName = name; }
    public synchronized void call(String role) { calls.put(role, calls.get(role) + 1); }
    public synchronized void action(String reason, String actionSource) {
        if (status.terminal()) return;
        String text = reason == null ? "" : reason.replaceAll("\\s+", " ").trim();
        stepText = text.codePointCount(0, text.length()) <= 20 ? text : text.substring(0, text.offsetByCodePoints(0, 20));
        source = actionSource;
        publish();
    }
    public synchronized void transition(TaskStatus next, String detail, String proof, String targetBoundary) {
        if (status.terminal()) return;
        if (!allowed(status, next)) throw new IllegalStateException("Invalid status transition: " + status + " -> " + next);
        if (next == TaskStatus.WAITING_USER && status != next) waitingSinceMs = System.currentTimeMillis();
        status = next;
        message = detail == null ? "" : detail;
        evidence = proof == null ? "" : proof;
        boundary = targetBoundary == null ? "" : targetBoundary;
        if (next.terminal()) { ended = SystemClock.elapsedRealtime(); finishedAtMs = System.currentTimeMillis(); }
        publish();
    }
    private static boolean allowed(TaskStatus from, TaskStatus to) {
        if (from == to || to == TaskStatus.CANCELLED || to == TaskStatus.FAILED) return true;
        if (from == TaskStatus.QUEUED) return to == TaskStatus.RUNNING;
        if (from == TaskStatus.RUNNING) return to == TaskStatus.WAITING_USER || to == TaskStatus.PAUSED || to == TaskStatus.VERIFIED;
        if (from == TaskStatus.WAITING_USER) return to == TaskStatus.RUNNING || to == TaskStatus.HANDOFF_TIMEOUT;
        return from == TaskStatus.PAUSED && to == TaskStatus.RUNNING;
    }
    public synchronized JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("taskId", taskId); o.addProperty("status", status.name());
        o.addProperty("goal", goal); o.addProperty("app", app); o.addProperty("appName", appName); o.addProperty("stepText", stepText);
        o.addProperty("source", source); o.addProperty("message", message); o.addProperty("evidence", evidence); o.addProperty("boundary", boundary);
        JsonObject counts = new JsonObject();
        for (Map.Entry<String,Integer> entry : calls.entrySet()) counts.addProperty(entry.getKey(), entry.getValue());
        o.add("llmCalls", counts);
        o.addProperty("skillId", skillId); o.add("sourceSteps",sourceSteps.deepCopy()); o.addProperty("comparison",comparison);
        o.addProperty("evidenceScreenshot",evidenceScreenshot); o.addProperty("createdAtMs",createdAtMs); o.addProperty("finishedAtMs",finishedAtMs);
        o.addProperty("waitingSinceMs", waitingSinceMs);
        o.addProperty("transcript", transcript); o.addProperty("intakeElapsedMs", intakeElapsedMs);
        o.addProperty("pendingConfirmation", pendingConfirmation && !status.terminal()); o.addProperty("requiresConfirmation", requiresConfirmation);
        o.addProperty("confirmDeadlineMs", confirmDeadlineMs);
        o.addProperty("protectedScreen", protectedScreen);
        o.addProperty("elapsedMs", (ended == 0 ? SystemClock.elapsedRealtime() : ended) - started);
        return o;
    }
    public synchronized TaskOutcome outcome() { return new TaskOutcome(status.name(), message, evidence, boundary); }
    public synchronized void evidenceScreenshot(String path) { evidenceScreenshot = path; }
    public synchronized void metrics(String id,JsonObject sources,String baseline) {
        skillId = id; sourceSteps = sources.deepCopy(); comparison = baseline; publish();
    }
    public synchronized void learned(String name) {
        if (status != TaskStatus.VERIFIED) return;
        message = "已学会：" + name;
        publish();
    }
    public synchronized void publish() { logs.status(json()); }
}
