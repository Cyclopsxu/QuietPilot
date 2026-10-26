package com.silentauto.shell.ext.guard;

import com.google.gson.*;
import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.ext.*;
import com.silentauto.shell.ext.skill.UiSnapshot;
import java.util.*;

/** Per-task bounded repetition detector. Skill playback never contributes observations. */
public final class LoopDetector implements StepListener {
    private final Deque<String[]> recent = new ArrayDeque<>();
    private int triggers;
    private String feedback = "";
    @Override public void afterAction(ExecutionContext ctx, int step, ActionDecision action, StepResult result, String dump) {
        record(dump, action);
    }
    public void reset() { recent.clear(); triggers=0; feedback=""; }
    public void record(String dump, ActionDecision action) {
        if ("skill".equals(action.source)) { recent.clear(); return; }
        if (Arrays.asList("done","report_answer","report_not_found","request_user").contains(action.action)) return;
        UiSnapshot ui;
        try { ui = new UiSnapshot(dump); } catch (RuntimeException unavailable) { recent.clear(); return; }
        // Full visible semantics, including result text; exclude timestamps, focus and volatile node indices.
        StringBuilder screen = new StringBuilder();
        if(ui.dump.has("ocr"))screen.append(ui.dump.get("ocr"));
        for (JsonObject node : ui.nodes) for (String key : new String[]{"package","id","class","text","desc"})
            screen.append(UiSnapshot.text(node,key)).append('\u0000');
        JsonObject target=ui.target(action);
        String feature=target==null ? action.inputIndex+":"+action.targetIndex+":"+action.x+":"+action.y : target.toString();
        String operation=action.action+":"+feature+":"+action.key+":"+action.direction+":"+action.text;
        recent.addLast(new String[]{screen.toString(),operation,action.action});
        while(recent.size()>6)recent.removeFirst();
        List<String[]> list=new ArrayList<>(recent);String[] last=list.get(list.size()-1);int same=0;
        for(String[] item:list)if(item[0].equals(last[0])&&item[1].equals(last[1]))same++;
        boolean alternating=false;
        if(list.size()>=4){int n=list.size();alternating=list.get(n-4)[0].equals(list.get(n-2)[0])
            &&list.get(n-3)[0].equals(list.get(n-1)[0])&&!list.get(n-4)[0].equals(list.get(n-3)[0]);}
        if(same>=3||alternating){
            triggers++;StringJoiner actions=new StringJoiner(" → ");for(String[] item:list)actions.add(item[2]);
            feedback="你在重复同样的操作（"+actions+"），当前界面可能已经满足目标。先判断目标是否已完成：完成则调用 finish / report_answer，否则换一种方法。";
            recent.clear(); // Require fresh repeated actions after the warning, not overlapping windows.
        }
    }
    public int triggers(){return triggers;}
    public String feedback(){return feedback;}
}
