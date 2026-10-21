package com.silentauto.shell.ext.metrics;

import com.google.gson.*;

public final class DisturbanceCounters {
    public int switches,focusLosses,imeSteals,userSwitches,unknownSwitches,samples;
    public boolean foregroundAvailable=true,focusAvailable=true,imeAvailable=true;
    public final JsonArray events=new JsonArray();
    private String previousForeground;private boolean wasFocus,wasIme;private long lastTouch=Long.MIN_VALUE/2;
    public DisturbanceCounters(String initial){previousForeground=initial;}
    public void sample(DisturbanceReadings reading,int virtualDisplay,long now,long elapsed,int step,String action,String source){
        samples++;
        if(reading.touchAgeMs>=0)lastTouch=Math.max(lastTouch,now-reading.touchAgeMs);
        if(reading.foreground.isEmpty())foregroundAvailable=false;
        else{
            if(!previousForeground.isEmpty()&&!previousForeground.equals(reading.foreground)){
                if(now-lastTouch<=1000)userSwitches++;
                else if(reading.touchHistoryAvailable){switches++;event("foreground",elapsed,step,action,source,"无最近触摸的主屏变化："+previousForeground+" → "+reading.foreground);}
                else{unknownSwitches++;event("foreground",elapsed,step,action,source,"触摸历史不可用，无法归因的主屏变化");}
            }
            previousForeground=reading.foreground;
        }
        if(reading.focusedDisplay<0)focusAvailable=false;
        boolean focus=virtualDisplay>=0&&reading.focusedDisplay==virtualDisplay;
        if(focus&&!wasFocus){focusLosses++;event("focus",elapsed,step,action,source,"主焦点切至虚拟屏 "+virtualDisplay);}wasFocus=focus;
        if(reading.imeDisplay<0)imeAvailable=false;
        boolean ime=virtualDisplay>=0&&reading.imeDisplay==virtualDisplay;
        if(ime&&!wasIme){imeSteals++;event("ime",elapsed,step,action,source,"输入法目标切至虚拟屏 "+virtualDisplay);}wasIme=ime;
    }
    public void event(String metric,long elapsed,int step,String action,String source,String detail){
        if(events.size()>=100)return;JsonObject item=new JsonObject();item.addProperty("metric",metric);item.addProperty("atMs",System.currentTimeMillis());item.addProperty("elapsedMs",elapsed);item.addProperty("step",step);item.addProperty("action",action);item.addProperty("source",source);item.addProperty("detail",detail);events.add(item);
    }
    public static JsonObject metric(String key,String label,Number value,boolean available,boolean pass,String reason){JsonObject item=new JsonObject();item.addProperty("key",key);item.addProperty("label",label);item.addProperty("value",value);item.addProperty("available",available);item.addProperty("pass",available&&pass);item.addProperty("reason",reason);return item;}
}
