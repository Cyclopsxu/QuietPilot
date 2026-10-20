package com.silentauto.shell.ext.handoff;

import android.os.SystemClock;
import com.silentauto.shell.ext.ExecutionContext;
import com.silentauto.shell.ext.TaskProgress;
import com.silentauto.shell.ext.verify.EvidenceFrame;
import com.silentauto.shell.ext.verify.ResultVerifier;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class HandoffManager {
    private final CountDownLatch response = new CountDownLatch(1);
    private volatile String outcome;
    private final long timeoutMs;

    public HandoffManager() { this(10 * 60 * 1000L); }
    public HandoffManager(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public static boolean valid(String kind, String message, String boundary) {
        return Arrays.asList("payment", "login", "captcha", "biometric", "confirm").contains(kind)
                && message != null && !message.trim().isEmpty() && boundary != null && !boundary.trim().isEmpty();
    }
    public synchronized void resume(String value) {
        if (!"done".equals(value) && !"cancel".equals(value) && !"open_main".equals(value))
            throw new IllegalArgumentException("outcome must be done or cancel");
        if (outcome != null) throw new IllegalStateException("handoff already answered");
        outcome = value;
        response.countDown();
    }
    public String awaitResponse(ExecutionContext ctx, ResultVerifier verifier, String boundary, TaskProgress progress) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        long lastChange = SystemClock.elapsedRealtime();
        Long previous = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            long wait = Math.min(1500, Math.max(1, deadline - SystemClock.elapsedRealtime()));
            if (response.await(wait, TimeUnit.MILLISECONDS)) return outcome;
            EvidenceFrame frame = verifier.observe();
            progress.protectedScreen(frame.protectedScreen);
            if (frame.protectedScreen) { previous = null; continue; }
            long now = SystemClock.elapsedRealtime();
            if (previous == null || previous.longValue() != frame.fingerprint) {
                previous = frame.fingerprint; lastChange = now; continue;
            }
            if (now - lastChange < 2000) continue;
            // A manual answer always wins over an in-flight read-only assessment.
            ResultVerifier.Result atBoundary = verifier.verifyBoundary(ctx, boundary);
            if (outcome != null) return outcome;
            if (atBoundary.frame != null && atBoundary.frame.protectedScreen) {
                progress.protectedScreen(true); continue;
            }
            if (!atBoundary.verified && atBoundary.assessed) {
                ResultVerifier.Result ready = verifier.verifyReady(ctx, boundary);
                if (outcome != null) return outcome;
                if (ready.verified) {
                    ctx.logs.task(ctx.taskId, "handoff automatically resumed: " + ready.evidence);
                    return "done";
                }
            }
            lastChange = SystemClock.elapsedRealtime();
        }
        return "timeout";
    }
}
