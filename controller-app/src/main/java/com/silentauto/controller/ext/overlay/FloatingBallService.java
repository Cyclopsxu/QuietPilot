package com.silentauto.controller.ext.overlay;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;
import com.silentauto.controller.AiConfigStore;
import com.silentauto.controller.MainActivity;
import com.silentauto.controller.ext.context.ScreenPrivacy;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class FloatingBallService extends Service {
    private static final int BALL_DP=64, EDGE_MARGIN_DP=28;
    private static final long HOLD_TO_RECORD_MS=1000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newCachedThreadPool();
    private final LinkedHashMap<String,JSONObject> tasks=new LinkedHashMap<>();
    private StatusSurface capsule;
    private WindowManager windows;private WindowManager.LayoutParams layout;private Ball ball;private OverlayRpc rpc;
    private boolean down,dragging,cancelled,recording,understanding,destroyed;
    private long pressed;private float startX,startY;private int originX,originY,generation;
    private String contextId="";private WavRecorder recorder;private AudioManager audio;private AudioFocusRequest focus;
    private String flash="";private long flashUntil;
    private final Runnable idle=()->{if(!down&&ball!=null){ball.setAlpha(.82f);ball.invalidate();}};
    private final Runnable hold=this::tryRecord;
    private final Runnable limit=()->finishGesture(false);
    @Override public void onCreate(){super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel("voice_ball","语音悬浮球",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);manager.createNotificationChannel(channel);
        PendingIntent guide=PendingIntent.getActivity(this,0,new Intent(this,OverlayPermissionActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(this,"voice_ball").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("语音悬浮球已开启").setContentText("按住约1秒，震动后说话；点击设置关闭").setContentIntent(guide).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=30)startForeground(1040,notice,OverlayPermissionActivity.ready(this)?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(1040,notice);
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        windows=getSystemService(WindowManager.class);audio=getSystemService(AudioManager.class);ball=new Ball();
        layout=new WindowManager.LayoutParams(dp(BALL_DP),dp(BALL_DP),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.setTitle("ShadowAuto voice ball");layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=dockX(false);layout.y=dp(180);windows.addView(ball,layout);
        ball.setOnTouchListener((v,event)->touch(event));main.postDelayed(idle,3000);
        rpc=new OverlayRpc(status->main.post(()->status(status)));
        capsule=new StatusCapsule(this,new StatusCapsule.Actions(){
            public void open(JSONObject task){openTask(task);}
            public void query(JSONObject task,boolean copy){
                if(copy){getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("查询答案",task.optString("answer")));message("已复制答案");}
                else io.execute(()->{try{rpc.request("openQuerySource",new JSONObject().put("taskId",task.optString("taskId")));}catch(Exception e){main.post(()->message(e.getMessage()));}});
            }
            public void confirm(String id,boolean ok){io.execute(()->{try{rpc.request("confirmTask",new JSONObject().put("taskId",id).put("ok",ok));}catch(Exception e){main.post(()->message(e.getMessage()));}});}
        });
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}private int screenWidth(){return getResources().getDisplayMetrics().widthPixels;}
    private int dockX(boolean left){
        int inset=dp(EDGE_MARGIN_DP);
        WindowInsets insets=ball==null?null:ball.getRootWindowInsets();
        if(insets!=null)inset=Math.max(inset,(left?insets.getSystemGestureInsets().left:insets.getSystemGestureInsets().right)+dp(8));
        return left?inset:Math.max(inset,screenWidth()-dp(BALL_DP)-inset);
    }
    private boolean touch(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            if(down||understanding)return true;
            down=true;dragging=false;cancelled=false;contextId="";pressed=SystemClock.elapsedRealtime();startX=event.getRawX();startY=event.getRawY();originX=layout.x;originY=layout.y;int token=++generation;
            main.removeCallbacks(idle);main.postDelayed(hold,HOLD_TO_RECORD_MS);main.postDelayed(limit,30000);
            // First operation is context capture. Recording, focus changes and visual feedback wait for its reply.
            io.execute(()->{try{ScreenPrivacy.sync(this);JSONObject result=rpc.request("captureContext",new JSONObject()).getJSONObject("result");String captured=result.optString("contextId");main.post(()->{
                if(!down||token!=generation||dragging||cancelled){discard(captured);return;}contextId=captured;ball.setAlpha(1);ball.invalidate();android.util.Log.i("ShadowAutoVoice","context ready");tryRecord();
            });}catch(Exception error){main.post(()->{if(token==generation){cancelled=true;finishGesture(true);message(error.getMessage());}});}});
            return true;
        }
        if(!down)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-startX,dy=event.getRawY()-startY;
            if(!dragging&&dy<-dp(80)){cancelled=true;finishGesture(true);return true;}
            if(!recording&&(Math.abs(dx)>dp(12)||dy>dp(12))){dragging=true;main.removeCallbacks(hold);}
            if(dragging){layout.x=Math.max(dockX(true),Math.min(dockX(false),originX+(int)dx));layout.y=Math.max(dp(32),Math.min(getResources().getDisplayMetrics().heightPixels-dp(80),originY+(int)dy));windows.updateViewLayout(ball,layout);}return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP){boolean tap=SystemClock.elapsedRealtime()-pressed<300&&!dragging&&!cancelled;finishGesture(dragging||cancelled||SystemClock.elapsedRealtime()-pressed<HOLD_TO_RECORD_MS);if(tap)openTask();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){finishGesture(true);return true;}return true;
    }
    private void tryRecord(){
        if(!down||dragging||cancelled||recording||contextId.isEmpty()||SystemClock.elapsedRealtime()-pressed<HOLD_TO_RECORD_MS)return;
        if(!OverlayPermissionActivity.ready(this)){cancelled=true;return;}
        try{
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(change->{if(change<0)main.post(()->finishGesture(true));}).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("暂时无法获取音频焦点");
            recorder=new WavRecorder();recorder.start();android.util.Log.i("ShadowAutoVoice","recording started after context");recording=true;ball.setAlpha(1);ball.invalidate();
            Vibrator vibrator=getSystemService(Vibrator.class);
            if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE));
        }catch(Exception error){cancelled=true;finishGesture(true);message(error.getMessage());}
    }
    private void finishGesture(boolean discard){
        if(!down)return;down=false;main.removeCallbacks(hold);main.removeCallbacks(limit);
        if(focus!=null){audio.abandonAudioFocusRequest(focus);focus=null;android.util.Log.i("ShadowAutoVoice","audio focus released");}
        boolean send=recording&&!discard&&!cancelled;WavRecorder active=recorder;recorder=null;recording=false;String captured=contextId;contextId="";
        if(dragging){layout.x=dockX(layout.x+dp(BALL_DP)/2<screenWidth()/2);windows.updateViewLayout(ball,layout);}
        if(!send){discard(captured);if(active!=null)io.execute(()->{try{active.stop();}catch(Exception ignored){}});}
        else{understanding=true;io.execute(()->{try{
            byte[] wav=active.stop();JSONObject params=new JSONObject().put("contextId",captured).put("audioFormat","wav").put("audioBase64",android.util.Base64.encodeToString(wav,android.util.Base64.NO_WRAP))
                    .put("models",new AiConfigStore(this).load().profilesJson());
            JSONObject result=rpc.request("submitVoiceTask",params).getJSONObject("result");main.post(()->{understanding=false;if(!result.optBoolean("understood")){flash="failed";flashUntil=SystemClock.elapsedRealtime()+5000;message(result.optString("reason"));}ball.invalidate();});
        }catch(Exception error){discard(captured);main.post(()->{understanding=false;message(error.getMessage());});}});}
        ball.invalidate();main.postDelayed(idle,3000);
    }
    private void discard(String id){if(id==null||id.isEmpty()||rpc==null)return;io.execute(()->{try{rpc.request("discardContext",new JSONObject().put("contextId",id));}catch(Exception ignored){}});}
    private void message(String text){if(destroyed)return;Toast.makeText(this,text==null?"操作失败":text,Toast.LENGTH_LONG).show();}
    private void status(JSONObject value){if(destroyed)return;String id=value.optString("taskId");if(id.isEmpty())return;JSONObject old=tasks.put(id,value);
        String state=value.optString("status");if((old==null||!state.equals(old.optString("status")))&&(state.equals("VERIFIED")||state.equals("ANSWERED")||state.equals("NOT_FOUND")||state.equals("FAILED"))){flash=(state.equals("VERIFIED")||state.equals("ANSWERED")||state.equals("NOT_FOUND"))?"done":"failed";flashUntil=SystemClock.elapsedRealtime()+5000;}
        if(ball!=null)ball.invalidate();if(capsule!=null)capsule.update(tasks.values());
    }
    private JSONObject waiting(){JSONObject first=null;for(JSONObject task:tasks.values())if("WAITING_USER".equals(task.optString("status"))&&(first==null||task.optLong("waitingSinceMs",Long.MAX_VALUE)<first.optLong("waitingSinceMs",Long.MAX_VALUE)))first=task;return first;}
    private int running(){int count=0;for(JSONObject task:tasks.values())if(!Arrays.asList("VERIFIED","ANSWERED","NOT_FOUND","FAILED","CANCELLED","HANDOFF_TIMEOUT").contains(task.optString("status")))count++;return count;}
    private void openTask(){openTask(waiting());}
    private void openTask(JSONObject target){
        if(target!=null&&Arrays.asList("moving","cancelling").contains(target.optString("transferState"))){Toast.makeText(this,"正在交接，请稍候",Toast.LENGTH_SHORT).show();return;}
        if(target!=null&&Arrays.asList("VERIFIED","ANSWERED","NOT_FOUND","FAILED","CANCELLED","HANDOFF_TIMEOUT").contains(target.optString("status"))){startActivity(new Intent(this,com.silentauto.controller.ext.history.TaskCenterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("taskId",target.optString("taskId")));return;}
        if(capsule!=null)capsule.compact();if(!OverlayPermissionActivity.ready(this)){startActivity(new Intent(this,OverlayPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(target!=null){open.putExtra("handoffTaskId",target.optString("taskId"));if("WAITING_USER".equals(target.optString("status")))open.putExtra("handoffStatus",target.toString());}startActivity(open);}
    @Override public void onDestroy(){destroyed=true;if(down)finishGesture(true);main.removeCallbacksAndMessages(null);if(rpc!=null)rpc.close();if(capsule!=null)capsule.close();if(windows!=null&&ball!=null)windows.removeView(ball);io.shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private final class Ball extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Ball(){super(FloatingBallService.this);setContentDescription("语音悬浮球：按住约1秒，震动后说话，松开发送，上滑取消");}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,w,h)));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,r=dp(recording?30:28);boolean needs=waiting()!=null;int count=running();long now=SystemClock.elapsedRealtime();
            int color=!OverlayPermissionActivity.ready(FloatingBallService.this)?0xff888888:needs?0xffef8b25:recording?0xffb94463:0xff247c76;
            if(!recording&&!needs&&now<flashUntil)color=flash.equals("done")?0xff21864e:0xffbd3e45;
            if(needs)color=Color.argb((int)(190+65*Math.sin(now/180.0)),239,139,37);paint.setColor(color);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,r,paint);
            paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(28));String symbol=recording?"●":understanding?"…":needs?"!":now<flashUntil?(flash.equals("done")?"✓":"!"):"♫";
            canvas.drawText(symbol,x,y+dp(8),paint);
            if(recording){for(int i=0;i<7;i++){float h=dp(2+8*(recorder==null?.1f:recorder.level))*(.4f+(i%3)*.3f);canvas.drawRect(x-dp(12)+i*dp(4),y-h,x-dp(10)+i*dp(4),y+h,paint);}}
            if(count>0&&!recording){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawArc(x-r,y-r,x+r,y+r,(now/8)%360,95,false,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(11));canvas.drawText(String.valueOf(count),getWidth()-dp(10),dp(13),paint);}
            if(recording||needs||count>0||understanding||now<flashUntil)postInvalidateDelayed(80);
        }
    }
}
