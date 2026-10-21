package com.silentauto.shell.ext;

import com.silentauto.shell.ext.guard.ClipboardGuard;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.util.Locale;
import com.silentauto.shell.*;
import com.silentauto.shell.AutomationEngine.TaskState;

public final class ActionExecutor {
    private final LogHub logs;

    public ActionExecutor(LogHub logs) {
        this.logs = logs;
    }

    public StepResult execute(ExecutionContext ctx, TaskState task, ActionDecision action) throws Exception {
        ctx.input.checkAllowed();
        UiBridge.ClickResult clickResult = null;
        UiBridge.FocusResult focusResult = null;
        if ("tap_target".equals(action.action)) {
            clickResult = tapTarget(ctx.taskId, ctx.ui, ctx.input, ctx.displayId, ctx.width, ctx.height, action.targetIndex);
        } else if ("focus_input".equals(action.action)) {
            focusResult = focusInput(ctx.taskId, ctx.ui, ctx.input, ctx.displayId, ctx.width, ctx.height, action.inputIndex, action.query);
        } else if ("tap".equals(action.action)) {
            int x = clamp(action.x, 0, ctx.width - 1);
            int y = clamp(action.y, 0, ctx.height - 1);
            if (x != action.x || y != action.y) {
                logs.task(ctx.taskId, "tap adjusted to display bounds: " + x + "," + y);
            }
            logs.task(ctx.taskId, "tap inject display-local " + x + "," + y + " on " + ctx.width + "x" + ctx.height);
            ctx.input.tap(ctx.displayId, x, y);
        } else if ("long_press".equals(action.action)) {
            longPress(ctx.taskId, ctx.input, ctx.displayId, ctx.width, ctx.height, action.x, action.y, action.durationMs);
        } else if ("drag".equals(action.action)) {
            drag(ctx.taskId, ctx.input, ctx.displayId, ctx.width, ctx.height, action);
        } else if ("scroll".equals(action.action)) {
            scrollUi(ctx.taskId, ctx.input, ctx.displayId, ctx.width, ctx.height, action.direction, action.distance);
        } else if ("text".equals(action.action)) {
            if (action.inputIndex > 0) {
                focusResult = focusInput(ctx.taskId, ctx.ui, ctx.input, ctx.displayId, ctx.width, ctx.height, action.inputIndex, "");
                if (focusResult == null) throw new IllegalStateException("Skill input target disappeared");
            }
            inputText(ctx.taskId, ctx.ui, ctx.input, ctx.clipboard, ctx.displayId, action.text);
        } else if ("set_clipboard".equals(action.action)) {
            task.agentClipboard = action.text;
            logs.task(ctx.taskId, "clipboard set");
        } else if ("paste".equals(action.action)) {
            ClipboardGuard.withTemporaryClip(ctx.clipboard, task.agentClipboard,
                    () -> pasteClipboard(ctx.ui, ctx.input, ctx.displayId));
            logs.task(ctx.taskId, "clipboard restored");
        } else if ("copy".equals(action.action)) {
            ClipboardGuard.preservingClipboard(ctx.clipboard, () -> {
                if (ctx.ui.copySelection(ctx.displayId)) {
                    task.agentClipboard = ctx.clipboard.getText();
                    logs.task(ctx.taskId, "copied selection: " + truncate(task.agentClipboard, 80));
                } else {
                    logs.task(ctx.taskId, "copy selection failed");
                }
                return null;
            });
            logs.task(ctx.taskId, "clipboard restored");
        } else if ("select_all".equals(action.action)) {
            if (ctx.ui.selectAll(ctx.displayId)) {
                logs.task(ctx.taskId, "selected all text by accessibility");
            } else {
                ctx.input.selectAll(ctx.displayId);
                logs.task(ctx.taskId, "selected all text by key events");
            }
        } else if ("delete".equals(action.action)) {
            if (ctx.ui.deleteSelection(ctx.displayId)) {
                logs.task(ctx.taskId, "deleted selected text by accessibility");
            } else {
                ctx.input.delete(ctx.displayId);
                logs.task(ctx.taskId, "delete key sent");
            }
        } else if ("clear_text".equals(action.action)) {
            if(action.inputIndex>0){focusResult=focusInput(ctx.taskId,ctx.ui,ctx.input,ctx.displayId,ctx.width,ctx.height,action.inputIndex,"");if(focusResult==null)throw new IllegalStateException("Skill clear target disappeared");}
            clearText(ctx.taskId, ctx.ui, ctx.input, ctx.displayId);
        } else if ("back".equals(action.action)) {
            ctx.input.key(ctx.displayId, KeyEvent.KEYCODE_BACK);
        } else if ("key".equals(action.action)) {
            pressKey(ctx.taskId, ctx.input, ctx.displayId, action.key);
        } else if ("submit_search".equals(action.action)) {
            submitSearch(task, ctx.ui, ctx.input, ctx.displayId, ctx.width, ctx.height);
        } else {
            sleep(task, Math.max(300, action.ms));
        }
        return new StepResult(clickResult, focusResult);
    }

