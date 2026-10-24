package com.silentauto.shell;
import android.os.Looper;
import android.view.MotionEvent;
import java.lang.reflect.*;
public final class TouchFlagsDeviceRegression {
 public static void main(String[]args)throws Exception{
  Looper.prepareMainLooper();ShellContext.init();InputBridge input=new InputBridge();Method motion=InputBridge.class.getDeclaredMethod("motion",int.class,long.class,long.class,int.class,float.class,float.class);motion.setAccessible(true);int flag=MotionEvent.class.getField("FLAG_NO_FOCUS_CHANGE").getInt(null);
  for(int display:new int[]{0,8})for(int action:new int[]{0,1,2,3}){MotionEvent event=(MotionEvent)motion.invoke(input,display,1L,2L,action,30f,40f);if(event.getAction()!=action||event.getX()!=30||event.getY()!=40||event.getFlags()!=(display==0?0:flag)||event.getDeviceId()!=(display==0?0:-1))throw new AssertionError("Invalid event for display "+display);event.recycle();}
  boolean blocked=false;try{input.key(9999,android.view.KeyEvent.KEYCODE_ENTER);}catch(com.silentauto.shell.ext.guard.BackgroundKeyPolicy.Blocked expected){blocked=true;}if(!blocked)throw new AssertionError("Native key reached injection on unverified background");
  Method delta=AiClient.class.getDeclaredMethod("delta",String.class,java.util.Map.class,StringBuilder.class);delta.setAccessible(true);
  StringBuilder reasoning=new StringBuilder();java.util.Map<Integer,Object> calls=new java.util.LinkedHashMap<>();
  String first="{\"choices\":[{\"delta\":{\"reasoning_content\":\"part1\"}}]}";
  String second="{\"choices\":[{\"delta\":{\"reasoning_content\":\"part2\",\"content\":\"answer\",\"tool_calls\":[{\"index\":0,\"id\":\"call1\",\"function\":{\"name\":\"get_ui_layout\",\"arguments\":\"{}\"}}]}}]}";
  delta.invoke(null,first,calls,reasoning);Object content=delta.invoke(null,second,calls,reasoning);
  if(!reasoning.toString().equals("part1part2")||!"answer".equals(content)||calls.size()!=1)throw new AssertionError("Streaming reasoning/tool chunks lost");
  System.out.println("PASS streaming reasoning retained separately from answer and tool calls");
  String probeId="atomic-trajectory-probe-"+System.nanoTime();
  com.silentauto.shell.ext.skill.TrajectoryRecorder recorder=new com.silentauto.shell.ext.skill.TrajectoryRecorder();recorder.prepare(probeId,"test");
  com.silentauto.shell.ext.TaskOutcome outcome=new com.silentauto.shell.ext.TaskOutcome("FAILED","probe","","");recorder.snapshotOutcome(probeId,outcome);
  java.io.File file=new java.io.File("/data/local/tmp/shadowauto/trajectories",probeId+".json");
  java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Thread writer=new Thread(()->{try{for(int i=0;i<100;i++)recorder.snapshotOutcome(probeId,outcome);}catch(Throwable e){failure.set(e);}});writer.start();int reads=0;
  try{do{try(java.io.Reader reader=new java.io.FileReader(file)){com.google.gson.JsonObject parsed=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();if(!probeId.equals(parsed.get("taskId").getAsString()))throw new AssertionError("wrong trace");reads++;}}while(writer.isAlive());writer.join();if(failure.get()!=null)throw new AssertionError(failure.get());}finally{writer.join();file.delete();}
  System.out.println("PASS atomic trajectory publication across "+reads+" concurrent reads");
  System.out.println("PASS native background key blocked before injection");
  System.out.println("PASS production touch DOWN/UP/MOVE/CANCEL keep coordinates/action; primary flags=0; secondary no-focus flag="+flag);
 }
}
