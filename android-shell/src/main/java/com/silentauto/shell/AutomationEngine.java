// Modified by Codex for ShadowAuto: delegate actions, verify query/action results, coordinate handoff and amend active goals. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import com.silentauto.shell.ext.*;
import com.silentauto.shell.ext.verify.ResultVerifier;
import com.silentauto.shell.ext.verify.EvidenceFrame;
import com.silentauto.shell.ext.handoff.HandoffManager;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silentauto.paddlerocr.PaddleOcrEngine;
import com.silentauto.paddlerocr.PaddleOcrResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AutomationEngine {
    private static final int MAX_STEPS = 25;
    private static final int MAX_OBSERVATION_TOOL_CALLS = 4;
    private static final int MAX_UI_CHARS = (int) (1_000_000 * 0.6);
    private static final int LOGCAT_LAYOUT_CHUNK = 3500;
    private static final String TOOL_SYSTEM_PROMPT = """
            You operate Android only by calling exactly one provided tool. Do not describe the action.
            """.trim();

    private final Config baseConfig;
    private final LogHub logs;
    private final AppCatalog appCatalog;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, TaskState> tasks = new LinkedHashMap<>();
    private final AtomicInteger nextTaskId = new AtomicInteger(1);
    private final List<StepListener> listeners = new ArrayList<>();
    private final List<ActionSource> sources = new ArrayList<>();
    private final com.silentauto.shell.ext.guard.TaskScheduler scheduler = new com.silentauto.shell.ext.guard.TaskScheduler();
    private final com.silentauto.shell.ext.guard.ForegroundWatcher foreground = new com.silentauto.shell.ext.guard.ForegroundWatcher();
    private final com.silentauto.shell.ext.skill.TrajectoryRecorder recorder = new com.silentauto.shell.ext.skill.TrajectoryRecorder();
    private final com.silentauto.shell.ext.skill.SkillStore skills = new com.silentauto.shell.ext.skill.SkillStore();
    private com.silentauto.shell.ext.context.ScreenContextReader contexts;
    private final Map<String, TaskRequest> voicePending = new LinkedHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService voiceTimers = Executors.newSingleThreadScheduledExecutor();
    private final com.silentauto.shell.ext.history.TaskArchive history = new com.silentauto.shell.ext.history.TaskArchive();
    private final com.silentauto.shell.ext.metrics.DisturbanceMonitor disturbance = new com.silentauto.shell.ext.metrics.DisturbanceMonitor();
    private UiBridge ui;
    private PaddleOcrEngine ocr;

    AutomationEngine(Config config, LogHub logs) {
        this.baseConfig = config;
        this.logs = logs;
        this.appCatalog = new AppCatalog(logs);
        listeners.add(recorder);
        listeners.add(disturbance);
    }

    synchronized String startTask(TaskRequest request) {
        if (Config.empty(request.goal)) {
            throw new IllegalArgumentException("goal is required");
        }
        String taskId = Config.empty(request.taskId) ? "task-" + nextTaskId.getAndIncrement() : request.taskId;
        if (!taskId.matches("[A-Za-z0-9_.-]{1,100}") || ".".equals(taskId) || "..".equals(taskId))
            throw new IllegalArgumentException("taskId must contain safe filename characters");
        TaskState previous=tasks.get(taskId);
        if(previous!=null&&previous.settled.getCount()!=0){
            if(!previous.progress.status().terminal())stopTask(taskId);
            if(previous.settled.getCount()!=0)throw new IllegalStateException("上一轮任务正在学习或收尾，请稍后重试");
        }
        TaskState task = new TaskState(taskId, request.goal, logs);
        task.type=request.type;task.progress.type(task.type.wire());
        tasks.put(taskId, task);
        recorder.prepare(taskId, task.amendments.goal());recorder.type(taskId,task.type.wire());
        task.progress.publish();
        submitTask(task,request);
        return taskId;
    }

    synchronized void stopTask() {
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            stopTask(taskId);
        }
        logs.task("automation stopped");
    }

    synchronized void stopTask(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status().terminal()) {
            return;
        }
        synchronized(task){task.stop.set(true);}
        if(task.transferInProgress) {
            task.progress.transfer("cancelling","正在取消交接并恢复后台页面…");
            return;
        }
        voicePending.remove(taskId);
        transition(task, TaskStatus.CANCELLED, task.cancellationMessage.isEmpty()?"automation cancelled":task.cancellationMessage, "", "");
        if (task.ai != null) task.ai.abort();
        if (task.verifierAi != null) task.verifierAi.abort();
        if (task.future != null) {
            task.future.cancel(true);
        }
        releaseTask(task);
        scheduler.release(task);
        boolean unstarted;
        synchronized(task){unstarted=!task.workerStarted;}
        if(unstarted){
            recorder.finish(taskId,task.progress.outcome());task.progress.disturbance(disturbance.finish(taskId));finishMetrics(task,null);
        }else recorder.snapshotOutcome(taskId, task.progress.outcome());
        archive(task);
        if(unstarted)task.settled.countDown();
        logs.task(taskId, "task metrics " + task.progress.json());
        logs.task(taskId, "automation stopped");
    }

    synchronized void forceStart(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status() != TaskStatus.QUEUED) throw new IllegalStateException("task is not queued");
        task.forceStart.set(true);
        logs.task(taskId, "WARNING: forceStart may conflict with the user on the main display");
    }

    synchronized void openTaskOnMain(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null || task.handoff == null || !task.progress.protectedScreen())
            throw new IllegalStateException("task is not waiting on a protected page");
        task.handoff.resume("open_main");
    }

    JsonObject takeResultOnMain(String taskId) {
        final TaskState task; final VirtualDisplaySession display;
        synchronized(this) {
            task=tasks.get(taskId);
            if(task==null)throw new IllegalStateException("Task not found");
            if(task.resultTransferred)return transferReply("already_moved","结果已经交接，请在应用中继续",false);
            if(task.transferInProgress)return transferReply("busy","正在交接，请稍候",false);
            if(task.stop.get()||task.progress.status()!=TaskStatus.WAITING_USER||task.display==null||task.handoff==null||!task.progress.json().get("resultReady").getAsBoolean())
                throw new IllegalStateException("结果已失效或任务已结束，请刷新任务");
            display=task.display; task.transferInProgress=true;
        }
        boolean replied=false;
        try {
            task.progress.transfer("moving","正在交接到主屏…");
            com.silentauto.shell.ext.handoff.TaskDisplayTransfer transfer=new com.silentauto.shell.ext.handoff.TaskDisplayTransfer(
                    new com.silentauto.shell.ext.handoff.TaskDisplayTransfer.Host() {
                        public String command(String...args)throws Exception{return com.silentauto.shell.ext.handoff.TaskDisplayTransfer.command(args);}
                        public com.silentauto.shell.ext.skill.ScreenFingerprint page(int id) {
                            return com.silentauto.shell.ext.handoff.PageIdentity.from(new com.silentauto.shell.ext.skill.UiSnapshot(
                                    getUi().dump(id,display.width,display.height,UiBridge.MODE_FULL)),task.packageName);
                        }
                        public String foreground()throws Exception{return foreground.foregroundPackage();}
                        public boolean cancelled(){return task.stop.get();}
                    });
            com.silentauto.shell.ext.handoff.TaskDisplayTransfer.Result moved=transfer.move(display.displayId,task.packageName,task.resultAnchors);
            JsonObject result=transferReply(moved.outcome,moved.message,moved.previewAvailable);
            result.addProperty("restored",moved.restored);result.addProperty("androidTaskId",moved.taskId);result.addProperty("rootTaskId",moved.rootId);
            logs.task(taskId,"result transfer outcome="+moved.outcome+" restored="+moved.restored+" root="+moved.rootId+" task="+moved.taskId);
            synchronized(this) {
                // No process/UI operation above holds the engine-wide monitor.
                task.transferInProgress=false;
                if(task.stop.get()) {
                    stopTask(taskId);
                    return transferReply("cancelled","交接已取消",false);
                }
                task.progress.transfer(moved.outcome,moved.message);
                if(moved.moved) {
                    task.resultTransferred=true;
                    if(task.conflict!=null)task.conflict.close();
                    task.handoff.resume("done");
                }
                replied=true;
            }
            return result;
        } finally {
            synchronized(this) {
                task.transferInProgress=false;
                if(task.stop.get()&&!task.progress.status().terminal())stopTask(taskId);
                else if(!replied&&!task.progress.status().terminal())task.progress.transfer("unavailable","交接状态未确认，请刷新任务后重试");
            }
        }
    }

    private static JsonObject transferReply(String outcome,String message,boolean preview) {
        JsonObject result=new JsonObject();result.addProperty("outcome",outcome);result.addProperty("message",message);
        result.addProperty("moved","moved".equals(outcome));result.addProperty("previewAvailable",preview);return result;
    }

    void openQuerySource(String taskId)throws Exception {
        TaskState task; synchronized(this){task=tasks.get(taskId);}
        JsonObject detail=task==null?history.get(taskId):task.progress.json();
        if(detail==null||!"query".equals(com.silentauto.shell.ext.skill.UiSnapshot.text(detail,"type"))
                ||!java.util.Arrays.asList("ANSWERED","NOT_FOUND").contains(com.silentauto.shell.ext.skill.UiSnapshot.text(detail,"status")))throw new IllegalStateException("Query result is not available");
        if(task!=null)releaseTask(task);
        appCatalog.openOnMain(taskId,com.silentauto.shell.ext.skill.UiSnapshot.text(detail,"app"));
    }

    synchronized void injectTouch(String taskId, String action, float x, float y) throws Exception {
        TaskState task = tasks.get(taskId);
        if (task == null || task.progress.status() != TaskStatus.WAITING_USER || task.display == null)
            throw new IllegalStateException("touch is only allowed while waiting for user");
        if(task.transferInProgress||task.manualTransfer!=null)throw new IllegalStateException("请在主屏操作当前页面");
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("finite coordinates required");
        if (task.packageName.equals(foreground.foregroundPackage())) throw new IllegalStateException("同一 App 正在主屏使用，暂停预览触摸");
        task.touch.inject(action, Math.max(0, Math.min(task.display.width - 1, x)),
                Math.max(0, Math.min(task.display.height - 1, y)));
    }

    JsonObject takeManualOnMain(String taskId) throws Exception {
        final TaskState task; final HandoffManager handoff;
        synchronized(this) {
            task=tasks.get(taskId);
            if(task==null||task.stop.get()||task.progress.status()!=TaskStatus.WAITING_USER||task.handoff==null||task.display==null||task.progress.json().get("resultReady").getAsBoolean())
                throw new IllegalStateException("任务当前不需要人工处理，请刷新");
            if(task.transferInProgress)throw new IllegalStateException("正在交接，请稍候");
            if(task.manualTransfer!=null)return transferReply("moved","请在主屏处理，完成后点交回后台继续",false);
            handoff=task.handoff;handoff.beginManual();task.transferInProgress=true;
        }
        try {
            task.progress.transfer("moving","正在把当前页面交给你在主屏处理…");
            com.silentauto.shell.ext.handoff.ManualTaskTransfer transfer=new com.silentauto.shell.ext.handoff.ManualTaskTransfer(
                new com.silentauto.shell.ext.handoff.TaskDisplayTransfer.Host() {
                    public String command(String...args)throws Exception{return com.silentauto.shell.ext.handoff.TaskDisplayTransfer.command(args);}
                    public com.silentauto.shell.ext.skill.ScreenFingerprint page(int id){return null;}
                    public String foreground()throws Exception{return foreground.foregroundPackage();}
                    public boolean cancelled(){return task.stop.get();}
                },task.packageName,task.display.displayId);
            transfer.toMain();
            synchronized(this) {
                if(task.stop.get())throw new InterruptedException("Task stopped");
                task.manualTransfer=transfer;
                task.progress.transfer("manual_main","请在主屏完成操作，完成后点“交回后台继续”");
            }
            return transferReply("moved","当前页面已交给你在主屏处理",false);
        } catch(Exception failure) {
            handoff.endManual();task.progress.transfer("preview","切换未完成，后台页面保留；可重试到主屏处理");throw failure;
        } finally {synchronized(this){task.transferInProgress=false;if(task.stop.get()&&!task.progress.status().terminal())stopTask(taskId);}}
    }

    void resumeTask(String taskId, String outcome) throws Exception {
        final TaskState task; final HandoffManager handoff;
        synchronized(this) {
            task=tasks.get(taskId);
            if(task==null||!"waiting_user".equals(task.state)||task.handoff==null)throw new IllegalStateException("task is not waiting for user");
            if(task.transferInProgress)throw new IllegalStateException("正在交接，请稍候再操作");
            if("cancel".equals(outcome)){stopTask(taskId);return;}
            handoff=task.handoff;
            if(task.manualTransfer==null){handoff.resume(outcome);if(task.verifierAi!=null)task.verifierAi.cancel();return;}
            if(!"done".equals(outcome))throw new IllegalArgumentException("请完成主屏处理后继续");
            task.transferInProgress=true;
        }
        try {
            task.progress.transfer("returning","正在将当前页面交回后台…");
            task.manualTransfer.toBackground();
            synchronized(this) {
                task.manualTransfer=null;
                task.progress.transfer("idle","已交回后台，重新观察后继续");
                handoff.resume(task.amendments.pending()?"amend":"done");
                if(task.verifierAi!=null)task.verifierAi.cancel();
            }
        } catch(Exception failure) {
            task.progress.transfer("manual_main","交回后台未完成，请保留主屏页面并重试");throw failure;
        } finally {synchronized(this){task.transferInProgress=false;if(task.stop.get()&&!task.progress.status().terminal())stopTask(taskId);}}
    }

    synchronized JsonArray listTasks() {
        JsonArray array = new JsonArray();
        for (TaskState task : tasks.values()) {
            JsonObject item = task.progress.json();
            item.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
            array.add(item);
        }
        return array;
    }

    private void transition(TaskState task, TaskStatus status, String message, String evidence, String boundary) {
        task.progress.transition(status, message, evidence, boundary);
        task.state = task.progress.status().name().toLowerCase(Locale.ROOT);
    }

    synchronized JsonObject statusJson() {
        JsonObject object = new JsonObject();
        boolean running = false;
        int displayId = -1;
        for (TaskState task : tasks.values()) {
            if (!"running".equals(task.state) && !"waiting_user".equals(task.state)) {
                continue;
            }
            running = true;
            if (displayId < 0 && task.display != null) {
                displayId = task.display.displayId;
            }
        }
        object.addProperty("state", running ? "running" : "idle");
        object.addProperty("displayId", running ? displayId : -1);
        JsonArray array = new JsonArray();
        for (TaskState task : tasks.values()) {
            JsonObject item = task.progress.json();
            item.addProperty("state", task.state);
            item.addProperty("status", task.progress.status().name());
            item.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
            array.add(item);
        }
        for(PendingVoice choice:voiceChoices.values())array.add(choice.choice.deepCopy());
        object.add("tasks", array);
        return object;
    }

    synchronized JsonObject dumpUiJson(int displayId, int width, int height, String mode) {
        String value = getUi().dump(displayId, width, height, mode);
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private synchronized com.silentauto.shell.ext.context.ScreenContextReader contexts() {
        if (contexts == null) contexts = new com.silentauto.shell.ext.context.ScreenContextReader(getUi(), bitmap -> {
            List<String> texts = new ArrayList<>();
            synchronized (this) { for (PaddleOcrResult item : getOcrEngine().recognize(bitmap)) texts.add(item.getText()); }
            return texts;
        });
        return contexts;
    }
    JsonObject captureContext() throws Exception {
        DisplaySpec display = DisplaySpec.current(logs); return contexts().capture(display.width,display.height);
    }
    void setContextReading(boolean enabled) throws Exception { contexts().reading(enabled); }
    void discardContext(String id) { contexts().discard(id); }

    private final java.util.concurrent.ConcurrentHashMap<String,PendingVoice> voiceChoices=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class PendingVoice {
        final JsonObject intake,params,choice; final long started,elapsed;
        PendingVoice(JsonObject i,JsonObject p,JsonObject c,long s){intake=i;params=p;choice=c;started=s;elapsed=SystemClock.elapsedRealtime()-s;}
    }
    private synchronized JsonArray voiceCandidates() {
        JsonArray result=new JsonArray();for(TaskState task:tasks.values()) {
            if(task.stop.get()||task.progress.status().terminal()||task.transferInProgress)continue;
            JsonObject state=task.progress.json(),value=new JsonObject();
            for(String k:new String[]{"taskId","goal","app","appName","status","stepText"})value.add(k,state.get(k));result.add(value);
        }return result;
    }
    private static JsonObject voiceConfig(JsonObject params){JsonObject result=new JsonObject();for(String key:new String[]{"models","apiKey","apiBase","model"})if(params.has(key))result.add(key,params.get(key).deepCopy());return result;}

    JsonObject submitVoiceTask(JsonObject params) throws Exception {
        String id = "voice-" + java.util.UUID.randomUUID();
        Config config = baseConfig.withOverrides(JsonRpc.text(params,"apiKey"),JsonRpc.text(params,"apiBase"),JsonRpc.text(params,"model")).withProfiles(params.getAsJsonObject("models"));
        config.intake.validate();
        JsonObject context = contexts().take(JsonRpc.text(params,"contextId"));
        long started = SystemClock.elapsedRealtime();
        JsonObject result;
        try {
            AiClient intake = new AiClient(config.forRole(config.intake),logs,id);
            result = new com.silentauto.shell.ext.voice.VoiceIntake().understand(JsonRpc.text(params,"audioBase64"),
                    JsonRpc.text(params,"audioFormat"),context,appCatalog.appsText(appCatalog.installedApps()),skills.list(true),voiceCandidates(),intake::completeParts);
        } finally { contexts().deleteConsumed(context); }
        if (!result.has("understood") || !result.get("understood").getAsBoolean()) return result;
        result.addProperty("voiceId",id);
        if("amend".equals(JsonRpc.text(result,"type"))) {
            JsonArray candidates=voiceCandidates();
            String route=com.silentauto.shell.ext.voice.VoiceRouting.route(result,candidates);
            if("ask".equals(route)) {
                JsonObject choice=com.silentauto.shell.ext.voice.VoiceRouting.choice(id,result,candidates,System.currentTimeMillis()+10_000);
                voiceChoices.put(id,new PendingVoice(result.deepCopy(),voiceConfig(params),choice,started));logs.status(choice);
                voiceTimers.schedule(()->{try{resolveVoice(id,"new");}catch(Exception e){logs.task(id,"voice route expired: "+e.getMessage());}},10,java.util.concurrent.TimeUnit.SECONDS);
                result.addProperty("route","ask");result.add("taskId",com.google.gson.JsonNull.INSTANCE);result.add("candidates",candidates);return result;
            }
            if(!"new".equals(route))try {
                JsonObject amend=result.getAsJsonObject("amend");
                amendTask(route,JsonRpc.text(amend,"kind"),JsonRpc.text(amend,"text"),SystemClock.elapsedRealtime()-started);
                result.addProperty("route","amended");result.addProperty("taskId",route);return result;
            }catch(IllegalStateException ended){ /* Completion races are routed as new tasks. */ }
            result=newVoiceIntent(result);
        }
        return registerVoice(id,result,params,started);
    }

    private JsonObject newVoiceIntent(JsonObject intake) {
        JsonObject result=intake.deepCopy();result.addProperty("type","action");result.addProperty("route","new");
        if(JsonRpc.text(result,"task").isEmpty())result.addProperty("task",JsonRpc.text(result,"transcript"));
        // A completed target supplies context, never permission to repeat its previous action.
        if(intake.has("amend")) {
            JsonObject amendment=intake.getAsJsonObject("amend");TaskState old;
            synchronized(this){old=tasks.get(JsonRpc.text(amendment,"target"));}
            if(old!=null&&old.progress.status().terminal()) {
                result.addProperty("app",old.packageName);
                result.addProperty("task","前一任务已结束："+old.progress.json().get("originalGoal").getAsString()+"。不要重复前一任务。现在作为新任务只处理："+JsonRpc.text(amendment,"text"));
            }
        }
        return result;
    }

    JsonObject resolveVoice(String id,String selected)throws Exception {
        PendingVoice pending=voiceChoices.get(id);if(pending==null)throw new IllegalStateException("归属选择已结束");
        if(!"new".equals(selected)) {
            boolean offered=false;for(com.google.gson.JsonElement candidate:pending.choice.getAsJsonArray("candidates"))if(selected.equals(JsonRpc.text(candidate.getAsJsonObject(),"taskId")))offered=true;
            if(!offered)throw new IllegalArgumentException("请选择列出的任务或新任务");
        }
        if(!voiceChoices.remove(id,pending))throw new IllegalStateException("归属选择已结束");
        JsonObject closed=pending.choice.deepCopy();closed.addProperty("routingRequired",false);closed.addProperty("routingResolved",true);closed.addProperty("status","CANCELLED");logs.status(closed);
        if(!"new".equals(selected))try {
            JsonObject amend=pending.intake.getAsJsonObject("amend");amendTask(selected,JsonRpc.text(amend,"kind"),JsonRpc.text(amend,"text"),pending.elapsed);
            JsonObject response=pending.intake.deepCopy();response.addProperty("route","amended");response.addProperty("taskId",selected);return response;
        }catch(IllegalStateException ended){ /* Target finished after the chooser appeared. */ }
        return registerVoice(id,newVoiceIntent(pending.intake),pending.params,SystemClock.elapsedRealtime()-pending.elapsed);
    }

    private JsonObject registerVoice(String id,JsonObject result,JsonObject params,long started)throws Exception {
        String pkg=JsonRpc.text(result,"app");boolean installed=false;for(AppCatalog.AppInfo app:appCatalog.installedApps())if(app.packageName.equals(pkg))installed=true;
        if(!installed)return com.silentauto.shell.ext.voice.VoiceIntake.rejected("不确定新任务使用哪个 App，请重新说明完整任务");
        TaskRequest request=new TaskRequest(id,"query".equals(JsonRpc.text(result,"type"))?JsonRpc.text(result,"question"):JsonRpc.text(result,"task"),JsonRpc.text(params,"apiKey"),JsonRpc.text(params,"apiBase"),JsonRpc.text(params,"model"));
        request.type=com.silentauto.shell.ext.query.TaskType.parse(JsonRpc.text(result,"type"));request.models=params.getAsJsonObject("models");
        boolean confirm=request.type!=com.silentauto.shell.ext.query.TaskType.QUERY&&!"high".equals(JsonRpc.text(result,"confidence"));
        synchronized(this) {
            TaskState task=new TaskState(id,request.goal,logs);task.type=request.type;task.progress.type(task.type.wire());tasks.put(id,task);recorder.prepare(id,request.goal);recorder.type(id,task.type.wire());task.progress.call("intake");
            task.progress.voice(JsonRpc.text(result,"transcript"),SystemClock.elapsedRealtime()-started,confirm,System.currentTimeMillis()+(confirm?10_000:3_000));
            if(task.type==com.silentauto.shell.ext.query.TaskType.QUERY){task.progress.confirmed();submitTask(task,request);}
            else {
                voicePending.put(id,request);task.future=voiceTimers.schedule(()->{synchronized(AutomationEngine.this){if(!voicePending.containsKey(id)||task.stop.get()||task.progress.status().terminal())return;if(confirm){voicePending.remove(id);stopTask(id);}else confirmTask(id,true);}},confirm?10:3,java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        result.addProperty("voiceId",id);result.addProperty("taskId",id);result.addProperty("route","new");return result;
    }
    synchronized void confirmTask(String id, boolean ok) {
        TaskRequest request = voicePending.remove(id); TaskState task = tasks.get(id);
        if (request == null || task == null || task.stop.get() || task.progress.status().terminal()) return;
        if (!ok) { stopTask(id); return; }
        task.progress.confirmed(); submitTask(task,request);
    }

    private void applyAmendments(TaskState task) {
        JsonArray applied=task.amendments.drain();if(applied.isEmpty())return;
        if(task.skillRunner!=null)task.skillRunner.abandonForAmendment();
        task.queryProgress.reset();
        task.searchText="";task.pendingSearchSubmit=false;task.searchInputActive=false;task.pendingInputText="";task.verificationFailures=0;task.answerFailures=0;
        task.progress.protectedScreen(false);
        task.lastAction="amended";task.lastReason="已应用用户追加要求；重新观察当前页面，依据有效目标继续，已完成操作不得重复";
        recorder.amended(task.taskId,task.amendments.records(),task.amendments.goal());
        task.progress.amended(task.amendments.goal(),task.amendments.records(),"已追加："+JsonRpc.text(applied.get(applied.size()-1).getAsJsonObject(),"text"));
        logs.task(task.taskId,"AMEND_APPLIED "+applied);
    }

    synchronized JsonObject amendTask(String id,String kind,String text){return amendTask(id,kind,text,-1);}
    private synchronized JsonObject amendTask(String id,String kind,String text,long intakeElapsed) {
        TaskState task=tasks.get(id);
        if(task==null)throw new IllegalStateException("目标任务不存在");
        synchronized(task.amendments) {
            if(task.progress.status().terminal()||task.stop.get())throw new IllegalStateException("目标任务已结束，请作为新任务提交");
            if(task.transferInProgress)throw new IllegalStateException("正在交接主屏，请稍候再追加");
            JsonObject record=task.amendments.add(kind,text);
            if(intakeElapsed>=0)task.progress.amendIntake(intakeElapsed);
            recorder.amended(id,task.amendments.records(),task.amendments.goal());
            task.progress.amended(task.amendments.goal(),task.amendments.records(),"已追加："+text);
            logs.task(id,"AMEND_QUEUED "+record);
            if("cancel".equals(kind)){task.cancellationMessage="已按你的要求取消";stopTask(id);}
            else {
                if(task.handoff!=null&&task.manualTransfer==null)task.handoff.tryResume("amend");
                if(task.verifierAi!=null)task.verifierAi.cancel();
                // Do not interrupt input injection. Discard any old planning response at the next boundary.
                if(task.ai!=null&&task.progress.status()==TaskStatus.RUNNING)task.ai.cancel();
            }
            return record;
        }
    }

    private void submitTask(TaskState task,TaskRequest request){
        task.future=executor.submit(()->{
            synchronized(task){if(task.stop.get()){task.settled.countDown();return;}task.workerStarted=true;}
            try{runTask(task,request);}finally{task.settled.countDown();}
        });
    }
    private void runTask(TaskState task, TaskRequest request) {
        Config config = baseConfig.withOverrides(request.apiKey, request.apiBase, request.model).withProfiles(request.models);
        String doneMessage = null;
        ExecutionContext ctx = null;
        try {
            if(task.stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Task stopped before planning");
            config.executor.validate();
            config.verifier.validate();
            AiClient ai = new AiClient(config, logs, task.taskId, () -> task.progress.call("executor"));
            task.ai = ai;
            UiBridge bridge = getUi();
            InputBridge input = new InputBridge();
            ClipboardBridge clipboard = new ClipboardBridge();
            applyAmendments(task);
            task.progress.action("正在选择应用", "model");
            task.searchText = task.amendments.version()==0?searchTextFromGoal(task.amendments.goal()):"";
            if (!Config.empty(task.searchText)) {
                logs.task(task.taskId, "search text hint: " + task.searchText);
            }

            logs.task(task.taskId, "listing installed apps");
            List<AppCatalog.AppInfo> apps = appCatalog.installedApps();
            com.silentauto.shell.ext.skill.SkillMatcher.Match match = null;
            try { match = new com.silentauto.shell.ext.skill.SkillMatcher().match(task.amendments.goal(),task.type.wire(), skills.list(true), ai::complete); }
            catch (Exception matchError) { logs.task(task.taskId, "skill match skipped: " + matchError.getMessage()); }
            ActionDecision app;
            if (match != null) {
                String skillPackage = match.skill.get("packageName").getAsString(), skillId = match.skill.get("skillId").getAsString();
                task.skillRunner = new com.silentauto.shell.ext.skill.SkillRunner(match.skill, match.args, () -> {
                    try { skills.abandoned(skillPackage, skillId); }
                    catch (Exception e) { logs.task(task.taskId, "skill abandon persistence failed: " + e.getMessage()); }
                    logs.task(task.taskId, "skill ABANDONED: " + skillId);
                });
                app = new ActionDecision(); app.packageName = match.skill.get("packageName").getAsString();
                logs.task(task.taskId, "skill matched: " + task.skillRunner.skillId());
            } else app = chooseApp(ai, task.amendments.goal(), apps);
            if (Config.empty(app.packageName)) {
                app.packageName = appCatalog.localPackageGuess(task.amendments.goal(), apps);
            }
            if (Config.empty(app.packageName)) {
                throw new IllegalStateException("AI did not choose an app package");
            }

            task.packageName = app.packageName;
            task.appName = app.packageName;
            for (AppCatalog.AppInfo installed : apps) {
                if (app.packageName.equals(installed.packageName)) { task.appName = installed.name; break; }
            }
            task.progress.app(app.packageName, task.appName);
            awaitAdmission(task);applyAmendments(task);
            if (task.stop.get()) throw new InterruptedException("task cancelled before launch");
            transition(task, TaskStatus.RUNNING, "开始执行", "", "");
            task.conflict = new com.silentauto.shell.ext.guard.MainAppConflictGuard(task.packageName,task.appName,
                    ()->{
                        String current=foreground.foregroundPackage();
                        if(task.forceStart.get()){if(task.packageName.equals(current))return "";task.forceStart.set(false);}
                        return current;
                    },task.progress::status,()->task.progress.json().get("stepText").getAsString(),
                    (state,detail)->{synchronized(task.progress){
                        if(state==TaskStatus.PAUSED&&task.progress.status()!=TaskStatus.RUNNING)return;
                        if(state==TaskStatus.RUNNING&&task.progress.status()!=TaskStatus.PAUSED)return;
                        transition(task,state,detail,"","");
                    }},()->{if(task.ai!=null)task.ai.cancel();if(task.verifierAi!=null)task.verifierAi.cancel();});
            input.inputAllowed(()->task.conflict.inputAllowed()&&!task.stop.get()&&!task.progress.status().terminal());
            task.conflict.awaitClear();
            disturbance.prepare(task.taskId,clipboard);
            if(task.stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Task stopped before display creation");
            DisplaySpec displaySpec = DisplaySpec.current(logs);
            task.videoStreamer = new VideoStreamer(logs, task.taskId, displaySpec.width, displaySpec.height);
            task.display = VirtualDisplaySession.create(task.videoStreamer.width(), task.videoStreamer.height(), displaySpec.dpi, task.videoStreamer.inputSurface());
            task.videoStreamer.start();
            logs.task(task.taskId, "virtual display created: " + task.display.displayId + " " + task.display.width + "x" + task.display.height + " dpi=" + task.display.dpi);
            logs.task(task.taskId, "virtual display local IME " + (task.display.localImeEnabled() ? "enabled" : "unavailable"));
            ctx = new ExecutionContext(task.taskId, task.display.displayId, task.display.width, task.display.height,
                    bridge, input, clipboard, logs);
            for (StepListener listener : listeners) listener.onTaskStart(ctx, task.amendments.goal(), app.packageName);
            task.conflict.awaitClear();
            appCatalog.launchPackage(task.taskId, app.packageName, task.display.displayId);
            logs.task(task.taskId, "launched " + app.packageName);
            sleep(task, 1800);

            task.touch = new com.silentauto.shell.ext.handoff.TouchSession(input, ctx.displayId);
            ActionExecutor actionExecutor = new ActionExecutor(logs);
            AiClient verifierAi = new AiClient(config.forRole(config.verifier), logs, task.taskId, () -> task.progress.call("verifier"));
            task.verifierAi = verifierAi;
            ResultVerifier verifier = new ResultVerifier(verifierAi::completeVision, () -> screenOcrJson(task),
                    () -> EvidenceFrame.from(task.display.captureBitmap(8, 120)));
            for (int step = 1; step <= MAX_STEPS && !task.stop.get(); step++) {
                if (task.conflict.awaitClear()) recoverAfterConflict(task);
                applyAmendments(task);
                long goalVersion=task.amendments.version();
                long planningEpoch=task.conflict.epoch();
                String dump = bridge.dump(task.display.displayId, task.display.width, task.display.height, UiBridge.MODE_SIMPLE);
                rememberUiObservation(task, dump);
                for (StepListener listener : listeners) listener.beforeStep(ctx, step, dump);
                ResultVerifier.Extracted recoveredQuery=null;
                if(task.type==com.silentauto.shell.ext.query.TaskType.QUERY&&task.queryProgress.observe(dump,task.lastAction)) {
                    logs.task(task.taskId,"QUERY_LOOP_RECOVERY: repeated scroll viewport; inspect screenshot and OCR");
                    try {
                        recoveredQuery=verifier.extractAnswer(ctx,task.amendments.goal(),true);
                        task.lastReason=recoveredQuery.answer.found?"独立截图/OCR发现有依据的答案":"当前滚动路径重复，截图/OCR也未找到足够原文。不要继续上下翻同一页面；返回换入口或使用应用内搜索。若只显示主版本，不得当作完整版本。";
                    } catch(Exception unavailable) {
                        if(task.stop.get()||Thread.currentThread().isInterrupted())throw unavailable;
                        task.lastReason="当前滚动路径重复，补充识别未能证明答案："+unavailable.getMessage()+"。不要重复滚动；返回换入口或搜索。";
                        logs.task(task.taskId,"query loop extraction unavailable: "+unavailable.getMessage());
                    }
                    if(task.amendments.version()!=goalVersion){step--;continue;}
                    if(task.conflict.awaitClear()||planningEpoch!=task.conflict.epoch()){recoverAfterConflict(task);step--;continue;}
                }
                ActionDecision action;
                if(recoveredQuery!=null&&recoveredQuery.answer.found) {
                    action=new ActionDecision();action.action="report_answer";action.source="query_recovery";
                    action.answer=recoveredQuery.answer.answer;action.quote=recoveredQuery.answer.quote;action.reason="重复页面后独立读取截图与OCR";
                } else action = task.skillRunner == null ? null : task.skillRunner.next(ctx, task, step, dump);
                if (action == null) action = firstNonNull(ctx, task, step, dump);
                if (action == null) {
                    try {
                    boolean rescuing = task.skillRunner != null && task.skillRunner.state() == com.silentauto.shell.ext.skill.SkillRunner.State.RESCUING;
                    action = nextAction(task, ai, bridge, task.amendments.goal(), app.packageName, step, task.display.displayId, dump);
                    if (rescuing) {
                        if ("done".equals(action.action)) action = ActionDecision.wait(300, "救援只恢复界面，重新观察");
                        action.source = "rescue";
                    }
                } catch(Exception changed){if(task.amendments.version()!=goalVersion){step--;continue;}throw changed;}
                }
                if(task.amendments.version()!=goalVersion){step--;continue;}
                if (task.stop.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("task cancelled");
                boolean recovered=task.conflict.awaitClear();
                if (recovered || planningEpoch!=task.conflict.epoch()) {
                    if(task.skillRunner!=null)task.skillRunner.retryUnexecuted(action);
                    recoverAfterConflict(task);step--;continue;
                }
                if("rescue".equals(action.source))task.skillRunner.rescueAction();
                if ("model".equals(action.source)) action = applyAutoCorrections(task, action);
                task.progress.action(step, action.reason, action.source);
                logs.task(task.taskId, "step " + step + ": " + action.action + " " + safe(action.reason) + " source=" + action.source);
                for(StepListener listener:listeners)listener.beforeAction(ctx,step,action);
                if(task.type==com.silentauto.shell.ext.query.TaskType.QUERY) {
                    String blocked=com.silentauto.shell.ext.guard.ReadOnlyGuard.blocked(action,bridge.dump(ctx.displayId,ctx.width,ctx.height,UiBridge.MODE_FULL));
                    if(!blocked.isEmpty()){task.lastAction="READONLY_BLOCKED";task.lastReason="查询任务只读，已拦截："+blocked+"，请换一种方式查";logs.task(task.taskId,task.lastReason+" READONLY_BLOCKED");continue;}
                }
                if ("request_user".equals(action.action) || "done".equals(action.action) || "report_answer".equals(action.action) || "report_not_found".equals(action.action)) {
                    for (StepListener listener : listeners) listener.afterAction(ctx, step, action, new StepResult(null, null), dump);
                }
                if ("request_user".equals(action.action)) {
                    if (!HandoffManager.valid(action.kind, action.message, action.boundary)) {
                        task.lastAction = "handoff_failed";
                        task.lastReason = "request_user requires a valid kind, message and visible boundary";
                        continue;
                    }
                    ResultVerifier.Result boundary = verifier.verifyBoundary(ctx, action.boundary);
                    if(task.amendments.version()!=goalVersion){step--;continue;}
                    if(task.conflict.awaitClear()||planningEpoch!=task.conflict.epoch()){recoverAfterConflict(task);step--;continue;}
                    logs.task(task.taskId, "handoff boundary verified=" + boundary.verified + ": "
                            + (boundary.verified ? boundary.evidence : boundary.missing));
                    boolean protectedScreen = boundary.frame != null && boundary.frame.protectedScreen;
                    if (!boundary.verified && !protectedScreen) {
                        task.lastAction = "handoff_failed";
                        task.lastReason = boundary.missing;
                        continue;
                    }
                    HandoffManager handoff = new HandoffManager();
                    task.handoff = handoff;
                    recorder.handedOff(task.taskId);
                    task.progress.protectedScreen(protectedScreen);
                    transition(task, TaskStatus.WAITING_USER, protectedScreen
                            ? "该页面受系统保护，请在 " + task.appName + " 中完成。" + action.message
                            : action.message, boundary.evidence, action.boundary);
                    logs.task(task.taskId, "HANDOFF " + action.kind + ": " + action.message);
                    logs.handoff(task.taskId, action.kind, action.message, action.boundary);
                    if(task.amendments.pending())handoff.tryResume("amend");
                    String outcome = handoff.awaitResponse(ctx, verifier, action.boundary, task.progress, verifierAi::cancel);
                    task.handoff = null;
                    if("amend".equals(outcome)){transition(task,TaskStatus.RUNNING,"追加要求已收到，继续执行","","");continue;}
                    if (!"done".equals(outcome)) {
                        task.resultStatus = "handoff";
                        if ("open_main".equals(outcome)) {
                            appCatalog.openOnMain(task.taskId, task.packageName);
                            task.lastReason = "已交给你在主屏完成";
                        } else {
                            task.lastReason = "已停在 " + action.boundary + "，等待用户: " + action.message + " (" + outcome + ")";
                        }
                        transition(task, "timeout".equals(outcome) ? TaskStatus.HANDOFF_TIMEOUT : TaskStatus.CANCELLED,
                                task.lastReason, "", action.boundary);
                        logs.result(task.taskId, "handoff", task.lastReason);
                        break;
                    }
                    transition(task, TaskStatus.RUNNING, "用户已处理，继续执行", "", "");
                    task.lastAction = "user_resumed";
                    task.lastReason = "User handled the boundary; inspect the fresh UI before continuing";
                    continue;
                }
                if(task.type==com.silentauto.shell.ext.query.TaskType.QUERY&&("report_answer".equals(action.action)||"report_not_found".equals(action.action))) {
                    try {
                        String question="skill".equals(action.source)?task.skillRunner.question():task.amendments.goal();
                        ResultVerifier.Extracted extracted=recoveredQuery!=null&&"query_recovery".equals(action.source)?recoveredQuery:verifier.extractAnswer(ctx,question);
                        if(task.amendments.version()!=goalVersion){step--;continue;}
                    if(task.conflict.awaitClear()||planningEpoch!=task.conflict.epoch()){recoverAfterConflict(task);step--;continue;}
                        boolean found=extracted.answer.found,uncertain=false;String answer=extracted.answer.answer,quote=extracted.answer.quote;
                        if(found&&!"skill".equals(action.source)&&!"query_recovery".equals(action.source)) {
                            boolean agrees="report_answer".equals(action.action)&&com.silentauto.shell.ext.query.AnswerVerifier.grounded(action.quote,extracted.visible)
                                    &&verifier.answersAgree(action.answer,answer);
                            if(agrees){answer=com.silentauto.shell.ext.query.AnswerVerifier.shortAnswer(action.answer);quote=action.quote;}
                            else if(task.answerFailures++<2){task.lastAction="answer_mismatch";task.lastReason="独立提取不一致，请继续检查页面："+answer+"；原文："+quote;continue;}
                            else uncertain=true;
                        } else if(!found&&"report_answer".equals(action.action)&&!"skill".equals(action.source)) {
                            if(task.answerFailures++<2){task.lastAction="answer_unproven";task.lastReason="当前界面不能证明答案，请继续查找或报告查过哪里";continue;}
                            throw new IllegalStateException("Query answer has no visible evidence");
                        }
                        String searched=action.searched;
                        if(!found&&searched.trim().isEmpty())searched="已检查"+task.appName+"当前查询页面";
                        ResultVerifier.Result proof=new ResultVerifier.Result(found,quote,"");proof.frame=extracted.frame;
                        task.progress.evidenceScreenshot(verifier.saveEvidence(ctx,proof));
                        synchronized(task.amendments) {
                        if(task.amendments.version()!=goalVersion){step--;continue;}
                        task.progress.answer(answer,quote,uncertain,searched);task.completed=true;task.resultStatus=found?"answered":"not_found";
                        transition(task,found?TaskStatus.ANSWERED:TaskStatus.NOT_FOUND,found?(uncertain?"可能是：":"")+answer:"没查到："+searched,quote,"");
                        logs.result(task.taskId,task.resultStatus,found?answer:searched);break;
                        }
                    } catch(InterruptedException interrupted){throw interrupted;}
                    catch(Exception unavailable){
                        if(task.amendments.version()!=goalVersion){step--;continue;}
                        if(task.answerFailures++<2){task.lastAction="answer_unavailable";task.lastReason="答案提取不可用："+unavailable.getMessage();continue;}
                        throw unavailable;
                    }
                }
                if ("done".equals(action.action)) {
                    ResultVerifier.Result verification = verifier.verify(ctx, task.amendments.goal(), action.expectedEvidence);
                    if(task.amendments.version()!=goalVersion){step--;continue;}
                    if(task.conflict.awaitClear()||planningEpoch!=task.conflict.epoch()){recoverAfterConflict(task);step--;continue;}
                    if (verification.verified) {
                        String evidencePath = verifier.saveEvidence(ctx, verification);
                        task.progress.evidenceScreenshot(evidencePath);
                        logs.task(task.taskId, "evidence saved: " + evidencePath);
                        task.completed = true;
                        task.resultStatus = "verified";
                        if (com.silentauto.shell.ext.handoff.ResultRetention.requested(task.amendments.goal(),verification.delivery)) {
                            task.resultAnchors=java.util.List.copyOf(verification.pageAnchors);
                            task.handoff = new HandoffManager();
                            task.progress.resultReady(true);
                            transition(task, TaskStatus.WAITING_USER, "已完成，后台页面已保留，点击去查看", verification.evidence, "结果已验收，等待查看");
                            logs.task(task.taskId, "result retained until user acknowledgement");
                            if(task.amendments.pending())task.handoff.tryResume("amend");
                            String reviewed = task.handoff.awaitAcknowledgement();
                            task.handoff = null;
                            task.progress.resultReady(false);
                            if("amend".equals(reviewed)){task.completed=false;task.resultStatus="";transition(task,TaskStatus.RUNNING,"追加要求已收到，继续执行","","");continue;}
                            if (task.stop.get() || !"done".equals(reviewed)) {
                                transition(task, TaskStatus.CANCELLED, "已关闭结果页面", verification.evidence, "");
                                break;
                            }
                            transition(task, TaskStatus.RUNNING, "查看结束，正在保存结果", verification.evidence, "");
                        }
                        synchronized(task.amendments) {
                        if(task.amendments.version()!=goalVersion){task.completed=false;task.resultStatus="";step--;continue;}
                        transition(task, TaskStatus.VERIFIED, task.resultTransferred ? "当前页面已交给你在主屏继续" : "任务已验收", verification.evidence, "");
                        logs.task(task.taskId, "VERIFIED: " + verification.evidence);
                        logs.result(task.taskId, "verified", verification.evidence);
                        break;
                        }
                    }
                    task.lastAction = "verification_failed";
                    task.lastReason = verification.missing;
                    logs.task(task.taskId, "verification failed: " + verification.missing);
                    if (++task.verificationFailures >= 3) {
                        task.resultStatus = "unverified";
                        transition(task, TaskStatus.FAILED, verification.missing, verification.evidence, "");
                        logs.result(task.taskId, "unverified", verification.missing);
                        break;
                    }
                    continue;
                }
                StepResult result = null;boolean interruptedInput=false;
                try { result = actionExecutor.execute(ctx, task, action); }
                catch(com.silentauto.shell.ext.guard.ReadOnlyGuard.Blocked blocked){task.lastAction="READONLY_BLOCKED";task.lastReason=blocked.getMessage();logs.task(task.taskId,task.lastReason);continue;}
                catch (com.silentauto.shell.ext.guard.MainAppConflictGuard.PausedInputException paused) { interruptedInput=true; }
                finally { for (StepListener listener : listeners) listener.afterAction(ctx, step, action, result, dump); }
                if(interruptedInput){
                    if(task.skillRunner!=null){task.skillRunner.retryUnexecuted(action);task.skillRunner.forceRescue();}
                    task.conflict.awaitClear();recoverAfterConflict(task);continue;
                }
                rememberAction(task, action, result.clickResult, result.focusResult);
                sleep(task, 900);
            }

            if (task.stop.get()) {
                doneMessage = "automation cancelled";
            } else if ("handoff".equals(task.resultStatus)) {
                doneMessage = "automation handoff: " + task.lastReason;
            } else if ("unverified".equals(task.resultStatus)) {
                doneMessage = "automation unverified: " + task.lastReason;
            } else if (task.completed) {
                doneMessage = "automation finished";
            } else {
                throw new IllegalStateException("max automation steps reached before finish");
            }
        } catch (Throwable e) {
            if (task.stop.get()) {
                transition(task, TaskStatus.CANCELLED, task.cancellationMessage.isEmpty()?"automation cancelled":task.cancellationMessage, "", "");
                doneMessage = "automation cancelled";
            } else {
                transition(task, TaskStatus.FAILED, e.getMessage(), "", "");
                logs.error(task.taskId, "automation failed", e);
            }
        } finally {
            if(task.conflict!=null)task.conflict.close();
            // A transferred result no longer needs a virtual display while learning is saved.
            if(task.resultTransferred||task.type==com.silentauto.shell.ext.query.TaskType.QUERY)releaseTask(task);
            JsonObject recorded = recorder.trajectory(task.taskId);
            if (recorded != null) recorded.add("outcome", new com.google.gson.Gson().toJsonTree(task.progress.outcome()));
            if (ctx != null) {
                for (StepListener listener : listeners) {
                    try { listener.onTaskEnd(ctx, task.progress.outcome()); }
                    catch (Throwable recordingError) { logs.error(task.taskId, "task listener failed", recordingError); }
                }
            }
            if (ctx == null) recorder.finish(task.taskId, task.progress.outcome());
            if (recorded != null && task.ai != null && !task.stop.get() && task.skillRunner == null) {
                try {
                    JsonObject metrics = task.progress.json();
                    int calls = 0;
                    for (JsonElement count : metrics.getAsJsonObject("llmCalls").asMap().values()) calls += count.getAsInt();
                    JsonObject learned = new com.silentauto.shell.ext.skill.SkillCompiler(skills).compile(recorded,
                            task.ai::complete, metrics.get("elapsedMs").getAsLong(), calls);
                    if (learned != null) task.progress.learned(learned.get("name").getAsString());
                } catch (Throwable compileError) {
                    logs.task(task.taskId, "skill compile skipped: " + compileError);
                }
            }
            task.ai = null;
            task.verifierAi = null;
            releaseTask(task);
            scheduler.release(task);
            task.progress.disturbance(disturbance.finish(task.taskId));
            finishMetrics(task, recorded);
            archive(task);
            logs.task(task.taskId, "task metrics " + task.progress.json());
            if (doneMessage != null) {
                logs.task(task.taskId, doneMessage);
            }
        }
    }

    private void recoverAfterConflict(TaskState task) throws Exception {
        if(task.stop.get())throw new InterruptedException("Task stopped during conflict");
        boolean visible=false;
        JsonObject fresh=JsonParser.parseString(getUi().dump(task.display.displayId,task.display.width,task.display.height,UiBridge.MODE_FULL)).getAsJsonObject();
        for(JsonElement item:com.silentauto.shell.ext.skill.UiSnapshot.array(fresh,"windows")){
            JsonObject window=item.getAsJsonObject();
            if(com.silentauto.shell.ext.skill.UiSnapshot.integer(window,"type")==1&&window.has("root")&&window.get("root").isJsonObject()
                    &&com.silentauto.shell.ext.skill.UiSnapshot.bool(window.getAsJsonObject("root"),"visible"))visible=true;
        }
        boolean moved=!visible||!foreground.appOnDisplay(task.display.displayId,task.packageName);
        if(moved){
            task.conflict.awaitClear();appCatalog.launchPackage(task.taskId,task.packageName,task.display.displayId);sleep(task,1800);
            if(task.skillRunner!=null)task.skillRunner.forceRescue();
        }
        JsonObject trace=recorder.trajectory(task.taskId);JsonArray completed=new JsonArray();
        if(trace!=null)for(JsonElement value:com.silentauto.shell.ext.skill.UiSnapshot.array(trace,"steps")){
            JsonObject step=value.getAsJsonObject(),summary=new JsonObject();summary.addProperty("step",step.get("step").getAsInt());summary.addProperty("stepText",JsonRpc.text(step,"stepText"));completed.add(summary);
        }
        task.lastAction="app_resumed";
        task.lastReason=(moved?"虚拟屏应用已移走，已重新启动。":"应用仍在虚拟屏。")+"从头观察，结合已完成的步骤继续。已完成步骤："+completed;
        logs.task(task.taskId,task.lastReason);
    }

    private void archive(TaskState task) {
        try { history.save(task.progress.json()); }
        catch (Exception e) { logs.task(task.taskId,"history save failed: "+e.getMessage()); }
    }
    JsonArray listHistory() throws Exception { return history.list(); }
    synchronized JsonObject getTask(String id) throws Exception {
        TaskState task=tasks.get(id);JsonObject detail=history.get(id);
        if (task!=null && (!task.progress.status().terminal() || detail==null || !detail.has("createdAtMs") || detail.get("createdAtMs").getAsLong()!=task.progress.json().get("createdAtMs").getAsLong())) {
            detail=task.progress.json();JsonObject trace=recorder.trajectory(id);
            detail.add("steps",trace==null?history.steps(id):com.silentauto.shell.ext.skill.UiSnapshot.array(trace,"steps"));
        }
        if(detail==null)throw new IllegalArgumentException("Task not found");
        return history.withEvidence(id,detail);
    }
    JsonArray listSkills() throws Exception {
        JsonArray result=skills.list(false);List<AppCatalog.AppInfo> apps=appCatalog.installedApps();
        for(JsonElement value:result){JsonObject skill=value.getAsJsonObject();String pkg=JsonRpc.text(skill,"packageName"),name=pkg;
            for(AppCatalog.AppInfo app:apps)if(pkg.equals(app.packageName)){name=app.name;break;}skill.addProperty("appName",name);}
        return result;
    }
    void setSkillStatus(String pkg,String id,String status) throws Exception { skills.status(pkg,id,status); }
    void deleteSkill(String pkg,String id) throws Exception { skills.delete(pkg,id); }

    private void finishMetrics(TaskState task, JsonObject recorded) {
        JsonObject sources = new JsonObject();
        for (String source : new String[]{"model","skill","rescue","query_recovery"}) sources.addProperty(source,0);
        if (recorded != null) for (JsonElement item : com.silentauto.shell.ext.skill.UiSnapshot.array(recorded,"steps")) {
            String source = com.silentauto.shell.ext.skill.UiSnapshot.text(item.getAsJsonObject(),"source");
            if (sources.has(source)) sources.addProperty(source,sources.get(source).getAsInt()+1);
        }
        String id = task.skillRunner == null ? "" : task.skillRunner.skillId(), comparison = "";
        if (!id.isEmpty()) try {
            JsonObject metrics = task.progress.json(); int calls = 0;
            for (JsonElement value : metrics.getAsJsonObject("llmCalls").asMap().values()) calls += value.getAsInt();
            long elapsed = metrics.get("elapsedMs").getAsLong();
            JsonObject skill = skills.get(task.packageName,id);
            if (skill != null) {
                JsonObject first = skill.getAsJsonObject("stats");
                comparison = String.format(Locale.ROOT,"模型调用 %d → %d 次，耗时 %.1f → %.1f 秒",
                        first.get("firstLlmCalls").getAsInt(),calls,first.get("firstElapsedMs").getAsDouble()/1000,elapsed/1000.0);
                skills.recordUse(task.packageName,id,elapsed,calls,(task.progress.status()==TaskStatus.VERIFIED||task.progress.status()==TaskStatus.ANSWERED) && task.skillRunner.state()!=com.silentauto.shell.ext.skill.SkillRunner.State.ABANDONED);
            }
        } catch (Exception e) { logs.task(task.taskId,"skill statistics unavailable: "+e.getMessage()); }
        task.progress.metrics(id,sources,comparison);
        if (!comparison.isEmpty()) logs.task(task.taskId,comparison);
    }

    private void awaitAdmission(TaskState task) throws Exception {
        String previous = "";
        while (!task.stop.get()) {
            boolean force = task.forceStart.get();
            boolean conflict = false;
            String reason;
            try {
                if (!force) conflict = task.packageName.equals(foreground.foregroundPackage());
                reason = scheduler.tryAcquire(task, task.packageName, conflict, force);
            } catch (Exception e) {
                reason = "暂时无法确认主屏 App，等待安全启动";
            }
            if (reason == null) return;
            reason = reason.replace(task.packageName, task.appName);
            if (!reason.equals(previous)) {
                transition(task, TaskStatus.QUEUED, reason, "", "");
                logs.task(task.taskId, reason);
                previous = reason;
            }
            Thread.sleep(2000);
        }
    }

    private ActionDecision firstNonNull(ExecutionContext ctx, TaskState task, int step, String dump) {
        for (ActionSource source : sources) {
            ActionDecision decision = source.next(ctx, task, step, dump);
            if (decision != null) return decision;
        }
        return null;
    }

    private synchronized UiBridge getUi() {
        if (ui == null) {
            ui = new UiBridge(logs);
            ui.connect();
        }
        return ui;
    }

    private void clearSearchSubmitIfExhausted(TaskState task) {
        if (task.searchSubmitAttempts >= 2 && task.pendingSearchSubmit) {
            task.pendingSearchSubmit = false;
            task.searchInputActive = false;
            logs.task(task.taskId, "search submit fallback exhausted");
        }
    }

    private ActionDecision chooseApp(AiClient ai, String goal, List<AppCatalog.AppInfo> apps) throws Exception {
        String prompt = String.format(Locale.US, """
                Choose the Android app for this user goal.
                Return only JSON: {"packageName":"...","reason":"..."}
                Goal: %s
                Apps:
                %s""", goal, appCatalog.appsText(apps));
        return ActionDecision.from(ai.complete(prompt));
    }

    private ActionDecision nextAction(TaskState task, AiClient ai, UiBridge bridge, String goal, String packageName, int step, int displayId, String uiDump) throws Exception {
        String prompt = String.format(Locale.US, """
                You control an Android app on a %dx%d virtual display.
                Rules:
                1. Call exactly one tool, never answer with prose.
                2. Coordinates are display-local in this %dx%d virtual display. Never use preview/screenshot pixels.
                3. Prefer tap_target using targetIndex from targets. Use raw tap only when no targetIndex exists, using a node center value.
                4. Use focus_input before input_text. input_text sets the focused field to the exact value, not an append operation.
                5. If the field should be empty, use clear_text. If existing text must be replaced, use input_text directly after focus_input.
                6. Use press_key with search or enter to submit focused search/input fields when appropriate.
                7. Use scroll_ui when more content is needed. direction means content direction: down reveals lower content, up reveals upper content.
                8. Use get_ui_layout mode=simple after a UI-changing action if the next target is uncertain; use mode=full only when simple lacks needed nodes.
                9. Use get_screen_ocr when UI layout is sparse, empty, wrong, or the visible page has text without accessibility nodes. OCR bounds are display-local.
                10. Use finish only when the visible UI proves the user's goal is complete.
                11. Search text candidate is the text to enter after opening a search field. If it is non-empty and the previous action clicked or focused a search/input field, call input_text with exactly that text; do not wait.
                12. Do not call wait repeatedly on sparse or empty UI. After two waits, call get_ui_layout, get_screen_ocr, input_text, press_back, scroll_ui, or another concrete tool.
                13. If a search was submitted and the UI dump has windows but inputs=[] and targets=[], the page may be visually rendered but accessibility-opaque. Do not press_back or restart the search only because nodes are missing. Prefer get_screen_ocr, get_ui_layout(full), wait once, or scroll_ui.
                14. For payment or payment passwords, login, captcha or sliders, face/fingerprint biometrics, and irreversible deletion/refunds without explicit user authorization, you MUST call request_user. Never attempt these yourself.
                Goal: %s
                Package: %s
                Step: %d
                Search text candidate: %s
                Previous action: %s
                Previous reason: %s
                Consecutive waits: %d
                Last UI stats: windows=%d inputs=%d targets=%d sparseAfterSearch=%d source=%s
                UI:
                %s""", task.display.width, task.display.height, task.display.width, task.display.height,
                goal, packageName, step, safe(task.searchText), safe(task.lastAction), safe(task.lastReason), task.consecutiveWaits,
                task.lastUiWindows, task.lastUiInputs, task.lastUiTargets, task.sparseUiAfterSearch, safe(task.lastUiWindowSource), truncate(uiDump, MAX_UI_CHARS));
        if (task.skillRunner != null && task.skillRunner.state() == com.silentauto.shell.ext.skill.SkillRunner.State.RESCUING)
            prompt += task.skillRunner.rescuePrompt();
        JsonArray messages = new JsonArray();
        messages.add(AiClient.message("system", TOOL_SYSTEM_PROMPT));
        if(task.type==com.silentauto.shell.ext.query.TaskType.QUERY)prompt+="\nQUERY 只读任务：仅搜索、浏览、读取。不得发送、提交、确认、购买、收藏等改变数据。读取所有可见节点的 text/desc，包括不可点击的文字；已有原文就直接报告，不必点击文字条目或重复滚动。找到答案调用 report_answer(answer<=60字,quote界面原文)，确实查不到调用 report_not_found(searched)。不得 finish，不得编造。";
        messages.add(AiClient.message("user", prompt));
        JsonArray tools = task.type==com.silentauto.shell.ext.query.TaskType.QUERY?com.silentauto.shell.ext.query.QueryTools.tools(ActionTools.tools()):ActionTools.tools();
        for (int observationCalls = 0; observationCalls <= MAX_OBSERVATION_TOOL_CALLS; observationCalls++) {
            AiClient.ToolResult result = ai.callTools(messages, tools);
            if (result.toolCalls.isEmpty()) {
                return ActionDecision.from(result.content);
            }
            AiClient.ToolCall call = result.toolCalls.get(0);
            logs.task(task.taskId, "tool_call " + call.name + " " + call.arguments);
            if ((task.skillRunner == null || task.skillRunner.state() == com.silentauto.shell.ext.skill.SkillRunner.State.ABANDONED)
                    && task.pendingSearchSubmit && task.searchSubmitAttempts < 2 && ActionTools.isObservationTool(call)) {
                logs.task(task.taskId, "auto submit search before observation");
                return ActionDecision.submitSearch("submit focused search before reading more layout");
            }
            if (!ActionTools.isObservationTool(call)) {
                return ActionDecision.fromTool(call);
            }
            if (observationCalls == MAX_OBSERVATION_TOOL_CALLS) {
                logs.task(task.taskId, "observation tool limit reached");
                return ActionDecision.wait(300);
            }
            String observation;
            if (ActionTools.isOcrTool(call)) {
                observation = screenOcrJson(task);
                logs.task(task.taskId, "ocr returned to AI");
            } else {
                String mode = ActionTools.layoutMode(call.arguments);
                observation = bridge.dump(displayId, task.display.width, task.display.height, mode);
                logLayoutDump(task.taskId, displayId, mode, observation);
                logs.task(task.taskId, "layout returned to AI");
            }
            messages.add(ActionTools.toolCallMessage(call, observationCalls));
            messages.add(ActionTools.toolResultMessage(ActionTools.toolCallId(call, observationCalls), truncate(observation, MAX_UI_CHARS)));
        }
        return ActionDecision.wait(1000);
    }

    private synchronized String screenOcrJson(TaskState task) {
        JsonObject object = new JsonObject();
        object.addProperty("displayId", task.display == null ? -1 : task.display.displayId);
        object.addProperty("width", task.display == null ? DisplaySpec.FALLBACK_WIDTH : task.display.width);
        object.addProperty("height", task.display == null ? DisplaySpec.FALLBACK_HEIGHT : task.display.height);
        JsonArray array = new JsonArray();
        object.add("results", array);
        Bitmap bitmap = null;
        try {
            if (task.display == null) {
                object.addProperty("error", "virtual display is not available");
                return JsonRpc.GSON.toJson(object);
            }
            logs.logcat(task.taskId, "ocr_dump_begin displayId=" + task.display.displayId);
            bitmap = ScreenCapture.latestBitmap(task.display, 8, 120);
            if (bitmap == null) {
                object.addProperty("error", "no latest frame available");
                logs.logcat(task.taskId, "ocr_dump_error no latest frame available");
                return JsonRpc.GSON.toJson(object);
            }
            List<PaddleOcrResult> results = getOcrEngine().recognize(bitmap);
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                PaddleOcrResult result = results.get(i);
                if (!Config.empty(result.getText())) {
                    if (combined.length() > 0) {
                        combined.append('\n');
                    }
                    combined.append(result.getText());
                }
                array.add(ocrResultJson(i, result));
            }
            object.addProperty("text", combined.toString());
            logOcrResults(task.taskId, results);
            logs.logcat(task.taskId, "ocr_dump_end displayId=" + task.display.displayId);
        } catch (Throwable e) {
            object.addProperty("error", e.getMessage());
            logs.error(task.taskId, "ocr dump failed", e);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return JsonRpc.GSON.toJson(object);
    }

    private PaddleOcrEngine getOcrEngine() throws Exception {
        if (ocr == null) {
            logs.logcat(null, "ocr_engine_init_begin");
            ocr = new PaddleOcrEngine();
            ocr.init();
            logs.logcat(null, "ocr_engine_init_done");
        }
        return ocr;
    }

    private JsonObject ocrResultJson(int index, PaddleOcrResult result) {
        JsonObject object = new JsonObject();
        Rect bounds = result.getBounds();
        object.addProperty("index", index);
        object.addProperty("text", result.getText());
        object.addProperty("confidence", result.getConfidence());
        JsonObject rect = new JsonObject();
        rect.addProperty("left", bounds.left);
        rect.addProperty("top", bounds.top);
        rect.addProperty("right", bounds.right);
        rect.addProperty("bottom", bounds.bottom);
        rect.addProperty("centerX", bounds.centerX());
        rect.addProperty("centerY", bounds.centerY());
        object.add("bounds", rect);
        JsonArray points = new JsonArray();
        for (Point point : result.getPoints()) {
            JsonObject item = new JsonObject();
            item.addProperty("x", point.x);
            item.addProperty("y", point.y);
            points.add(item);
        }
        object.add("points", points);
        return object;
    }

    private void logOcrResults(String taskId, List<PaddleOcrResult> results) {
        logs.logcat(taskId, "ocr_result_count=" + results.size());
        for (int i = 0; i < results.size(); i++) {
            PaddleOcrResult result = results.get(i);
            Rect bounds = result.getBounds();
            logs.logcat(taskId, "ocr[" + i + "] text=" + result.getText()
                    + " confidence=" + result.getConfidence()
                    + " bounds=" + bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom
                    + " cls=" + result.getClsIndex()
                    + " clsConfidence=" + result.getClsConfidence());
        }
    }

    private void logLayoutDump(String taskId, int displayId, String mode, String layout) {
        String value = layout == null ? "" : layout;
        int total = value.isEmpty() ? 1 : (value.length() + LOGCAT_LAYOUT_CHUNK - 1) / LOGCAT_LAYOUT_CHUNK;
        logs.logcat(taskId, "layout_dump_begin displayId=" + displayId + " mode=" + mode + " chars=" + value.length() + " chunks=" + total);
        if (value.isEmpty()) {
            logs.logcat(taskId, "layout_dump_part 1/1 <empty>");
        } else {
            for (int i = 0; i < value.length(); i += LOGCAT_LAYOUT_CHUNK) {
                int part = i / LOGCAT_LAYOUT_CHUNK + 1;
                int end = Math.min(value.length(), i + LOGCAT_LAYOUT_CHUNK);
                logs.logcat(taskId, "layout_dump_part " + part + "/" + total + " " + value.substring(i, end));
            }
        }
        logs.logcat(taskId, "layout_dump_end displayId=" + displayId + " mode=" + mode);
    }

    private ActionDecision applyAutoCorrections(TaskState task, ActionDecision action) {
        if ("wait".equals(action.action) && !Config.empty(task.pendingInputText)) {
            String text = task.pendingInputText;
            task.pendingInputText = "";
            logs.task(task.taskId, "auto input after search focus: " + text);
            return ActionDecision.inputText(text, "auto input after focused search field");
        }
        if (task.pendingSearchSubmit && task.searchSubmitAttempts < 2 && blocksSearchSubmit(action)) {
            logs.task(task.taskId, "auto submit search instead of " + action.action);
            return ActionDecision.submitSearch("submit focused search after entering text");
        }
        if (task.searchSubmitted && task.sparseUiAfterSearch > 0 && "back".equals(action.action)) {
            logs.task(task.taskId, "block back on sparse post-search accessibility tree");
            return sparsePostSearchFallback(task);
        }
        return action;
    }

    private ActionDecision sparsePostSearchFallback(TaskState task) {
        if (task.sparseUiAfterSearch <= 1) {
            return ActionDecision.wait(1200, "wait without leaving sparse post-search page");
        }
        String direction = task.sparseUiAfterSearch % 2 == 0 ? "down" : "up";
        return ActionDecision.scroll(direction, 0, "try to reveal accessible content without leaving sparse post-search page");
    }

    private boolean blocksSearchSubmit(ActionDecision action) {
        return "wait".equals(action.action)
                || "back".equals(action.action)
                || "done".equals(action.action);
    }

    private void rememberAction(TaskState task, ActionDecision action, UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        String previousAction = task.lastAction;
        String previousReason = task.lastReason;
        if ("wait".equals(action.action)) {
            task.consecutiveWaits++;
        } else {
            task.consecutiveWaits = 0;
        }
        if (shouldInputSearchText(task, action, clickResult, focusResult)) {
            task.pendingInputText = task.searchText;
            task.searchInputActive = true;
            task.pendingSearchSubmit = false;
            task.searchSubmitAttempts = 0;
            task.searchSubmitted = false;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "will input search text next step: " + task.pendingInputText);
        }
        if ("text".equals(action.action) && isSearchTextEntry(task, action, previousAction, previousReason)) {
            task.pendingSearchSubmit = true;
            task.searchInputActive = false;
            task.searchSubmitAttempts = 0;
            task.searchSubmitted = false;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "will submit search next step");
        }
        if ("key".equals(action.action) && task.pendingSearchSubmit && isSubmitKey(action.key)) {
            task.searchSubmitAttempts++;
            task.searchSubmitted = true;
            task.sparseUiAfterSearch = 0;
            logs.task(task.taskId, "model submitted search by key attempt " + task.searchSubmitAttempts);
            clearSearchSubmitIfExhausted(task);
        }
        if ("submit_search".equals(action.action)) {
            task.searchSubmitted = true;
            task.sparseUiAfterSearch = 0;
        }
        if ("text".equals(action.action) || "clear_text".equals(action.action) || "back".equals(action.action)) {
            task.pendingInputText = "";
        }
        if ("clear_text".equals(action.action) || "back".equals(action.action)) {
            task.searchInputActive = false;
        }
        task.lastAction = action.action;
        task.lastReason = action.reason;
    }

    private void rememberUiObservation(TaskState task, String dump) {
        UiStats stats = uiStats(dump);
        task.lastUiWindows = stats.windows;
        task.lastUiInputs = stats.inputs;
        task.lastUiTargets = stats.targets;
        task.lastUiWindowSource = stats.source;
        task.lastUiWindowError = stats.error;
        if (task.searchSubmitted && stats.sparse()) {
            task.sparseUiAfterSearch++;
            logs.task(task.taskId, "sparse post-search UI tree: windows=" + stats.windows
                    + " inputs=" + stats.inputs
                    + " targets=" + stats.targets
                    + " streak=" + task.sparseUiAfterSearch
                    + " source=" + safe(stats.source));
        } else if (!stats.sparse()) {
            task.sparseUiAfterSearch = 0;
        }
    }

    private UiStats uiStats(String dump) {
        UiStats stats = new UiStats();
        try {
            JsonObject object = JsonParser.parseString(safe(dump)).getAsJsonObject();
            stats.windows = arraySize(object.get("windows"));
            stats.inputs = arraySize(object.get("inputs"));
            stats.targets = arraySize(object.get("targets"));
            stats.source = stringValue(object, "windowSource");
            stats.error = stringValue(object, "windowError");
        } catch (Throwable ignored) {
            stats.error = "parse failed";
        }
        return stats;
    }

    private int arraySize(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return 0;
        }
        return element.getAsJsonArray().size();
    }

    private String stringValue(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return "";
        }
        return object.get(name).getAsString();
    }

    private boolean isSearchTextEntry(TaskState task, ActionDecision action, String previousAction, String previousReason) {
        if (Config.empty(task.searchText) || Config.empty(action.text)) {
            return false;
        }
        if (!sameText(action.text, task.searchText)) {
            return false;
        }
        return task.searchInputActive
                || looksLikeSearchInput(previousAction + " " + previousReason + " " + action.reason);
    }

    private boolean sameText(String left, String right) {
        return safe(left).trim().equalsIgnoreCase(safe(right).trim());
    }

    private boolean isSubmitKey(String key) {
        String value = safe(key).toLowerCase(Locale.US);
        return "search".equals(value) || "enter".equals(value);
    }

    private boolean shouldInputSearchText(TaskState task, ActionDecision action, UiBridge.ClickResult clickResult, UiBridge.FocusResult focusResult) {
        if (Config.empty(task.searchText)) {
            return false;
        }
        if (!"tap_target".equals(action.action) && !"focus_input".equals(action.action) && !"tap".equals(action.action)) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        appendSearchSignal(text, action.reason);
        appendSearchSignal(text, action.query);
        if (clickResult != null) {
            appendSearchSignal(text, clickResult.label);
            appendSearchSignal(text, clickResult.path);
        }
        if (focusResult != null) {
            appendSearchSignal(text, focusResult.label);
            appendSearchSignal(text, focusResult.path);
        }
        return looksLikeSearchInput(text.toString());
    }

    private void appendSearchSignal(StringBuilder out, String value) {
        if (!Config.empty(value)) {
            out.append(' ').append(value);
        }
    }

    private boolean looksLikeSearchInput(String value) {
        String text = safe(value).toLowerCase(Locale.US);
        return text.contains("search")
                || text.contains("query")
                || text.contains("keyword")
                || text.contains("input")
                || text.contains("edit")
                || text.contains("搜索")
                || text.contains("搜")
                || text.contains("输入")
                || text.contains("查找");
    }

    private String searchTextFromGoal(String goal) {
        if (Config.empty(goal)) {
            return "";
        }
        String[] markers = {"搜索", "搜一下", "搜", "查找", "查询"};
        for (String marker : markers) {
            int index = goal.indexOf(marker);
            if (index >= 0) {
                String text = cleanSearchText(goal.substring(index + marker.length()));
                return genericSearchTarget(text) ? "" : text;
            }
        }
        return "";
    }

    private String cleanSearchText(String raw) {
        String value = safe(raw).trim();
        while (value.startsWith(":") || value.startsWith("：") || value.startsWith("，") || value.startsWith(",") || value.startsWith(" ")) {
            value = value.substring(1).trim();
        }
        String[] stops = {"，", ",", "。", ".", "；", ";", "\n", "然后", "并", "再", "点杯", "点一杯", "买", "购买", "下单"};
        int end = value.length();
        for (String stop : stops) {
            int index = value.indexOf(stop);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return value.substring(0, end).trim();
    }

    private boolean genericSearchTarget(String value) {
        String text = safe(value).trim().toLowerCase(Locale.US);
        return text.isEmpty()
                || text.startsWith("入口")
                || text.startsWith("框")
                || text.startsWith("栏")
                || text.startsWith("按钮")
                || text.startsWith("图标")
                || text.startsWith("icon")
                || text.startsWith("button");
    }

    private void releaseTask(TaskState task) {
        synchronized(task) {
        if (task.videoStreamer != null) {
            task.videoStreamer.stop();
            task.videoStreamer = null;
        }
        if (task.display != null) {
            task.display.release();
            task.display = null;
        }
        }
    }

    private void sleep(TaskState task, long ms) {
        if (!task.stop.get()) {
            SystemClock.sleep(ms);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    static final class TaskRequest {
        final String taskId;
        final String goal;
        final String apiKey;
        final String apiBase;
        final String model;
        JsonObject models;
        com.silentauto.shell.ext.query.TaskType type=com.silentauto.shell.ext.query.TaskType.ACTION;

        TaskRequest(String goal, String apiKey, String apiBase, String model) {
            this("", goal, apiKey, apiBase, model);
        }

        TaskRequest(String taskId, String goal, String apiKey, String apiBase, String model) {
            this.taskId = taskId;
            this.goal = goal;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.model = model;
        }
    }

    public static final class TaskState {
        public final String taskId;
        public final AtomicBoolean forceStart = new AtomicBoolean(false);
        public final AtomicBoolean stop = new AtomicBoolean(false);
        final java.util.concurrent.CountDownLatch settled=new java.util.concurrent.CountDownLatch(1);
        boolean workerStarted;
        public volatile String state = "queued";
        public final TaskProgress progress;
        public com.silentauto.shell.ext.guard.MainAppConflictGuard conflict;
        public com.silentauto.shell.ext.skill.SkillRunner skillRunner;
        public String packageName = "";
        public String appName = "";
        public volatile AiClient verifierAi;
        public volatile Future<?> future;
        public volatile VirtualDisplaySession display;
        public volatile VideoStreamer videoStreamer;
        public volatile AiClient ai;
        public com.silentauto.shell.ext.query.TaskType type=com.silentauto.shell.ext.query.TaskType.ACTION;
        public int answerFailures;
        public final com.silentauto.shell.ext.query.QueryProgress queryProgress=new com.silentauto.shell.ext.query.QueryProgress();
        public final com.silentauto.shell.ext.amend.AmendmentQueue amendments;
        public String cancellationMessage="";
        public boolean completed;
        public volatile boolean resultTransferred, transferInProgress;
        public volatile java.util.List<String> resultAnchors=java.util.List.of();
        public volatile HandoffManager handoff;
        public volatile com.silentauto.shell.ext.handoff.ManualTaskTransfer manualTransfer;
        public com.silentauto.shell.ext.handoff.TouchSession touch;
        public String resultStatus = "";
        public int verificationFailures;
        public String agentClipboard = "";
        public String searchText = "";
        public String pendingInputText = "";
        public boolean searchInputActive;
        public boolean pendingSearchSubmit;
        public int searchSubmitAttempts;
        public String lastAction = "";
        public String lastReason = "";
        public int consecutiveWaits;
        public boolean searchSubmitted;
        public int sparseUiAfterSearch;
        public int lastUiWindows;
        public int lastUiInputs;
        public int lastUiTargets;
        public String lastUiWindowSource = "";
        public String lastUiWindowError = "";

        TaskState(String taskId, String goal, LogHub logs) {
            this.taskId = taskId;this.amendments=new com.silentauto.shell.ext.amend.AmendmentQueue(goal);
            this.progress = new TaskProgress(taskId, goal, logs);
        }
    }

    private static final class UiStats {
        int windows;
        int inputs;
        int targets;
        String source = "";
        String error = "";

        boolean sparse() {
            return windows > 0 && inputs == 0 && targets == 0;
        }
    }

}
