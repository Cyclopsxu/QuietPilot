package com.silentauto.shell.ext.guard;

import com.silentauto.shell.ext.skill.UiSnapshot;
import com.google.gson.JsonObject;
import java.util.regex.*;

/** Conservative focus-lease gate. It observes metadata, never records the user's text. */
public final class MainInputIdleGuard {
    public static final long QUIET_MS=2000;
    public static final class Busy extends Exception {
        public final boolean resetQuiet;
        public Busy(String reason){this(reason,true);}
        public Busy(String reason,boolean resetQuiet){super(reason);this.resetQuiet=resetQuiet;}
    }
    private long quietSince=-1;
    private String reason="尚未确认主屏输入状态";
    public String reason(){return reason;}

    public boolean observe(String ui,String ime,String input,long now) {
        String blocked=blocked(ui,ime,input);
        if(!blocked.isEmpty()){quietSince=-1;reason=blocked;return false;}
        long age=recentMainInputAge(input);
        if(age>=0&&age<QUIET_MS){quietSince=-1;reason="主屏刚有触摸或按键";return false;}
        if(quietSince<0)quietSince=now;
        if(now-quietSince<QUIET_MS){reason="等待主屏连续空闲 2 秒";return false;}
        reason="";return true;
    }

    private static String blocked(String ui,String ime,String input) {
        Matcher shown=Pattern.compile("\\bmInputShown=(true|false)\\b").matcher(ime);
        if(!shown.find()||!input.contains("RecentQueue:"))return "主屏输入状态不可读";
        if(Boolean.parseBoolean(shown.group(1)))return "主屏键盘尚未收起";
        for(String line:input.split("\n"))
            if(mainEvent(line)&&!Pattern.compile("age=([0-9.]+)ms").matcher(line).find())return "主屏输入时间不可读";
        try {
            UiSnapshot snapshot=new UiSnapshot(ui);
            if(UiSnapshot.bool(snapshot.dump,"rootsUnavailable")||snapshot.nodes.isEmpty())return "主屏控件不可读";
            boolean app=false;
            for(var value:UiSnapshot.array(snapshot.dump,"windows")) {
                JsonObject window=value.getAsJsonObject();
                if(UiSnapshot.integer(window,"type")==1&&window.has("root")&&window.get("root").isJsonObject()
                        &&UiSnapshot.bool(window.getAsJsonObject("root"),"visible"))app=true;
            }
            if(!app)return "主屏应用窗口不可读";
            for(JsonObject node:snapshot.nodes)
                if(UiSnapshot.bool(node,"editable")&&UiSnapshot.bool(node,"focused"))return "主屏输入框仍在编辑";
        } catch(RuntimeException unreadable){return "主屏控件不可读";}
        return "";
    }

    public static long recentMainInputAge(String input) {
        long youngest=-1;
        for(String line:input.split("\n")) {
            if(!mainEvent(line))continue;
            Matcher age=Pattern.compile("age=([0-9.]+)ms").matcher(line);
            if(age.find()) {
                long value=(long)Double.parseDouble(age.group(1));
                if(youngest<0||value<youngest)youngest=value;
            }
        }
        return youngest;
    }

    public static void checkLease(String input,long leaseStarted,long now)throws Busy {
        if(!input.contains("RecentQueue:"))throw new Busy("主屏输入记录不可读，归还焦点");
        for(String line:input.split("\n"))
            if(mainEvent(line)&&!Pattern.compile("age=([0-9.]+)ms").matcher(line).find())throw new Busy("主屏输入时间不可读，归还焦点");
        long age=recentMainInputAge(input);
        // Allow for the small delay between the dumpsys sample and this check.
        if(age>=0&&age<=now-leaseStarted+100)throw new Busy("检测到主屏操作，归还焦点");
    }

    private static boolean mainEvent(String line) {
        return (line.contains("MotionEvent")||line.contains("KeyEvent"))
                &&Pattern.compile("displayId=0(?:[, )]|$)").matcher(line).find();
    }
}
