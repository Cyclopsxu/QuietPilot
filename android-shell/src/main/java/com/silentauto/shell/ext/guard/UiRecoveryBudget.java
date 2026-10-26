package com.silentauto.shell.ext.guard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Stop model/input loops while roots are unavailable. One read-only app recovery per task. */
public final class UiRecoveryBudget {
    public enum Next { READY, WAIT, RECONNECT, RESTART_QUERY, HANDOFF }
    /** A root stub with missing children is not a usable observation; a genuine leaf is. */
    public static boolean incompleteTraversal(int windows, int readableNodes, int nullChildren,
                                              boolean hasControls) {
        return windows > 0 && readableNodes == windows && nullChildren > 0 && !hasControls;
    }
    public static boolean placeholderRoot(boolean visible,boolean emptyBounds,int children,boolean usefulContent) {
        return !visible&&emptyBounds&&children==0&&!usefulContent;
    }
    private int missing;
    private boolean restarted,reconnected;
    public boolean reconnectAttempted(){return reconnected;}
    public Next observe(String dump,boolean query) {
        JsonObject ui=JsonParser.parseString(dump).getAsJsonObject();
        boolean unavailable=ui.has("rootsUnavailable")&&ui.get("rootsUnavailable").getAsBoolean();
        for(com.google.gson.JsonObject node:new com.silentauto.shell.ext.skill.UiSnapshot(dump).nodes) {
            String id=com.silentauto.shell.ext.skill.UiSnapshot.text(node,"id");
            if(id.equals("android:id/aerr_wait")||id.equals("android:id/aerr_close"))unavailable=true;
        }
        if(!unavailable){missing=0;return Next.READY;}
        if(++missing<3)return Next.WAIT;
        missing=0;
        if(!reconnected){reconnected=true;return Next.RECONNECT;}
        if(query&&!restarted){restarted=true;return Next.RESTART_QUERY;}
        return Next.HANDOFF;
    }
}