    private UiBridge.ClickResult tapTarget(String taskId, UiBridge bridge, InputBridge input, int displayId, int width, int height, int targetIndex) throws Exception {
        if (targetIndex <= 0) {
            logs.task(taskId, "tap target ignored: invalid index " + targetIndex);
            return null;
        }
        UiBridge.ClickResult result = bridge.clickTarget(displayId, targetIndex);
        if (result == null) {
            logs.task(taskId, "tap target not found: " + targetIndex);
            return null;
        }
        if (result.clicked) {
            logs.task(taskId, "tap target clicked by accessibility: #" + result.targetIndex + " " + safe(result.label));
            return result;
        }
        int x = clamp(result.x, 0, width - 1);
        int y = clamp(result.y, 0, height - 1);
        logs.task(taskId, "tap target fallback display-local " + x + "," + y + " for #" + result.targetIndex + " " + safe(result.label));
        input.tap(displayId, x, y);
        return result;
    }

    private UiBridge.FocusResult focusInput(String taskId, UiBridge bridge, InputBridge input, int displayId, int width, int height, int inputIndex, String query) throws Exception {
        UiBridge.FocusResult result = bridge.focusInput(displayId, inputIndex, query);
        if (result == null) {
            logs.task(taskId, "input not found" + (empty(query) ? "" : ": " + query));
            return null;
        }
        if (result.accessibility) {
            String index = result.inputIndex > 0 ? "input #" + result.inputIndex : "target #" + result.targetIndex;
            logs.task(taskId, "focused " + index + " by accessibility: " + safe(result.label));
            return result;
        }
        int x = clamp(result.x, 0, width - 1);
        int y = clamp(result.y, 0, height - 1);
        logs.task(taskId, "focus input fallback tap " + x + "," + y + ": " + safe(result.label));
        input.tap(displayId, x, y);
        return result;
    }

    private void inputText(String taskId, UiBridge bridge, InputBridge input, ClipboardBridge clipboard, int displayId, String text) throws Exception {
        if (bridge.setText(displayId, text)) {
            logs.task(taskId, "text set by accessibility");
            return;
        }
        boolean pasted = ClipboardGuard.withTemporaryClip(clipboard, text,
                () -> pasteClipboard(bridge, input, displayId));
        logs.task(taskId, "clipboard restored");
        if (pasted) {
            logs.task(taskId, "text pasted from clipboard");
            return;
        }
        input.text(displayId, text);
        logs.task(taskId, "text sent as key events");
    }

    private boolean pasteClipboard(UiBridge bridge, InputBridge input, int displayId) throws Exception {
        if (bridge.paste(displayId)) {
            return true;
        }
        input.paste(displayId);
        return true;
    }

    private void clearText(String taskId, UiBridge bridge, InputBridge input, int displayId) throws Exception {
        if (bridge.setText(displayId, "")) {
            logs.task(taskId, "text cleared by accessibility");
            return;
        }
        if (bridge.selectAll(displayId)) {
            bridge.deleteSelection(displayId);
            logs.task(taskId, "text cleared by selection");
            return;
        }
        input.selectAll(displayId);
        input.delete(displayId);
        logs.task(taskId, "clear text sent by key events");
    }

    private void longPress(String taskId, InputBridge input, int displayId, int width, int height, int x, int y, int durationMs) throws Exception {
        int localX = clamp(x, 0, width - 1);
        int localY = clamp(y, 0, height - 1);
        logs.task(taskId, "long press " + localX + "," + localY);
        input.longPress(displayId, localX, localY, durationMs);
    }

    private void drag(String taskId, InputBridge input, int displayId, int width, int height, ActionDecision action) throws Exception {
        int startX = clamp(action.startX, 0, width - 1);
        int startY = clamp(action.startY, 0, height - 1);
        int endX = clamp(action.endX, 0, width - 1);
        int endY = clamp(action.endY, 0, height - 1);
        int duration = action.durationMs > 0 ? action.durationMs : 450;
        logs.task(taskId, "drag " + startX + "," + startY + " -> " + endX + "," + endY);
        input.swipe(displayId, startX, startY, endX, endY, duration);
    }

