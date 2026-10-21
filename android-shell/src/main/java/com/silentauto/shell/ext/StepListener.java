package com.silentauto.shell.ext;

import com.silentauto.shell.ActionDecision;

public interface StepListener {
    default void onTaskStart(ExecutionContext ctx, String goal, String packageName) {}
    default void beforeStep(ExecutionContext ctx, int step, String uiDump) {}
    default void beforeAction(ExecutionContext ctx,int step,ActionDecision action) {}
    default void afterAction(ExecutionContext ctx, int step, ActionDecision action, StepResult result, String uiDumpBefore) {}
    default void onTaskEnd(ExecutionContext ctx, TaskOutcome outcome) {}
}
