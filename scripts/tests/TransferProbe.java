package com.silentauto.shell.ext.tests;
import com.silentauto.shell.ext.handoff.*;
import com.silentauto.shell.ext.skill.ScreenFingerprint;
import java.util.*;
public final class TransferProbe {
 static final String PKG="fixture.app";
 static final List<String> ANCHORS=List.of("餐厅A","地址A");
 static final class Host implements TaskDisplayTransfer.Host {
  int display=9,moves;boolean denied,wrongPage,wrongTask,missing,rollbackDenied,cancel;
  public String command(String...args)throws Exception{
   if(Arrays.equals(args,new String[]{"am","stack","list"}))return missing&&display==0?"":"RootTask id=42 displayId="+display+"\n  taskId="+(wrongTask&&display==0?99:42)+": fixture.app/.Home visible=true topActivity=ComponentInfo{fixture.app/.Details}\n";
   if(!Arrays.equals(Arrays.copyOf(args,3),new String[]{"am","display","move-stack"}))throw new AssertionError("Unexpected launch");
   moves++;if(denied||(rollbackDenied&&args[4].equals("9")))throw new java.io.IOException("denied");display=Integer.parseInt(args[4]);return "";
  }
  public ScreenFingerprint page(int id){return new ScreenFingerprint(PKG,"",Arrays.asList("返回","搜索","分享","收藏","评价","优惠","订座","买单",wrongPage&&id==0?"餐厅B":"餐厅A",wrongPage&&id==0?"地址B":"地址A"));}
  public String foreground(){return PKG;}
  public boolean cancelled(){return cancel&&display==0;}
  public void pause(){}
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static TaskDisplayTransfer.Result run(Host h){return new TaskDisplayTransfer(h).move(9,PKG,ANCHORS);}
 public static void main(String[]args){
  Host success=new Host();require(run(success).moved&&success.display==0&&success.moves==1,"success");
  Host denied=new Host();denied.denied=true;TaskDisplayTransfer.Result d=run(denied);require(!d.moved&&d.previewAvailable&&d.restored&&denied.display==9,"denied fallback");
  Host page=new Host();page.wrongPage=true;require(page.page(9).similarity(page.page(0))>=.6,"old false positive fixture");TaskDisplayTransfer.Result p=run(page);require(!p.moved&&p.restored&&page.display==9&&page.moves==2,"different restaurant must roll back despite common controls");
  Host missing=new Host();missing.missing=true;TaskDisplayTransfer.Result m=run(missing);require(!m.previewAvailable&&m.outcome.equals("unavailable")&&!m.message.contains("已保留原"),"vanished task is not a preview");
  Host replaced=new Host();replaced.wrongTask=true;require(!run(replaced).previewAvailable&&replaced.moves==1,"never move a replacement task");
  Host rollback=new Host();rollback.wrongPage=true;rollback.rollbackDenied=true;require(!run(rollback).previewAvailable,"failed rollback is unavailable");
  Host cancelled=new Host();cancelled.cancel=true;require(!run(cancelled).moved&&cancelled.display==9,"cancel rolls back before release");
  Host empty=new Host();require(new TaskDisplayTransfer(empty).move(9,PKG,List.of()).previewAvailable&&empty.moves==0,"no anchors stays in preview");
  require(!PageIdentity.contains(success.page(9),List.of("收藏")),"generic label is not identity");
  require(PageIdentity.contains(new ScreenFingerprint(PKG,"",List.of("极致·东岛\u20063\u2006号0")),List.of("极致·东岛3号")),"Unicode spacing normalizes");
  require(ResultRetention.requested("选一家适合约会的餐厅","view"),"normal selection retained");
  require(ResultRetention.requested("发送成功后结束","complete"),"completed action retained unless explicitly opted out");
  require(ResultRetention.requested("完成后停在详情页面","complete"),"explicit retain wins");
  require(!ResultRetention.requested("不要停留页面","view"),"explicit opt out wins");
  require(ResultRetention.requested("查询餐厅", ""),"old verifier metadata is conservative");
  System.out.println("PASS entity identity, generic-text false positive, rollback, vanished/replaced task, failed rollback, cancellation, missing anchors, Unicode and semantic delivery");
 }
}
