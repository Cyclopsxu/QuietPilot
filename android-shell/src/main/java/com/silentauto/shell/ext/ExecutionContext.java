package com.silentauto.shell.ext;

import com.silentauto.shell.UiBridge;
import com.silentauto.shell.InputBridge;
import com.silentauto.shell.ClipboardBridge;
import com.silentauto.shell.LogHub;

public final class ExecutionContext {
    public final String taskId;
    public final int displayId, width, height;
    public final UiBridge ui;
    public final InputBridge input;
    public final ClipboardBridge clipboard;
    public final LogHub logs;

    public ExecutionContext(String taskId, int displayId, int width, int height,
            UiBridge ui, InputBridge input, ClipboardBridge clipboard, LogHub logs) {
        this.taskId = taskId;
        this.displayId = displayId;
        this.width = width;
        this.height = height;
        this.ui = ui;
        this.input = input;
        this.clipboard = clipboard;
        this.logs = logs;
    }
}
