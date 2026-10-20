package com.silentauto.shell.ext;

import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.AutomationEngine.TaskState;

public interface ActionSource {
    /** Return null to let the next source or the model handle this step. */
    ActionDecision next(ExecutionContext ctx, TaskState task, int step, String uiDump);
}
