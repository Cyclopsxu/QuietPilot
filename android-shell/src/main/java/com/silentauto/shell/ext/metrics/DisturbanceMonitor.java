package com.silentauto.shell.ext.metrics;

import android.content.ClipData;
import com.google.gson.*;
import com.silentauto.shell.*;
import com.silentauto.shell.ext.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Sampling runs off task/UI threads. gfxinfo is queried exactly once before and once after. */
public final class DisturbanceMonitor implements StepListener {
    private final ConcurrentHashMap<String,Session> active=new ConcurrentHashMap<>();
    private final ScheduledExecutorService killers=Executors.newSingleThreadScheduledExecutor(low("metrics-timeouts"));
    private static ThreadFactory low(String name){return r->{Thread t=new Thread(r,name);t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;};}
    private final class Session {
        final String id;final ClipboardBridge clipboard;final long started=System.nanoTime();
        final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(low("disturbance-sampler"));
        volatile DisturbanceCounters counts;String mainPackage="",clipBefore;long[] framesBefore;
        volatile int display=-1,step;volatile String action="准备",source="model";
        volatile boolean backgroundRunning;
        volatile long inputFocusFrom,inputFocusUntil;int inputFocusTransactions;
        final JsonArray focusSamples=new JsonArray();
        boolean focusSamplesTruncated;
        final com.silentauto.shell.ext.guard.BackgroundFocusGuard safety=new com.silentauto.shell.ext.guard.BackgroundFocusGuard();
        ScheduledFuture<?> polling;long sampleNanos,maxNanos,nextEligible;int skipped;String error="";
        Session(String id,ClipboardBridge clipboard){this.id=id;this.clipboard=clipboard;}
        long elapsed(){return (System.nanoTime()-started)/1_000_000;}
        void baseline(){try{
            DisturbanceReadings reading=reading();mainPackage=reading.foreground;counts=new DisturbanceCounters(mainPackage);
            counts.foregroundAvailable=!mainPackage.isEmpty();
            try{clipBefore=clip(clipboard);}catch(Exception unavailable){clipBefore=null;}
            if(mainPackage.matches("[A-Za-z0-9_.]+"))framesBefore=DisturbanceReadings.frames(read("dumpsys","gfxinfo",mainPackage));
        }catch(Exception failure){error=failure.getClass().getSimpleName();if(counts==null)counts=new DisturbanceCounters("");}}
        DisturbanceReadings reading()throws Exception{
            String windows=read("dumpsys","window"),input=read("dumpsys","input"),ime=read("dumpsys","input_method");
            DisturbanceReadings result=DisturbanceReadings.parse(windows,input,ime);
            if(backgroundRunning){
                JsonObject row=new JsonObject();row.addProperty("atMs",System.currentTimeMillis());row.addProperty("elapsedMs",elapsed());
                row.addProperty("step",step);row.addProperty("action",action);row.addProperty("source",source);
                row.addProperty("foreground",result.foreground);row.addProperty("mainFocusAvailable",!result.mainFocus.isEmpty()&&!result.mainFocus.endsWith("=null"));row.addProperty("focusedDisplay",result.focusedDisplay);row.addProperty("imeDisplay",result.imeDisplay);
                JsonArray windowLines=new JsonArray(),imeLines=new JsonArray();
                for(String line:windows.split("\n"))if(line.contains("mCurrentFocus=")||line.contains("mTopFocusedDisplay"))windowLines.add(line.trim());
                for(String line:ime.split("\n"))if(line.contains("mCurClient=")||line.contains("mCurFocusedWindow=")||line.contains("mCurTokenDisplayId=")||line.contains("mDisplayIdToShowIme="))imeLines.add(line.trim());
                row.add("window",windowLines);row.add("inputMethod",imeLines);
                try{row.addProperty("clipboardDigest",clip(clipboard));}catch(Exception unavailable){row.addProperty("clipboardUnavailable",true);}
                if(focusSamples.size()<7200)focusSamples.add(row);else focusSamplesTruncated=true;
            }
            return result;
        }
        void sample(){
            long begin=System.nanoTime();if(begin<nextEligible){skipped++;return;}
            try{
                DisturbanceReadings current=reading();
                counts.sample(current,display,System.nanoTime()/1_000_000,elapsed(),step,action,source);
                // Attribute a sample by interval: its window read may precede lease cleanup.
                boolean expectedInput=begin<=inputFocusUntil&&System.nanoTime()>=inputFocusFrom&&inputFocusFrom>0&&current.focusedDisplay==display;
                String before=safety.issue();safety.observe(backgroundRunning&&!expectedInput,display,current.focusedDisplay);
                if(before.isEmpty()&&!safety.issue().isEmpty())counts.event("background_safety",elapsed(),step,action,source,safety.issue());
            }
            catch(Exception failure){error=failure.getClass().getSimpleName();counts.foregroundAvailable=false;counts.focusAvailable=false;counts.imeAvailable=false;}
            long duration=System.nanoTime()-begin;sampleNanos+=duration;maxNanos=Math.max(maxNanos,duration);
            if(duration>450_000_000)nextEligible=System.nanoTime()+500_000_000; // Back off rather than catch up in a tight loop.
        }
        JsonObject finish(){
            sample();boolean clipboardAvailable=true,changed=false;try{changed=clipBefore!=null&&!clipBefore.equals(clip(clipboard));if(clipBefore==null)clipboardAvailable=false;}catch(Exception failure){clipboardAvailable=false;}
            long[] after=null;try{if(!mainPackage.isEmpty())after=DisturbanceReadings.frames(read("dumpsys","gfxinfo",mainPackage));}catch(Exception ignored){}
            boolean frameAvailable=framesBefore!=null&&after!=null&&after[0]>=framesBefore[0]&&after[1]>=framesBefore[1]&&after[1]-framesBefore[1]<=after[0]-framesBefore[0];
            long frames=frameAvailable?after[0]-framesBefore[0]:0,janky=frameAvailable?after[1]-framesBefore[1]:0;double rate=frames==0?0:janky/(double)frames;
            if(changed)counts.event("clipboard",elapsed(),step,action,source,"主屏剪贴板开始/结束内容不同（可能包含用户编辑）");
            if(frameAvailable&&rate>.05)counts.event("jank",elapsed(),step,action,source,"主屏新增帧掉帧率超过 5%："+janky+"/"+frames);
            JsonArray metrics=new JsonArray();
            metrics.add(DisturbanceCounters.metric("foreground","未归因前台切换",counts.switches,counts.foregroundAvailable&&counts.unknownSwitches==0,counts.switches==0,"原始切换 "+counts.totalSwitches+"；显式交接关联 "+counts.authorizedSwitches+"；触摸关联 "+counts.userSwitches+"；数据不足 "+counts.unknownSwitches+"（关联不等于因果证明）"));
            metrics.add(DisturbanceCounters.metric("focus","焦点丢失次数",counts.focusLosses,counts.focusAvailable,counts.focusLosses==0,"主焦点指向本任务虚拟屏的次数"));
            metrics.add(DisturbanceCounters.metric("ime","键盘被抢次数",counts.imeSteals,counts.imeAvailable,counts.imeSteals==0,"活动输入法目标变为本任务虚拟屏"));
            metrics.add(DisturbanceCounters.metric("clipboard","剪贴板内容变化",changed?1:0,clipboardAvailable,!changed,clipboardAvailable?"仅比较摘要，不保存剪贴板内容":"剪贴板读取不可用"));
            JsonObject frame=DisturbanceCounters.metric("jank","指定 App 掉帧率",rate,frameAvailable&&frames>0,rate<=.05,frameAvailable?(frames==0?"本次主屏无新增帧，无法评估掉帧率":"新增卡顿帧 "+janky+" / "+frames+"，阈值 5%") : "gfxinfo 不可用或计数重置");frame.addProperty("measuredPackage",mainPackage);frame.addProperty("scope","task_start_foreground_package_process");frame.addProperty("foregroundSamples",counts.foregroundSamples.getOrDefault(mainPackage,0));frame.addProperty("totalSamples",counts.samples);frame.addProperty("coversOtherApps",false);frame.addProperty("frames",frames);frame.addProperty("jankyFrames",janky);frame.addProperty("reason",frame.get("reason").getAsString()+"；仅任务开始时的 "+mainPackage+"，不代表其他 App 或持续信息流流畅度");metrics.add(frame);
            JsonObject report=new JsonObject();report.add("focusSamples",focusSamples);report.addProperty("focusSamplesTruncated",focusSamplesTruncated);report.add("metrics",metrics);report.add("events",counts.events);report.addProperty("foregroundPackage",mainPackage);report.addProperty("samples",counts.samples);
            report.addProperty("backgroundSafetyIssue",safety.issue());
            report.addProperty("inputFocusTransactions",inputFocusTransactions);
            report.addProperty("backgroundSafetyTriggered",safety.triggered());
            report.addProperty("sampleIntervalMs",500);report.addProperty("avgSampleMs",counts.samples==0?0:sampleNanos/1_000_000.0/counts.samples);report.addProperty("maxSampleMs",maxNanos/1_000_000.0);report.addProperty("skippedTicks",skipped);
            report.addProperty("touchMethod","dumpsys input / RecentQueue / displayId=0 MotionEvent / age");report.addProperty("rawForegroundSwitches",counts.totalSwitches);report.addProperty("authorizedHandoffSwitches",counts.authorizedSwitches);report.addProperty("unattributedForegroundSwitches",counts.switches);report.addProperty("unknownForegroundSwitches",counts.unknownSwitches);report.addProperty("userForegroundSwitches",counts.userSwitches);report.addProperty("eventsTruncated",counts.eventsTruncated);report.addProperty("zeroDisturbanceVerified",false);report.addProperty("assessmentScope","采样观察与显式交接关联，非全程无打扰认证");report.addProperty("samplingError",error);return report;
        }
    }
    public String backgroundIssue(String id){Session s=active.get(id);return s==null?"":s.safety.issue();}
    public void inputFocus(String id,boolean begin,boolean restored){
        Session s=active.get(id);if(s==null)return;
        if(begin){s.inputFocusFrom=System.nanoTime();s.inputFocusUntil=s.inputFocusFrom+10_000_000_000L;}
        else s.inputFocusUntil=Math.min(s.inputFocusUntil,System.nanoTime());
        if(begin)s.inputFocusTransactions++;
        if(s.counts!=null)s.counts.event("input_focus_transaction",s.elapsed(),s.step,s.action,s.source,
                begin?"兼容输入：短时取得虚拟屏焦点，页面不转主屏":restored?"兼容输入结束：主屏焦点已核对恢复":"兼容输入异常：主屏焦点未恢复");
    }
    public void backgroundRunning(String id,boolean running){Session s=active.get(id);if(s!=null)s.backgroundRunning=running;}
    public void explicitBackgroundRetry(String id){Session s=active.get(id);if(s!=null)s.safety.explicitRetry();}
    public void handoffEntry(String id){Session s=active.get(id);if(s!=null&&s.counts!=null)s.counts.authorizeEntry("com.silentauto.controller",System.nanoTime()/1_000_000);}
    public void handoffTarget(String id,String target,boolean returning){
        Session s=active.get(id);if(s==null||s.counts==null)return;long now=System.nanoTime()/1_000_000;
        if(returning){s.counts.authorize(target,"com.silentauto.controller","user_return_to_background",now);s.counts.authorize(target,s.mainPackage,"user_return_to_background",now);}
        else{s.counts.authorize("com.silentauto.controller",target,"user_take_on_main",now);s.counts.authorize(s.mainPackage,target,"user_take_on_main",now);}
    }
    public void prepare(String id,ClipboardBridge clipboard){Session session=new Session(id,clipboard);active.put(id,session);try{session.sampler.submit(session::baseline).get(3,TimeUnit.SECONDS);}catch(Exception error){session.error="baseline timeout/unavailable";if(error instanceof InterruptedException)Thread.currentThread().interrupt();}}
    @Override public void onTaskStart(ExecutionContext ctx,String goal,String pkg){Session session=active.get(ctx.taskId);if(session==null)return;session.display=ctx.displayId;session.backgroundRunning=true;session.polling=session.sampler.scheduleAtFixedRate(session::sample,0,500,TimeUnit.MILLISECONDS);}
    @Override public void beforeStep(ExecutionContext ctx,int step,String dump){Session session=active.get(ctx.taskId);if(session!=null){session.step=step;session.action="观察/规划";}}
    @Override public void beforeAction(ExecutionContext ctx,int step,ActionDecision action){Session session=active.get(ctx.taskId);if(session!=null){session.step=step;session.action=action.action;session.source=action.source;}}
    @Override public void afterAction(ExecutionContext ctx,int step,ActionDecision action,StepResult result,String dump){Session session=active.get(ctx.taskId);if(session!=null)session.action=action.action+" 后观察";}
    @Override public void onTaskEnd(ExecutionContext ctx,TaskOutcome outcome){Session session=active.get(ctx.taskId);if(session!=null)session.action="清理虚拟屏";}
    public JsonObject finish(String id){
        Session session=active.remove(id);if(session==null)return empty();
        boolean interrupted=Thread.interrupted();
        if(session.polling!=null)session.polling.cancel(false);JsonObject report;
        try{report=session.sampler.submit(()->{Thread.sleep(200);return session.finish();}).get(5,TimeUnit.SECONDS);}
        catch(Exception error){report=empty();report.addProperty("samplingError",error.getClass().getSimpleName());}
        finally{session.sampler.shutdownNow();if(interrupted)Thread.currentThread().interrupt();}
        return report;
    }
    private static JsonObject empty(){JsonObject report=new JsonObject();JsonArray metrics=new JsonArray();String[] labels={"前台切换次数","焦点丢失次数","键盘被抢次数","剪贴板内容变化","主屏掉帧率"};String[] keys={"foreground","focus","ime","clipboard","jank"};for(int i=0;i<keys.length;i++)metrics.add(DisturbanceCounters.metric(keys[i],labels[i],0,false,false,"任务未执行或采样不可用"));report.add("metrics",metrics);report.add("events",new JsonArray());return report;}
    private String read(String...command)throws Exception{
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();ScheduledFuture<?> kill=killers.schedule(process::destroyForcibly,700,TimeUnit.MILLISECONDS);StringBuilder text=new StringBuilder();
        try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;
            while((line=input.readLine())!=null&&text.length()<256_000){text.append(line).append('\n');if(command.length>1&&"input_method".equals(command[1])&&line.contains("mInputShown="))break;}
        }finally{kill.cancel(false);process.destroy();}return text.toString();
    }
    private static String clip(ClipboardBridge bridge)throws Exception{
        ClipData clip=bridge.getClip();StringBuilder value=new StringBuilder();if(clip!=null){for(int i=0;i<clip.getDescription().getMimeTypeCount();i++)value.append(clip.getDescription().getMimeType(i)).append('\u0000');for(int i=0;i<clip.getItemCount();i++){ClipData.Item item=clip.getItemAt(i);value.append(item.getText()).append('\u0000').append(item.getHtmlText()).append('\u0000').append(item.getUri()).append('\u0000').append(item.getIntent()).append('\u0000');}}
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
