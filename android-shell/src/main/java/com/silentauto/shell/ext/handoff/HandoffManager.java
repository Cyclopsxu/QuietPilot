package com.silentauto.shell.ext.handoff;

import android.os.SystemClock;
import com.silentauto.shell.ext.ExecutionContext;
import com.silentauto.shell.ext.TaskProgress;
import com.silentauto.shell.ext.verify.EvidenceFrame;
import com.silentauto.shell.ext.verify.ResultVerifier;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

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
        return awaitResponse(ctx, verifier, boundary, progress, () -> {});
    }

    public String awaitAcknowledgement() throws InterruptedException {
        response.await();
        return outcome;
    }

    public String awaitResponse(ExecutionContext ctx, ResultVerifier verifier, String boundary, TaskProgress progress,
                                Runnable cancelVerification) throws Exception {
        if (outcome != null) return outcome;
        AtomicReference<EvidenceFrame> latest = new AtomicReference<>();
        AtomicLong lastChange = new AtomicLong(SystemClock.elapsedRealtime());
        ScheduledExecutorService observer = Executors.newSingleThreadScheduledExecutor();
        observer.schedule(() -> {
            boolean expired;
            synchronized (this) {
                expired = outcome == null;
                if (expired) { outcome = "timeout"; response.countDown(); }
            }
            if (expired) cancelVerification.run();
        }, timeoutMs, TimeUnit.MILLISECONDS);
        if (verifier != null) observer.scheduleAtFixedRate(() -> {
            try {
                EvidenceFrame frame = verifier.observe();
                EvidenceFrame previous = latest.getAndSet(frame);
                if (previous == null || previous.fingerprint != frame.fingerprint || frame.protectedScreen)
                    lastChange.set(SystemClock.elapsedRealtime());
                progress.protectedScreen(frame.protectedScreen);
            } catch (Exception unavailable) {
                latest.set(null);
                lastChange.set(SystemClock.elapsedRealtime());
                progress.protectedScreen(true);
            }
        }, 0, 1500, TimeUnit.MILLISECONDS);
        try {
            while (true) {
                if (response.await(1500, TimeUnit.MILLISECONDS)) return outcome;
                EvidenceFrame frame = latest.get();
                if (frame == null || frame.protectedScreen || SystemClock.elapsedRealtime() - lastChange.get() < 2000) continue;
                long epoch = lastChange.get();
                ResultVerifier.Result atBoundary = verifier.verifyBoundary(ctx, boundary);
                if (outcome != null) return outcome;
                if (epoch != lastChange.get()) continue;
                if (!atBoundary.verified && atBoundary.assessed) {
                    ResultVerifier.Result ready = verifier.verifyReady(ctx, boundary);
                    if (outcome != null) return outcome;
                    if (epoch == lastChange.get() && ready.verified) {
                        synchronized (this) {
                            if (outcome != null) return outcome;
                            outcome = "done";
                        }
                        ctx.logs.task(ctx.taskId, "handoff automatically resumed: " + ready.evidence);
                        return "done";
                    }
                }
                // Bound repeated verifier calls while retaining 1.5-second read-only observations.
                lastChange.set(SystemClock.elapsedRealtime());
            }
        } finally { observer.shutdownNow(); }
    }
}
