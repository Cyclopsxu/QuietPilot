package com.silentauto.shell.ext.handoff;

/** Explicit preferences override the verifier's semantic result delivery decision. */
public final class ResultRetention {
    private ResultRetention() {}
    public static boolean requested(String goal) { return requested(goal,"view"); }
    public static boolean requested(String goal,String delivery) {
        if(goal!=null) {
            if(goal.matches("(?si).*(不要|无需|不必|不需要)(停留|停在|留在|保留).*")) return false;
            if(goal.matches("(?si).*(停留|停在|留在|保留).*(页面|界面|详情|应用|app).*")) return true;
        }
        // Action results remain available until the user reviews or explicitly dismisses them.
        return true;
    }
}
