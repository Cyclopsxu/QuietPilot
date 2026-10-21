package com.silentauto.shell.ext.handoff;

/** Explicit requests to leave the final page open require user acknowledgement. */
public final class ResultRetention {
    private ResultRetention() {}
    public static boolean requested(String goal) {
        if (goal == null) return false;
        return goal.matches("(?s).*(停留|停在|留在|保留).*(页面|界面|详情|应用|App).*")
                && !goal.matches("(?s).*(不要|无需|不必)(停留|停在|留在|保留).*");
    }
}
