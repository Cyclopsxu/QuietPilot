import com.google.gson.*;
import com.silentauto.shell.ext.guard.UiRecoveryBudget;
import com.silentauto.shell.ext.verify.DocumentProof;

public class UiRecoveryRegression {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static String note(String title,String body,boolean list){
  JsonObject root=new JsonObject();root.addProperty("package","com.huawei.notepad");if(list)root.addProperty("id","com.huawei.notepad:id/notes_listview");JsonArray children=new JsonArray();
  for(String[] field:list?new String[][]{{"com.huawei.notepad:id/list_title",title}}:new String[][]{{"com.huawei.notepad:id/title",title},{"com.huawei.notepad:id/notetext_textview",body}}){JsonObject n=new JsonObject();n.addProperty("package","com.huawei.notepad");n.addProperty("id",field[0]);n.addProperty("text",field[1]);n.addProperty("visible",true);children.add(n);}root.add("children",children);JsonObject window=new JsonObject();window.addProperty("type",1);window.add("root",root);JsonArray windows=new JsonArray();windows.add(window);JsonObject ui=new JsonObject();ui.add("windows",windows);return ui.toString();
 }
 public static void main(String[] args){
  ok(UiRecoveryBudget.placeholderRoot(false,true,0,false),"WeChat invisible zero-bounds placeholder is unusable");
  ok(!UiRecoveryBudget.placeholderRoot(true,true,0,false),"visible leaf isn't a placeholder");
  ok(!UiRecoveryBudget.placeholderRoot(false,false,0,false),"sized root isn't a zero-bounds placeholder");
  ok(!UiRecoveryBudget.placeholderRoot(false,true,1,false),"root with descendants remains traversable");
  ok(!UiRecoveryBudget.placeholderRoot(false,true,0,true),"meaningful leaf isn't rejected");
  ok(UiRecoveryBudget.incompleteTraversal(1,1,1,false),"root with only null children is unusable");
  ok(!UiRecoveryBudget.incompleteTraversal(1,1,0,false),"genuine leaf remains valid");
  ok(!UiRecoveryBudget.incompleteTraversal(1,12,1,false),"one missing branch does not invalidate a readable page");
  ok(!UiRecoveryBudget.incompleteTraversal(1,1,1,true),"readable root control remains actionable");
  String absent="{\"rootsUnavailable\":true,\"windows\":[]}";UiRecoveryBudget b=new UiRecoveryBudget();
  ok(b.observe(absent,true)==UiRecoveryBudget.Next.WAIT,"transient absence waits");ok(b.observe(absent,true)==UiRecoveryBudget.Next.WAIT,"bounded initial wait");ok(b.observe(absent,true)==UiRecoveryBudget.Next.RECONNECT,"connection recovers before restarting app");
  b.observe(absent,true);b.observe(absent,true);ok(b.observe(absent,true)==UiRecoveryBudget.Next.RESTART_QUERY,"query restarts once after reconnection fails");
  ok(b.observe("{\"windows\":[]}",true)==UiRecoveryBudget.Next.READY,"legacy metadata isn't fabricated as unhealthy");
  b.observe(absent,true);b.observe(absent,true);ok(b.observe(absent,true)==UiRecoveryBudget.Next.HANDOFF,"cannot restart forever");
  b=new UiRecoveryBudget();b.observe(absent,false);b.observe(absent,false);ok(b.observe(absent,false)==UiRecoveryBudget.Next.RECONNECT,"editing may reconnect without stopping app");b.observe(absent,false);b.observe(absent,false);ok(b.observe(absent,false)==UiRecoveryBudget.Next.HANDOFF,"editing never force-stops");
  UiRecoveryBudget anr=new UiRecoveryBudget();String anrUi="{\"windows\":[{\"type\":1,\"root\":{\"id\":\"android:id/aerr_wait\"}}]}";anr.observe(anrUi,true);anr.observe(anrUi,true);ok(anr.observe(anrUi,true)==UiRecoveryBudget.Next.RECONNECT,"ANR system controls trigger bounded recovery");
  ok(new UiRecoveryBudget().observe(note("记录","高德无响应，点击等待",false),true)==UiRecoveryBudget.Next.READY,"document text cannot masquerade as ANR controls");
  com.silentauto.shell.ActionDecision reserve=new com.silentauto.shell.ActionDecision();reserve.action="tap_target";reserve.targetIndex=1;
  for(String label:new String[]{"立即预订","预定","订座","预约"}){String control="{\"windows\":[{\"type\":1,\"root\":{\"package\":\"com.sankuai.meituan\",\"text\":\""+label+"\",\"targetIndex\":1}}]}";ok(!com.silentauto.shell.ext.guard.ReadOnlyGuard.blocked(reserve,control).isEmpty(),"booking control must stay blocked: "+label);}
  String title="测试｜长文",body="成都→康定\n".repeat(200)+"来源：https://example.org/景点\n正文末尾。";String goal="更新笔记\n标题："+title+"\n完整正文开始：\n"+body+"\n完整正文结束。";
  DocumentProof p=new DocumentProof();p.observe(goal,note(title,body,false));ok(p.supported()&&!p.verified(),"draft alone does not prove save");p.observe(goal,note("测试｜长…","",true));ok(!p.verified(),"truncated list card alone not sufficient");p.observe(goal,note(title,body,false));ok(p.verified(),"full long body after list and reopen proves persistence");
  p.observe(goal,note(title,body.replace("正文末尾。",""),false));ok(!p.verified(),"missing ending fails");p.observe(goal,note("同名近似",body,false));ok(!p.verified(),"wrong title fails");p.observe(goal,note(title,body.replace("example.org","wrong.org"),false));ok(!p.verified(),"wrong source fails");
  p.observe(goal,note(title,body,false));ok(!p.verified(),"restoring a draft after mutation requires a fresh list/reopen proof");
  p.observe(goal+"\n完整正文开始：\n额外\n完整正文结束。",note(title,body,false));ok(!p.supported()&&!p.verified(),"ambiguous expected body rejected");
  p.observe("只需摘要",note(title,body,false));ok(!p.supported(),"generic goals retain model verification");
  DocumentProof q=new DocumentProof();q.observe(goal,note(title,"",true));q.observe(goal,note(title,body,false));ok(!q.verified(),"existing list before writing does not prove a new save");
  String amended=goal.replace("正文末尾。","更新末尾。");p.observe(goal,note(title,body,false));p.observe(goal,note(title,"",true));p.observe(amended,note(title,body,false));ok(!p.verified(),"amendment resets old proof");
  System.out.println("PASS bounded root recovery, query-only restart, editing preservation, exact long-body save/reopen, wrong title/source/ending, ambiguous goal and amendment invalidation");
 }
}
