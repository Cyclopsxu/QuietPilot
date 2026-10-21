package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.UiBridge;
import com.silentauto.shell.ext.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class TrajectoryRecorder implements StepListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
    private static final class Session {
        final JsonObject root=new JsonObject(); final JsonArray steps=new JsonArray();
        final long started=System.nanoTime();
        UiSnapshot before; ScreenFingerprint fingerprint; JsonObject pending;
        Session(String id,String goal){ root.addProperty("taskId",id);root.addProperty("goal",goal);root.addProperty("packageName","");root.addProperty("handedOff",false);root.add("steps",steps); }
    }
    public void prepare(String id,String goal){sessions.put(id,new Session(id,goal));}
    public void handedOff(String id){Session s=sessions.get(id);if(s!=null)synchronized(s){s.root.addProperty("handedOff",true);}}
    @Override public void onTaskStart(ExecutionContext ctx,String goal,String pkg){
        Session s=sessions.computeIfAbsent(ctx.taskId,id->new Session(id,goal));synchronized(s){s.root.addProperty("packageName",pkg);}
    }
    @Override public void beforeStep(ExecutionContext ctx,int step,String uiDump){
        Session s=sessions.get(ctx.taskId);if(s==null)return;
        synchronized(s){
            s.before=snapshot(ctx);ScreenFingerprint next=ScreenFingerprint.from(s.before,UiSnapshot.text(s.root,"packageName"),ctx.height);
            completePending(s,next);s.fingerprint=next;
        }
    }
    @Override public void afterAction(ExecutionContext ctx,int step,ActionDecision action,StepResult result,String dump){
        Session s=sessions.get(ctx.taskId);if(s==null)return;
        synchronized(s){
            JsonObject record=new JsonObject();record.addProperty("step",step);
            record.add("fingerprint",s.fingerprint.json());record.add("action",GSON.toJsonTree(action));
            JsonObject target=s.before.target(action);
            if(target==null && result!=null){
                ActionDecision indexed=new ActionDecision();
                if(result.clickResult!=null){indexed.action="tap_target";indexed.targetIndex=result.clickResult.targetIndex;target=s.before.target(indexed);}
                else if(result.focusResult!=null){indexed.action="focus_input";indexed.inputIndex=result.focusResult.inputIndex;target=s.before.target(indexed);
                    if(target==null && result.focusResult.targetIndex>0){indexed.action="tap_target";indexed.targetIndex=result.focusResult.targetIndex;target=s.before.target(indexed);}}
            }
            record.add("target",target==null?JsonNull.INSTANCE:target);
            record.addProperty("elapsedMs",(System.nanoTime()-s.started)/1_000_000);
            String stepText=action.reason==null?"":action.reason.replaceAll("\\s+"," ").trim();
            record.addProperty("stepText",stepText.codePointCount(0,stepText.length())<=20?stepText:stepText.substring(0,stepText.offsetByCodePoints(0,20)));
            record.addProperty("source",action.source);record.addProperty("reason",action.reason);record.addProperty("uiChanged",false);
            s.steps.add(record);s.pending=record;
        }
    }
    @Override public void onTaskEnd(ExecutionContext ctx,TaskOutcome outcome){
        Session s=sessions.get(ctx.taskId);if(s==null)return;
        synchronized(s){try{completePending(s,ScreenFingerprint.from(snapshot(ctx),UiSnapshot.text(s.root,"packageName"),ctx.height));}catch(Exception unavailable){if(s.pending!=null)s.pending.addProperty("observationError",unavailable.getMessage());}}
        finish(ctx.taskId,outcome);
    }
    private static UiSnapshot snapshot(ExecutionContext ctx){return new UiSnapshot(ctx.ui.dump(ctx.displayId,ctx.width,ctx.height,UiBridge.MODE_FULL));}
    private static void completePending(Session s,ScreenFingerprint next){
        if(s.pending!=null){s.pending.addProperty("uiChanged",s.fingerprint.similarity(next)<1);s.pending.add("fingerprintAfter",next.json());s.pending=null;}
    }
    public JsonObject trajectory(String id){Session s=sessions.get(id);if(s==null)return null;synchronized(s){return s.root.deepCopy();}}
    public void snapshotOutcome(String id,TaskOutcome outcome){Session s=sessions.get(id);if(s!=null)synchronized(s){save(s,outcome);}}
    public void finish(String id,TaskOutcome outcome){Session s=sessions.remove(id);if(s!=null)synchronized(s){save(s,outcome);}}
    private static void save(Session s,TaskOutcome outcome){
        s.root.add("outcome",GSON.toJsonTree(outcome));
        File directory=new File("/data/local/tmp/shadowauto/trajectories");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("cannot create trajectory directory");
        File file=new File(directory,UiSnapshot.text(s.root,"taskId")+".json");
        try(Writer output=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){GSON.toJson(s.root,output);}
        catch(IOException e){throw new IllegalStateException("cannot save trajectory",e);}
    }
}
