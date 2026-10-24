package com.silentauto.shell;
import android.os.*;import android.graphics.Bitmap;import java.lang.reflect.*;import com.google.gson.*;
/** On-device regression: normal capture, unavailable mirror, and repeated resource release. */
public class CaptureMirrorDeviceRegression {
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();ShellContext.init();Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("currentActivityThread").invoke(null);Field instrumentation=at.getDeclaredField("mInstrumentation");instrumentation.setAccessible(true);instrumentation.set(thread,new android.app.Instrumentation());
  for(int source:new int[]{0,-1})try{new CaptureMirror(source,1,1,null);throw new AssertionError("primary source allowed");}catch(IllegalArgumentException expected){}
  LogHub logs=new LogHub();UiBridge ui=new UiBridge(logs);ui.connect();DisplaySpec spec=DisplaySpec.current(logs);JsonArray results=new JsonArray();
  for(int i=0;i<3;i++) {
   VideoStreamer video=new VideoStreamer(logs,"mirror-fault-"+i,spec.width,spec.height);video.start();VirtualDisplaySession vd=null;
   try {
    vd=VirtualDisplaySession.create(spec.width,spec.height,spec.dpi,video.inputSurface());new AppCatalog(logs).launchPackage("mirror-fault", "com.huawei.calculator",vd.displayId);SystemClock.sleep(1000);
    Bitmap good=vd.captureBitmap(8,120);if(good==null)throw new AssertionError("normal capture missing");good.recycle();
    Field field=VirtualDisplaySession.class.getDeclaredField("captureMirror");field.setAccessible(true);CaptureMirror mirror=(CaptureMirror)field.get(vd);if(mirror==null)throw new AssertionError("not using mirror");mirror.close();
    if(vd.captureBitmap(8,120)!=null)throw new AssertionError("closed mirror returned stale frame");
    JsonObject dump=JsonParser.parseString(ui.dump(vd.displayId,spec.width,spec.height,UiBridge.MODE_FULL)).getAsJsonObject();if(dump.get("rootsUnavailable").getAsBoolean())throw new AssertionError("capture failure broke UI");
    JsonObject stats=vd.captureStats();if(stats.get("surfaceSwitches").getAsLong()!=0||stats.get("misses").getAsLong()!=1)throw new AssertionError("unsafe fallback");stats.addProperty("displayId",vd.displayId);stats.addProperty("rootReadableAfterFailure",true);results.add(stats);
   }finally {if(vd!=null){vd.release();vd.release();}video.stop();}
  }
  System.out.println("DEVICE_REGRESSION "+results);System.exit(0);
 }
}
