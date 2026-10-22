package com.silentauto.shell.ext.metrics;

import com.google.gson.*;
import java.util.*;

public final class DisturbanceCounters {
    public int switches,focusLosses,imeSteals,userSwitches,unknownSwitches,samples,totalSwitches,authorizedSwitches;
    public boolean foregroundAvailable=true,focusAvailable=true,imeAvailable=true,eventsTruncated;
    public final JsonArray events=new JsonArray();
    public final Map<String,Integer> foregroundSamples=new LinkedHashMap<>();
    private String previousForeground;private boolean wasFocus,wasIme;private long lastTouch=Long.MIN_VALUE/2,lastTransitionAt;
    private JsonObject lastTransition;
    private final List<Authorization> authorizations=new ArrayList<>();
    private static final class Authorization {
        String from,to,reason;long until;
        Authorization(String from,String to,String reason,long now){this.from=from;this.to=to;this.reason=reason;until=now+8000;}
    }
    public DisturbanceCounters(String initial){previousForeground=initial;}
    public synchronized void authorize(String from,String to,String reason,long now){
        authorizations.removeIf(a->a.until<now);
        if(from.equals(to)||from.isEmpty()||to.isEmpty())return;
        if(lastTransition!=null&&now-lastTransitionAt<=1500&&from.equals(lastTransition.get("from").getAsString())&&to.equals(lastTransition.get("to").getAsString())){
            String old=lastTransition.get("attribution").getAsString();
            if(!old.equals("explicit_handoff")){
                if(old.equals("touch_correlated"))userSwitches--;else if(old.equals("unknown"))unknownSwitches--;else switches--;
                authorizedSwitches++;lastTransition.addProperty("attribution","explicit_handoff");lastTransition.addProperty("handoffReason",reason);
            }
            return;
        }
        if(authorizations.size()>=8)authorizations.remove(0);
        authorizations.add(new Authorization(from,to,reason,now));
    }
    public synchronized void authorizeEntry(String to,long now){
        String from=lastTransition!=null&&now-lastTransitionAt<=1500&&to.equals(lastTransition.get("to").getAsString())?lastTransition.get("from").getAsString():previousForeground;
        authorize(from,to,"user_opened_handoff",now);
    }
    public synchronized void sample(DisturbanceReadings reading,int virtualDisplay,long now,long elapsed,int step,String action,String source){
        samples++;
        if(reading.touchAgeMs>=0)lastTouch=Math.max(lastTouch,now-reading.touchAgeMs);
        if(reading.foreground.isEmpty())foregroundAvailable=false;
        else{
            foregroundSamples.merge(reading.foreground,1,Integer::sum);
            if(!previousForeground.isEmpty()&&!previousForeground.equals(reading.foreground)){
                totalSwitches++;authorizations.removeIf(a->a.until<now);Authorization matched=null;
                for(Authorization a:authorizations)if(a.from.equals(previousForeground)&&a.to.equals(reading.foreground)){matched=a;break;}
                String attribution;
                if(matched!=null){authorizedSwitches++;authorizations.remove(matched);attribution="explicit_handoff";}
                else if(now-lastTouch<=1000){userSwitches++;attribution="touch_correlated";}
                else if(reading.touchHistoryAvailable){switches++;attribution="unattributed";}
                else{unknownSwitches++;attribution="unknown";}
                JsonObject event=record("foreground",elapsed,step,action,source,"主屏变化："+previousForeground+" → "+reading.foreground);
                event.addProperty("from",previousForeground);event.addProperty("to",reading.foreground);event.addProperty("attribution",attribution);
                if(matched!=null)event.addProperty("handoffReason",matched.reason);
                lastTransition=event;lastTransitionAt=now;
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
    private JsonObject record(String metric,long elapsed,int step,String action,String source,String detail){
        JsonObject item=new JsonObject();item.addProperty("metric",metric);item.addProperty("atMs",System.currentTimeMillis());item.addProperty("elapsedMs",elapsed);item.addProperty("step",step);item.addProperty("action",action);item.addProperty("source",source);item.addProperty("detail",detail);
        if(events.size()<100)events.add(item);else eventsTruncated=true;return item;
    }
    public synchronized void event(String metric,long elapsed,int step,String action,String source,String detail){record(metric,elapsed,step,action,source,detail);}
    public static JsonObject metric(String key,String label,Number value,boolean available,boolean pass,String reason){JsonObject item=new JsonObject();item.addProperty("key",key);item.addProperty("label",label);item.addProperty("value",value);item.addProperty("available",available);item.addProperty("pass",available&&pass);item.addProperty("reason",reason);return item;}
}
