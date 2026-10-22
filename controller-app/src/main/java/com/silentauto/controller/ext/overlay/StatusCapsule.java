package com.silentauto.controller.ext.overlay;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import org.json.JSONObject;
import java.util.*;

public final class StatusCapsule implements StatusSurface {
    public interface Actions { void open(JSONObject task); void confirm(String id,boolean ok); void query(JSONObject task,boolean copy); void resolve(String voiceId,String taskId); }
    private final Context context;private final Actions actions;private final Handler main=new Handler(Looper.getMainLooper());
    private final WindowManager windows;private final WindowManager.LayoutParams layout;private final Surface surface;
    private final Map<String,String> previous=new HashMap<>();
    private JSONObject task,queryResult,amendResult;private long amendUntil;private final Map<String,String> previousMessages=new HashMap<>();private long queryUntil;private Collection<JSONObject> lastValues=java.util.List.of();private boolean attached,expanded,closed;private long terminalUntil;
    private int routeOffset;private float routeTouchY;private boolean routeScrolled;private final java.util.List<RectF> routeButtons=new ArrayList<>();
    private final RectF yes=new RectF(),no=new RectF(),open=new RectF(),copy=new RectF();
    private final Runnable collapse=()->{if(task!=null&&!task.optBoolean("pendingConfirmation")){expanded=false;render();}};
    public StatusCapsule(Context context,Actions actions){
        this.context=context;this.actions=actions;windows=context.getSystemService(WindowManager.class);surface=new Surface();
        layout=new WindowManager.LayoutParams(dp(216),dp(40),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.setTitle("ShadowAuto status capsule");layout.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;layout.y=dp(8);
        layout.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static boolean terminal(String state){return Arrays.asList("VERIFIED","ANSWERED","NOT_FOUND","FAILED","CANCELLED","HANDOFF_TIMEOUT").contains(state);}
    @Override public void update(Collection<JSONObject> values){
        if(closed)return;lastValues=new ArrayList<>(values);JSONObject chosen=null,finished=null;boolean handoffArrived=false;
        for(JSONObject value:values){String id=value.optString("taskId"),state=value.optString("status");String old=previous.put(id,state);String oldMessage=previousMessages.put(id,value.optString("message"));
            if(amendResult!=null&&id.equals(amendResult.optString("taskId"))&&terminal(state))amendResult=null;
            if(value.optString("message").startsWith("已追加：")&&!value.optString("message").equals(oldMessage)&&!terminal(state)){
                amendResult=value;amendUntil=SystemClock.elapsedRealtime()+3000;main.postDelayed(()->{if(SystemClock.elapsedRealtime()>=amendUntil){amendResult=null;update(lastValues);}},3100);
            }if("WAITING_USER".equals(state)&&!state.equals(old))handoffArrived=true;
            if(terminal(state)){if(old!=null&&!terminal(old)){finished=value;if(queryAnswer(value)){queryResult=value;queryUntil=SystemClock.elapsedRealtime()+8000;main.postDelayed(()->{if(SystemClock.elapsedRealtime()>=queryUntil){queryResult=null;update(lastValues);}},8100);}
            terminalUntil=SystemClock.elapsedRealtime()+(queryAnswer(value)?8000:5000);}continue;}
            if(chosen==null||priority(value)<priority(chosen)||(priority(value)==0&&priority(chosen)==0&&value.optLong("waitingSinceMs",Long.MAX_VALUE)<chosen.optLong("waitingSinceMs",Long.MAX_VALUE)))chosen=value;
        }
        if(chosen==null){if(finished!=null)chosen=finished;else if(task!=null&&terminal(task.optString("status"))&&SystemClock.elapsedRealtime()<terminalUntil)chosen=task;}
        if(chosen!=null&&chosen.optBoolean("routingRequired")&&(task==null||!chosen.optString("taskId").equals(task.optString("taskId"))))routeOffset=0;
        if(queryResult!=null&&SystemClock.elapsedRealtime()<queryUntil&&(chosen==null||priority(chosen)>1))chosen=queryResult;
        if(amendResult!=null&&SystemClock.elapsedRealtime()<amendUntil&&(chosen==null||!chosen.optBoolean("routingRequired")))chosen=amendResult;
        boolean changed=chosen!=null&&(handoffArrived||task==null||!chosen.optString("taskId").equals(task.optString("taskId"))||!chosen.optString("status").equals(task.optString("status"))||chosen.optBoolean("pendingConfirmation")!=task.optBoolean("pendingConfirmation")||(chosen.optString("message").startsWith("已追加：")&&!chosen.optString("message").equals(task.optString("message"))));
        task=chosen;
        if(changed){expanded=true;main.removeCallbacks(collapse);if(!chosen.optBoolean("pendingConfirmation")&&!chosen.optBoolean("routingRequired"))main.postDelayed(collapse,queryAnswer(chosen)?8000:3000);}
        render();
        if(finished!=null)main.postDelayed(()->{if(task!=null&&terminal(task.optString("status"))&&SystemClock.elapsedRealtime()>=terminalUntil){task=null;render();}},queryAnswer(finished)?8100:5100);
    }
    private static boolean queryAnswer(JSONObject task){return "query".equals(task.optString("type"))&&Arrays.asList("ANSWERED","NOT_FOUND").contains(task.optString("status"));}
    private static int priority(JSONObject task){if(task.optBoolean("routingRequired"))return -1;if("WAITING_USER".equals(task.optString("status")))return 0;return task.optBoolean("pendingConfirmation")?1:2;}
    @Override public void compact(){expanded=false;render();}
    private void render(){
        if(closed)return;
        if(task==null){if(attached){windows.removeView(surface);attached=false;}return;}
        layout.width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(24),dp(expanded?352:216));int candidates=task.optJSONArray("candidates")==null?0:task.optJSONArray("candidates").length();
        layout.height=dp(expanded&&task.optBoolean("routingRequired")?126+Math.min(candidates,4)*40:expanded?154:40);
        surface.setContentDescription("任务状态："+word()+"，"+task.optString("goal")+"，"+task.optString("stepText"));
        if(attached)windows.updateViewLayout(surface,layout);else{windows.addView(surface,layout);attached=true;}surface.invalidate();
    }
    private String word(){if(task.optBoolean("routingRequired"))return "请选择追加任务";if(java.util.Arrays.asList("moving","returning","cancelling").contains(task.optString("transferState")))return "正在交接…";String state=task.optString("status");if("manual_main".equals(task.optString("transferState")))return "主屏处理 · 点此继续";if(task.optBoolean("pendingConfirmation"))return task.optBoolean("requiresConfirmation")?"待确认":"即将开始";
        switch(state){case "RUNNING":return "执行中";case "WAITING_USER":return task.optBoolean("resultReady")?"已完成 · 去查看":"待处理";case "QUEUED":return "排队中";case "PAUSED":return "已暂停";case "ANSWERED":return "查到了";case "NOT_FOUND":return "没查到";case "VERIFIED":return "已完成";case "FAILED":return "失败";case "CANCELLED":return "已取消";default:return "处理超时";}}
    @Override public void close(){closed=true;main.removeCallbacksAndMessages(null);if(attached){windows.removeView(surface);attached=false;}}
    private final class Surface extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Surface(){super(context);setClickable(true);}
        private void line(Canvas canvas,String value,float x,float y,float size,int color,float width){text.setColor(color);text.setTextSize(dp(size));canvas.drawText(TextUtils.ellipsize(value,text,width,TextUtils.TruncateAt.END).toString(),x,y,text);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(task==null)return;float w=getWidth(),h=getHeight();
            paint.setColor(0xf4232d3d);canvas.drawRoundRect(0,0,w,h,dp(20),dp(20),paint);
            boolean waiting="WAITING_USER".equals(task.optString("status")),pending=task.optBoolean("pendingConfirmation");
            int color=waiting?0xffffb35c:"FAILED".equals(task.optString("status"))?0xffff8686:"VERIFIED".equals(task.optString("status"))?0xff7bdfad:0xff8dd7cd;
            String symbol=waiting?"!":"VERIFIED".equals(task.optString("status"))?"✓":"●";
            line(canvas,symbol,dp(13),dp(27),18,color,dp(22));String label=word();
            if(pending)label+=" · "+Math.max(0,(task.optLong("confirmDeadlineMs")-System.currentTimeMillis()+999)/1000)+"s";
            else if(!expanded&&!task.optString("stepText").isEmpty())label+=" · "+task.optString("stepText");
            line(canvas,label,dp(38),dp(26),14,Color.WHITE,w-dp(50));yes.setEmpty();no.setEmpty();open.setEmpty();copy.setEmpty();routeButtons.clear();
            if(expanded&&task.optBoolean("routingRequired")) {
                long seconds=Math.max(0,(task.optLong("confirmDeadlineMs")-System.currentTimeMillis()+999)/1000);
                line(canvas,"追加到哪个任务？"+seconds+"s 后按新任务处理",dp(14),dp(63),13,Color.WHITE,w-dp(28));
                org.json.JSONArray candidates=task.optJSONArray("candidates");int count=candidates==null?0:candidates.length();routeOffset=Math.min(routeOffset,Math.max(0,count-4));
                for(int i=0;i<Math.min(4,count);i++){JSONObject candidate=candidates.optJSONObject(routeOffset+i);RectF rect=new RectF(dp(14),dp(76+i*40),w-dp(14),dp(110+i*40));routeButtons.add(rect);button(canvas,rect,candidate.optString("goal"),0xff35485e);}
                open.set(dp(14),h-dp(42),w-dp(14),h-dp(8));button(canvas,open,"作为新任务"+(count>4?"（上滑查看更多候选）":""),0xff266b60);postInvalidateDelayed(500);return;
            }
            if(expanded){
                text.setColor(Color.WHITE);text.setTextSize(dp(14));boolean queryResult=queryAnswer(task);
                String goal=queryResult?(task.optBoolean("uncertain")?"可能是：":"")+("NOT_FOUND".equals(task.optString("status"))?"没查到："+task.optString("searched"):task.optString("answer")):amendResult!=null&&SystemClock.elapsedRealtime()<amendUntil&&task.optString("taskId").equals(amendResult.optString("taskId"))?amendResult.optString("message"):"manual_main".equals(task.optString("transferState"))?task.optString("message"):task.optBoolean("resultReady")?task.optString("evidence",task.optString("goal")):task.optString("goal");
                StaticLayout lines=StaticLayout.Builder.obtain(goal,0,goal.length(),text,(int)(w-dp(28))).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();canvas.save();canvas.translate(dp(14),dp(44));lines.draw(canvas);canvas.restore();
                String detail=queryResult?"来源："+task.optString("appName",task.optString("sourceApp")):waiting||terminal(task.optString("status"))?task.optString("message"):task.optString("stepText");
                line(canvas,detail,dp(14),dp(105),12,0xffccd8df,w-dp(28));
                if(queryResult){
                    if(!task.optString("answer").isEmpty()){copy.set(dp(14),dp(116),w/2-dp(6),h-dp(8));button(canvas,copy,"复制",0xff266b60);}
                    open.set(copy.isEmpty()?dp(14):w/2+dp(6),dp(116),w-dp(14),h-dp(8));button(canvas,open,"去看看",0xff35485e);
                } else if(pending){if(task.optBoolean("requiresConfirmation")){yes.set(dp(14),dp(116),w/2-dp(6),h-dp(8));button(canvas,yes,"确认",0xff266b60);}no.set(yes.isEmpty()?dp(14):w/2+dp(6),dp(116),w-dp(14),h-dp(8));button(canvas,no,"取消",0xff703f4b);}
                else{open.set(dp(14),dp(116),w-dp(14),h-dp(8));button(canvas,open,java.util.Arrays.asList("moving","returning","cancelling").contains(task.optString("transferState"))?"正在交接…":"manual_main".equals(task.optString("transferState"))?"交回后台继续":task.optBoolean("resultReady")?"去查看":waiting?"到主屏处理":"查看任务",0xff35485e);}
            }
            if(pending)postInvalidateDelayed(500);
        }
        private void button(Canvas canvas,RectF rect,String label,int color){paint.setColor(color);canvas.drawRoundRect(rect,dp(10),dp(10),paint);text.setColor(Color.WHITE);text.setTextSize(dp(14));text.setTextAlign(Paint.Align.CENTER);canvas.drawText(TextUtils.ellipsize(label,text,rect.width()-dp(16),TextUtils.TruncateAt.END).toString(),rect.centerX(),rect.centerY()+dp(5),text);text.setTextAlign(Paint.Align.LEFT);}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(task!=null&&task.optBoolean("routingRequired")){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){routeScrolled=false;routeTouchY=event.getY();return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(event.getY()-routeTouchY)>dp(25)){
                    routeScrolled=true;org.json.JSONArray all=task.optJSONArray("candidates");int max=Math.max(0,(all==null?0:all.length())-4);routeOffset=Math.max(0,Math.min(max,routeOffset+(event.getY()<routeTouchY?1:-1)));routeTouchY=event.getY();invalidate();return true;
                }
                if(event.getActionMasked()==MotionEvent.ACTION_UP){if(routeScrolled)return true;performClick();String voice=task.optString("voiceId");if(open.contains(event.getX(),event.getY()))actions.resolve(voice,"new");else for(int i=0;i<routeButtons.size();i++)if(routeButtons.get(i).contains(event.getX(),event.getY())){JSONObject target=task.optJSONArray("candidates").optJSONObject(routeOffset+i);actions.resolve(voice,target.optString("taskId"));break;}return true;}
                return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP&&task!=null){performClick();String id=task.optString("taskId");if(copy.contains(event.getX(),event.getY()))actions.query(task,true);else if(open.contains(event.getX(),event.getY())&&queryAnswer(task))actions.query(task,false);else if(yes.contains(event.getX(),event.getY()))actions.confirm(id,true);else if(no.contains(event.getX(),event.getY()))actions.confirm(id,false);else if(!java.util.Arrays.asList("moving","returning","cancelling").contains(task.optString("transferState")))actions.open(task);return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
