package com.silentauto.shell.ext.handoff;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class HandoffManager {
    private static final Set<String> KINDS = Set.of("payment", "login", "captcha", "confirm", "other");
    private final CountDownLatch response = new CountDownLatch(1);
    private String outcome;

    public static boolean valid(String kind, String message, String boundary) {
        return KINDS.contains(kind) && message != null && !message.trim().isEmpty()
                && boundary != null && !boundary.trim().isEmpty();
    }

    public synchronized void resume(String value) {
        if (!"done".equals(value) && !"cancel".equals(value)) {
            throw new IllegalArgumentException("outcome must be done or cancel");
        }
        if (outcome != null) throw new IllegalStateException("handoff already answered");
        outcome = value;
        response.countDown();
    }

    public String awaitResponse() throws InterruptedException {
        return response.await(10, TimeUnit.MINUTES) ? outcome : "timeout";
    }
}
