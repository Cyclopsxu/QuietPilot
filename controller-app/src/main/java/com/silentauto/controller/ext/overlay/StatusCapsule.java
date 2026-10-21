package com.silentauto.controller.ext.overlay;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import org.json.JSONObject;
import java.util.*;

public final class StatusCapsule implements StatusSurface {
    public interface Actions { void open(JSONObject task); void confirm(String id,boolean ok); }
    private final Context context;private final Actions actions;private final Handler main=new Handler(Looper.getMainLooper());
    private final WindowManager windows;private final WindowManager.LayoutParams layout;private final Surface surface;
    private final Map<String,String> previous=new HashMap<>();
    private JSONObject task;private boolean attached,expanded,closed;private long terminalUntil;
    private final RectF yes=new RectF(),no=new RectF(),open=new RectF();
    private final Runnable collapse=()->{if(task!=null&&!task.optBoolean("pendingConfirmation")){expanded=false;render();}};
    public StatusCapsule(Context context,Actions actions){
        this.context=context;this.actions=actions;windows=context.getSystemService(WindowManager.class);surface=new Surface();
        layout=new WindowManager.LayoutParams(dp(216),dp(40),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.setTitle("ShadowAuto status capsule");layout.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;layout.y=dp(8);
        layout.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static boolean terminal(String state){return Arrays.asList("VERIFIED","FAILED","CANCELLED","HANDOFF_TIMEOUT").contains(state);}
    @Override public void update(Collection<JSONObject> values){
        if(closed)return;JSONObject chosen=null,finished=null;boolean handoffArrived=false;
        for(JSONObject value:values){String id=value.optString("taskId"),state=value.optString("status");String old=previous.put(id,state);if("WAITING_USER".equals(state)&&!state.equals(old))handoffArrived=true;
            if(terminal(state)){if(old!=null&&!terminal(old)){finished=value;terminalUntil=SystemClock.elapsedRealtime()+5000;}continue;}
            if(chosen==null||priority(value)<priority(chosen)||(priority(value)==0&&priority(chosen)==0&&value.optLong("waitingSinceMs",Long.MAX_VALUE)<chosen.optLong("waitingSinceMs",Long.MAX_VALUE)))chosen=value;
        }
        if(chosen==null){if(finished!=null)chosen=finished;else if(task!=null&&terminal(task.optString("status"))&&SystemClock.elapsedRealtime()<terminalUntil)chosen=task;}
        boolean changed=chosen!=null&&(handoffArrived||task==null||!chosen.optString("taskId").equals(task.optString("taskId"))||!chosen.optString("status").equals(task.optString("status"))||chosen.optBoolean("pendingConfirmation")!=task.optBoolean("pendingConfirmation"));
        task=chosen;
        if(changed){expanded=true;main.removeCallbacks(collapse);if(!chosen.optBoolean("pendingConfirmation"))main.postDelayed(collapse,3000);}
        render();
        if(finished!=null)main.postDelayed(()->{if(task!=null&&terminal(task.optString("status"))&&SystemClock.elapsedRealtime()>=terminalUntil){task=null;render();}},5100);
    }
    private static int priority(JSONObject task){if("WAITING_USER".equals(task.optString("status")))return 0;return task.optBoolean("pendingConfirmation")?1:2;}
    @Override public void compact(){expanded=false;render();}
    private void render(){
        if(closed)return;
        if(task==null){if(attached){windows.removeView(surface);attached=false;}return;}
        layout.width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(24),dp(expanded?352:216));layout.height=dp(expanded?154:40);
        surface.setContentDescription("任务状态："+word()+"，"+task.optString("goal")+"，"+task.optString("stepText"));
        if(attached)windows.updateViewLayout(surface,layout);else{windows.addView(surface,layout);attached=true;}surface.invalidate();
    }
    private String word(){if(java.util.Arrays.asList("moving","cancelling").contains(task.optString("transferState")))return "正在交接…";String state=task.optString("status");if(task.optBoolean("pendingConfirmation"))return task.optBoolean("requiresConfirmation")?"待确认":"即将开始";
        switch(state){case "RUNNING":return "执行中";case "WAITING_USER":return task.optBoolean("resultReady")?"已完成 · 去查看":"待处理";case "QUEUED":return "排队中";case "PAUSED":return "已暂停";case "VERIFIED":return "已完成";case "FAILED":return "失败";case "CANCELLED":return "已取消";default:return "处理超时";}}
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
            line(canvas,label,dp(38),dp(26),14,Color.WHITE,w-dp(50));yes.setEmpty();no.setEmpty();open.setEmpty();
            if(expanded){
                text.setColor(Color.WHITE);text.setTextSize(dp(14));String goal=task.optString("goal");
                StaticLayout lines=StaticLayout.Builder.obtain(goal,0,goal.length(),text,(int)(w-dp(28))).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();canvas.save();canvas.translate(dp(14),dp(44));lines.draw(canvas);canvas.restore();
                String detail=waiting||terminal(task.optString("status"))?task.optString("message"):task.optString("stepText");
                line(canvas,detail,dp(14),dp(105),12,0xffccd8df,w-dp(28));
                if(pending){if(task.optBoolean("requiresConfirmation")){yes.set(dp(14),dp(116),w/2-dp(6),h-dp(8));button(canvas,yes,"确认",0xff266b60);}no.set(yes.isEmpty()?dp(14):w/2+dp(6),dp(116),w-dp(14),h-dp(8));button(canvas,no,"取消",0xff703f4b);}
                else{open.set(dp(14),dp(116),w-dp(14),h-dp(8));button(canvas,open,java.util.Arrays.asList("moving","cancelling").contains(task.optString("transferState"))?"正在交接…":task.optBoolean("resultReady")?"去查看":waiting?"去处理":"查看任务",0xff35485e);}
            }
            if(pending)postInvalidateDelayed(500);
        }
        private void button(Canvas canvas,RectF rect,String label,int color){paint.setColor(color);canvas.drawRoundRect(rect,dp(10),dp(10),paint);text.setColor(Color.WHITE);text.setTextSize(dp(14));text.setTextAlign(Paint.Align.CENTER);canvas.drawText(label,rect.centerX(),rect.centerY()+dp(5),text);text.setTextAlign(Paint.Align.LEFT);}
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_UP&&task!=null){performClick();String id=task.optString("taskId");if(yes.contains(event.getX(),event.getY()))actions.confirm(id,true);else if(no.contains(event.getX(),event.getY()))actions.confirm(id,false);else if(!java.util.Arrays.asList("moving","cancelling").contains(task.optString("transferState")))actions.open(task);return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
