package com.silentauto.shell.ext.guard;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskScheduler {
    private final Map<Object, String> active = new LinkedHashMap<>();

    /** Returns null after reserving a slot, otherwise a user-facing queue reason. */
    public synchronized String tryAcquire(Object owner, String app, boolean foregroundConflict, boolean force) {
        if (active.containsKey(owner)) return null;
        if (active.size() >= 2) return "已有两个任务执行中，等待空位";
        if (active.containsValue(app)) return "同一 App 已有任务执行中，正在排队";
        if (foregroundConflict && !force) return "等你离开 " + app + " 后自动开始";
        active.put(owner, app);
        return null;
    }
    public synchronized void release(Object owner) { active.remove(owner); }
}
