package com.silentauto.shell.ext;

import com.silentauto.shell.ActionDecision;

public interface StepListener {
    default void beforeStep(ExecutionContext ctx, int step, String uiDump) {}
    default void afterAction(ExecutionContext ctx, int step, ActionDecision action, StepResult result, String uiDumpBefore) {}
    default void onTaskEnd(ExecutionContext ctx, boolean success, String reason) {}
}
