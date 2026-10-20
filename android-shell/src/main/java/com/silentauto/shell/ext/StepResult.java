package com.silentauto.shell.ext;

import com.silentauto.shell.UiBridge;

public final class StepResult {
    public final UiBridge.ClickResult clickResult;
    public final UiBridge.FocusResult focusResult;

    public StepResult(UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        this.clickResult = clickResult;
        this.focusResult = focusResult;
    }
}
