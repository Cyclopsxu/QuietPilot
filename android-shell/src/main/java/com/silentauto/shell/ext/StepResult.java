package com.silentauto.shell.ext;

import com.silentauto.shell.UiBridge;

public final class StepResult {
    public String injectionMethod="unrecorded";
    public final UiBridge.ClickResult clickResult;
    public final UiBridge.FocusResult focusResult;

    public StepResult(UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        this.clickResult = clickResult;
        this.focusResult = focusResult;
        if(clickResult!=null)injectionMethod=clickResult.clicked?"accessibility_click":"display_touch";
        if(focusResult!=null)injectionMethod=focusResult.accessibility?"accessibility_focus":"display_touch";
    }
}