    private void scrollUi(String taskId, InputBridge input, int displayId, int width, int height, String direction, int distance) throws Exception {
        String dir = empty(direction) ? "down" : direction.toLowerCase(Locale.US);
        int centerX = Math.max(1, width / 2);
        int centerY = Math.max(1, height / 2);
        int minX = Math.min(40, Math.max(0, width - 1));
        int minY = Math.min(40, Math.max(0, height - 1));
        int maxX = Math.max(minX, width - 40);
        int maxY = Math.max(minY, height - 40);
        int vertical = distance > 0 ? distance : Math.max(240, height * 3 / 5);
        int horizontal = distance > 0 ? distance : Math.max(180, width * 3 / 5);
        int startX = centerX;
        int startY = centerY;
        int endX = centerX;
        int endY = centerY;
        if ("up".equals(dir)) {
            startY = clamp(centerY - vertical / 2, minY, maxY);
            endY = clamp(startY + vertical, minY, maxY);
        } else if ("left".equals(dir)) {
            startX = clamp(centerX - horizontal / 2, minX, maxX);
            endX = clamp(startX + horizontal, minX, maxX);
        } else if ("right".equals(dir)) {
            startX = clamp(centerX + horizontal / 2, minX, maxX);
            endX = clamp(startX - horizontal, minX, maxX);
        } else {
            dir = "down";
            startY = clamp(centerY + vertical / 2, minY, maxY);
            endY = clamp(startY - vertical, minY, maxY);
        }
        logs.task(taskId, "scroll " + dir + " via swipe " + startX + "," + startY + " -> " + endX + "," + endY);
        input.swipe(displayId, startX, startY, endX, endY, 420);
    }

    private void pressKey(String taskId, InputBridge input, int displayId, String key) throws Exception {
        int keyCode = keyCode(key);
        if (keyCode == 0) {
            logs.task(taskId, "unsupported key ignored: " + safe(key));
            return;
        }
        boolean injected = input.key(displayId, keyCode);
        logs.task(taskId, "press key " + key + " displayId=" + displayId + " keyCode=" + keyCode + " injected=" + injected);
    }

    private void submitSearch(TaskState task, UiBridge bridge, InputBridge input, int displayId, int width, int height) throws Exception {
        task.searchSubmitAttempts++;
        logs.task(task.taskId, "auto submit search attempt " + task.searchSubmitAttempts);
        if (bridge.hasInputMethodWindow(displayId)) {
            int x = clamp(width - 72, 0, width - 1);
            int y = clamp(height - 72, 0, height - 1);
            boolean injected = input.tap(displayId, x, y);
            logs.task(task.taskId, "submit search by IME action area displayId=" + displayId + " " + x + "," + y + " injected=" + injected);
            clearSearchSubmitIfExhausted(task);
            return;
        }
        UiBridge.ClickResult target = bridge.clickBestSubmitTarget(displayId, width, height);
        if (target != null) {
            if (target.clicked) {
                logs.task(task.taskId, "submit target clicked by accessibility: " + safe(target.label));
            } else {
                int x = clamp(target.x, 0, width - 1);
                int y = clamp(target.y, 0, height - 1);
                boolean injected = input.tap(displayId, x, y);
                logs.task(task.taskId, "submit target fallback tap displayId=" + displayId + " " + x + "," + y + " injected=" + injected + ": " + safe(target.label));
            }
            clearSearchSubmitIfExhausted(task);
            return;
        }
        boolean injected = input.key(displayId, KeyEvent.KEYCODE_ENTER);
        logs.task(task.taskId, "submit search by enter key displayId=" + displayId + " injected=" + injected);
        clearSearchSubmitIfExhausted(task);
    }

    private void clearSearchSubmitIfExhausted(TaskState task) {
        if (task.searchSubmitAttempts >= 2 && task.pendingSearchSubmit) {
            task.pendingSearchSubmit = false;
            task.searchInputActive = false;
            logs.task(task.taskId, "search submit fallback exhausted");
        }
    }

    private int keyCode(String key) {
        String value = empty(key) ? "" : key.toLowerCase(Locale.US);
        if ("enter".equals(value)) {
            return KeyEvent.KEYCODE_ENTER;
        }
        if ("search".equals(value)) {
            return KeyEvent.KEYCODE_SEARCH;
        }
        if ("tab".equals(value)) {
            return KeyEvent.KEYCODE_TAB;
        }
        if ("dpad_up".equals(value)) {
            return KeyEvent.KEYCODE_DPAD_UP;
        }
        if ("dpad_down".equals(value)) {
            return KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if ("dpad_left".equals(value)) {
            return KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if ("dpad_right".equals(value)) {
            return KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if ("dpad_center".equals(value)) {
            return KeyEvent.KEYCODE_DPAD_CENTER;
        }
        return 0;
    }

    private void sleep(TaskState task, long ms) {
        if (!task.stop.get()) {
            SystemClock.sleep(ms);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
