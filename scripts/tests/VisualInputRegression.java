import com.silentauto.shell.ext.guard.*;
import com.silentauto.shell.ActionDecision;
import com.google.gson.*;

public final class VisualInputRegression {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class Host implements InputFocusTransaction.Host {
  int main=7,focus=0,restoredTask=-1;boolean begun,ended,restored;
  public int mainTask(){return main;}public int focusedDisplay(){return focus;}
  public void begin(){begun=true;}public void enter(){focus=12;}
  public void check(){if(main!=7)throw new IllegalStateException("User switched main app");}
  public void restore(int task){ok(!Thread.currentThread().isInterrupted(),"restoration must run even after interruption");restoredTask=task;focus=0;}
  public void end(boolean success){ended=true;restored=success;}
 }
 static void failure(Host host,java.util.concurrent.Callable<Void> input)throws Exception {
  boolean failed=false;try{InputFocusTransaction.run(host,12,input);}catch(Exception expected){failed=true;}
  ok(failed&&host.ended&&host.restored&&host.focus==0,"failure restores main focus");
 }
 public static void main(String[] args)throws Exception{
  com.silentauto.shell.ext.TaskProgress progress=new com.silentauto.shell.ext.TaskProgress("visual-regression","goal",new com.silentauto.shell.LogHub());
  progress.call("visual_planner");ok(progress.json().getAsJsonObject("llmCalls").get("visual_planner").getAsInt()==1,"new visual role is counted without null unboxing");
  String body="测试 1792986804",recipient="文件传输助手",goal="向"+recipient+"发送："+body;
  VisualActionPolicy.validateMessageBinding(goal,body,recipient);VisualActionPolicy.validateMessageBinding(goal,"","");
  for(String[] invalid:new String[][]{
    {goal,body,""},{goal,"",recipient},{goal,"测试1792986805",recipient},{goal,body,"其他联系人"},
    {goal+" 没有编号","没有编号",recipient},{goal+" 1792986804 1792986805","1792986804 1792986805",recipient}}){
   boolean blocked=false;try{VisualActionPolicy.validateMessageBinding(invalid[0],invalid[1],invalid[2]);}catch(IllegalArgumentException expected){blocked=true;}
   ok(blocked,"partial, unauthorized or ambiguous exact message binding rejected before starting task");
  }
  VisualActionPolicy.validateMessageVersion(false,0,1);VisualActionPolicy.validateMessageVersion(true,1,1);
  boolean amended=false;try{VisualActionPolicy.validateMessageVersion(true,0,1);}catch(IllegalStateException expected){amended=true;}
  ok(amended,"sent message cannot prove an amended goal or trigger replay");
  Host normal=new Host();ok("ok".equals(InputFocusTransaction.run(normal,12,()->"ok"))&&normal.restoredTask==7,"normal input restores original task");
  Host failed=new Host();failure(failed,()->{throw new IllegalStateException("No paste menu");});
  Host switched=new Host();failure(switched,()->{switched.main=9;return null;});ok(switched.restoredTask==9,"restore current user task instead of stale task");
  Host cancelled=new Host();failure(cancelled,()->{Thread.currentThread().interrupt();throw new InterruptedException();});ok(Thread.interrupted(),"cancellation flag preserved after cleanup");
  Host busy=new Host();busy.focus=8;boolean rejected=false;try{InputFocusTransaction.run(busy,12,()->null);}catch(IllegalStateException expected){rejected=true;}
  ok(rejected&&!busy.begun,"do not acquire focus already owned by another virtual task");
  JsonArray menu=JsonParser.parseString("[{\"text\":\"粘贴拍摄输入\",\"confidence\":0.99,\"bounds\":{\"left\":60,\"top\":100,\"right\":360,\"bottom\":160}}]").getAsJsonArray();
  int[] target=VisualActionPolicy.pasteTarget(menu,1000,2000);ok(target[0]==110&&target[1]==130,"merged OCR menu clicks paste portion only");
  menu.add(menu.get(0).deepCopy());ok(VisualActionPolicy.pasteTarget(menu,1000,2000)==null,"ambiguous paste candidates are rejected");
  ActionDecision grounded=new ActionDecision();grounded.action="tap";grounded.x=365;grounded.y=237;
  VisualActionPolicy.ground(grounded,JsonParser.parseString("{\"x\":365,\"y\":237,\"coordinate_space\":\"normalized_1000\"}").getAsJsonObject(),new JsonArray(),1152,2376);
  ok(grounded.x==420&&grounded.y==563,"Qwen normalized coordinates map to actual display pixels");
  ActionDecision moved=new ActionDecision();moved.action="tap";
  JsonObject label=JsonParser.parseString("{\"target_text\":\"目标会话\"}").getAsJsonObject();
  JsonArray rows=JsonParser.parseString("[{\"text\":\"目标会话\",\"confidence\":0.99,\"bounds\":{\"centerX\":360,\"centerY\":330}}]").getAsJsonArray();
  VisualActionPolicy.ground(moved,label,rows,1152,2376);
  rows.get(0).getAsJsonObject().getAsJsonObject("bounds").addProperty("centerY",540);
  VisualActionPolicy.ground(moved,label,rows,1152,2376);ok(moved.y==540,"fresh OCR relocates reordered conversation before touch");
  JsonArray title=JsonParser.parseString("[{\"text\":\"文件传输助手\",\"confidence\":0.99,\"bounds\":{\"bottom\":100}}]").getAsJsonArray();
  ok(VisualActionPolicy.hasTitle(title,"文件传输助手",2376),"recipient title is required before exact body injection");
  title.get(0).getAsJsonObject().getAsJsonObject("bounds").addProperty("bottom",1000);
  ok(!VisualActionPolicy.hasTitle(title,"文件传输助手",2376),"message body mentioning recipient is not the chat title");
  JsonArray messages=JsonParser.parseString("[{\"text\":\"1792984685（仅文件传输助手）\",\"confidence\":0.99,\"bounds\":{\"centerX\":700,\"centerY\":1000}}]").getAsJsonArray();
  ok(VisualActionPolicy.messageMarker(messages,"测试 1792984685（仅文件传输助手）")[1]==1000,"copy targets unique authorized message identifier");
  ok(VisualActionPolicy.messageMarker(messages,"测试 1792984686")==null,"do not copy an old test message");
  messages.add(messages.get(0).deepCopy());ok(VisualActionPolicy.messageMarker(messages,"测试 1792984685")==null,"ambiguous message identifier cannot prove the body");
  JsonArray draft=JsonParser.parseString("[{\"text\":\"1792986071\",\"confidence\":0.99,\"bounds\":{\"top\":2200}}]").getAsJsonArray();
  ok(VisualActionPolicy.uniqueDraftMarker(draft,"正文1792986071",2376),"new marker appears once in draft area");
  JsonObject old=draft.get(0).getAsJsonObject().deepCopy();old.addProperty("text","1792984685");old.getAsJsonObject("bounds").addProperty("top",1000);draft.add(old);
  ok(VisualActionPolicy.uniqueDraftMarker(draft,"正文1792986071",2376),"similar old number is not a duplicate");
  old.addProperty("text","1792986071");ok(!VisualActionPolicy.uniqueDraftMarker(draft,"正文1792986071",2376),"matching history marker blocks sending");
  draft.remove(1);draft.get(0).getAsJsonObject().addProperty("confidence",.7);ok(!VisualActionPolicy.uniqueDraftMarker(draft,"正文1792986071",2376),"uncertain marker cannot allow send");
  ActionDecision a=new ActionDecision();a.action="text";a.text="中文测试";a.x=300;a.y=1000;VisualActionPolicy.validate(a,1000,2000);
  a.action="key";rejected=false;try{VisualActionPolicy.validate(a,1000,2000);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"visual mode cannot enable native keys");
  a.action="tap";a.x=1000;rejected=false;try{VisualActionPolicy.validate(a,1000,2000);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"reject off-display coordinates");
  System.out.println("PASS visual input success/failure/cancellation cleanup, current-main restoration, focus ownership, paste ambiguity and action bounds");
 }
}
