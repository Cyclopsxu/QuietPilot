package com.silentauto.shell.ext.guard;

/** A display id alone does not keep native key injection from changing global focus. */
public final class BackgroundKeyPolicy {
    private BackgroundKeyPolicy() {}
    public static final class Blocked extends IllegalStateException {
        public Blocked() { super("BACKGROUND_KEY_BLOCKED：此虚拟屏未确认独立焦点，原生按键可能抢占主屏。请重新观察，点击页面上实际可见的搜索按钮、地点候选或返回按钮；不要重复发送按键。无法操作时请求主屏人工处理。"); }
    }
    public static void requireAllowed(int displayId, boolean independentlyIsolated) {
        if (displayId > 0 && !independentlyIsolated) throw new Blocked();
    }
}
