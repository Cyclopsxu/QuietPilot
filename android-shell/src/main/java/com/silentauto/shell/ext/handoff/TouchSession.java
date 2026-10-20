package com.silentauto.shell.ext.handoff;

import android.os.SystemClock;
import android.view.MotionEvent;
import com.silentauto.shell.InputBridge;

public final class TouchSession {
    private final InputBridge input;
    private final int displayId;
    private long downTime;
    public TouchSession(InputBridge input, int displayId) { this.input = input; this.displayId = displayId; }
    public synchronized void inject(String action, float x, float y) throws Exception {
        int code;
        if ("down".equals(action)) {
            if (downTime != 0) throw new IllegalStateException("gesture already active");
            downTime = SystemClock.uptimeMillis(); code = MotionEvent.ACTION_DOWN;
        } else {
            if (downTime == 0) throw new IllegalStateException("gesture must start with down");
            if ("move".equals(action)) code = MotionEvent.ACTION_MOVE;
            else if ("up".equals(action)) code = MotionEvent.ACTION_UP;
            else if ("cancel".equals(action)) code = MotionEvent.ACTION_CANCEL;
            else throw new IllegalArgumentException("action must be down, move, up or cancel");
        }
        try {
            if (!input.touch(displayId, code, x, y, downTime)) throw new IllegalStateException("touch injection rejected");
        } finally {
            if (code == MotionEvent.ACTION_UP || code == MotionEvent.ACTION_CANCEL) downTime = 0;
        }
    }
}
