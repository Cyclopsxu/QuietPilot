package com.silentauto.shell.ext.guard;

/** Latches interference; a focus bounce must never automatically resume input. */
public final class BackgroundFocusGuard {
    private volatile String issue="";
    private volatile boolean triggered;
    public synchronized void observe(boolean backgroundRunning,int targetDisplay,int focusedDisplay) {
        if(backgroundRunning&&targetDisplay>0&&focusedDisplay==targetDisplay&&issue.isEmpty()) {
            triggered=true;
            issue="检测到后台任务抢占主屏焦点，已暂停自动操作，避免反复切屏。此设备的兼容模式不能保证完全后台；可转主屏处理或结束任务";
        }
    }
    public String issue(){return issue;}
    public boolean triggered(){return triggered;}
    public synchronized void explicitRetry(){issue="";}
}
