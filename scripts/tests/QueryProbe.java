package com.silentauto.shell.ext.tests;
import com.google.gson.*;import com.silentauto.shell.*;import com.silentauto.shell.ext.query.*;import com.silentauto.shell.ext.guard.*;import com.silentauto.shell.ext.skill.*;import java.util.*;
public final class QueryProbe {
 static String ui="""
 {"windows":[{"type":1,"root":{"package":"fixture.app","children":[
 {"text":"发送","targetIndex":1,"clickable":true,"bounds":"0,0,100,100"},
 {"hint":"搜索","editable":true,"focused":true,"inputIndex":1,"targetIndex":2},
 {"hint":"Message","editable":true,"inputIndex":2,"targetIndex":3},
 {"text":"物流状态：派送中","visible":true},
 {"text":"SECRET","password":true}
 ]}}]}
 """;
 static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  ActionDecision a=new ActionDecision();a.action="tap_target";a.targetIndex=1;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"send blocked");
  a.action="tap";a.x=20;a.y=20;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"coordinate send blocked");
  a.action="focus_input";a.inputIndex=2;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"message field blocked");a.inputIndex=1;require(ReadOnlyGuard.blocked(a,ui).isEmpty(),"search focus allowed");
  a.action="text";a.inputIndex=0;require(ReadOnlyGuard.blocked(a,ui).isEmpty(),"focused search accepts text");
  a.action="request_user";a.kind="payment";require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"query payment handoff blocked");a.kind="captcha";require(ReadOnlyGuard.blocked(a,ui).isEmpty(),"manual captcha allowed");
  a.action="set_clipboard";require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"no automatic clipboard write");
  String city="{\"width\":100,\"height\":800,\"windows\":[{\"type\":1,\"root\":{\"package\":\"com.sankuai.meituan\",\"id\":\"com.sankuai.meituan:id/msc_root\",\"children\":[";
  String[] cityTexts={"国内城市","海外地区","城市/区县/商场等地点","城市开头字母","K","我的地址","立即预订"};
  for(int i=0;i<cityTexts.length;i++){if(i>0)city+=",";city+="{\"package\":\"com.sankuai.meituan\",\"text\":\""+cityTexts[i]+"\",\"visible\":true,\"enabled\":true,\"clickable\":false,\"bounds\":\"0,"+(i*100)+",100,"+(i*100+80)+"\"}";}
  city+="]}}]}";
  a.action="tap";a.x=50;a.y=10;require(ReadOnlyGuard.blocked(a,city).isEmpty(),"MSC public domestic tab allowed from current text bounds");
  a.y=410;require(ReadOnlyGuard.blocked(a,city).isEmpty(),"MSC city alphabet allowed");
  a.y=210;require(ReadOnlyGuard.blocked(a,city).isEmpty(),"MSC public search entry allowed");
  a.x=100;require(!ReadOnlyGuard.blocked(a,city).isEmpty(),"outside display rejected before clamping");a.x=50;
  a.y=510;require(!ReadOnlyGuard.blocked(a,city).isEmpty(),"saved addresses never promoted");
  a.y=610;require(!ReadOnlyGuard.blocked(a,city).isEmpty(),"booking text never promoted");
  a.y=10;require(!ReadOnlyGuard.blocked(a,city.replace("msc_root","other_root")).isEmpty(),"MSC root required");
  require(!ReadOnlyGuard.blocked(a,city.replace("海外地区","普通内容")).isEmpty(),"city context required");
  require(!ReadOnlyGuard.blocked(a,city.replace("com.sankuai.meituan","other.app")).isEmpty(),"other apps stay blocked");
  require(!ReadOnlyGuard.blocked(a,city.replace("\"visible\":true","\"visible\":false")).isEmpty(),"hidden city controls stay blocked");
  String amap="""
  {"windows":[{"type":1,"root":{"package":"com.autonavi.minimap","children":[
   {"package":"com.autonavi.minimap","class":"com.autonavi.minimap.ajx3.widget.view.SpringHorizontalScrollView","visible":true,"bounds":"180,160,1100,250"},
   {"package":"com.autonavi.minimap","class":"android.widget.EditText","visible":true,"editable":true,"focused":true,"inputIndex":1,"bounds":"200,160,1080,250"},
   {"package":"com.autonavi.minimap","desc":"搜索","targetIndex":2,"visible":true,"bounds":"880,310,1060,410"}
  ]}}]}
  """;
  a.action="text";a.inputIndex=1;require(ReadOnlyGuard.blocked(a,amap).isEmpty(),"anonymous Amap editor bound to real search bar and submit");
  require(!ReadOnlyGuard.blocked(a,amap.replace("SpringHorizontalScrollView","OtherView")).isEmpty(),"anonymous editor needs its search bar");
  require(!ReadOnlyGuard.blocked(a,amap.replace("880,310,1060,410","880,1100,1060,1200")).isEmpty(),"distant search label cannot bless editor");
  require(!ReadOnlyGuard.blocked(a,amap.replace("com.autonavi.minimap","other.app")).isEmpty(),"anonymous input in other apps rejected");
  require(!ReadOnlyGuard.blocked(a,amap.replace("\"focused\":true","\"password\":true,\"focused\":true")).isEmpty(),"anonymous password rejected");
  String ambiguous=amap.replace("\"desc\":\"搜索\"", "\"editable\":true,\"desc\":\"搜索\"");
  require(!ReadOnlyGuard.blocked(a,ambiguous).isEmpty(),"distinct second editor makes form ambiguous");
  String location="""
  {"windows":[{"type":1,"root":{"package":"com.sankuai.meituan","id":"com.sankuai.meituan:id/msc_root","children":[
   {"package":"com.sankuai.meituan","class":"android.widget.EditText","visible":true,"editable":true,"focused":true,"inputIndex":1,"hint":"城市/区县/商场等地点"}
  ]}}]}
  """;
  a.action="text";a.inputIndex=1;require(ReadOnlyGuard.blocked(a,location).isEmpty(),"MSC exact location-search hint allowed");
  require(!ReadOnlyGuard.blocked(a,location.replace("msc_root","other_root")).isEmpty(),"location editor requires MSC root");
  require(!ReadOnlyGuard.blocked(a,location.replace("城市/区县/商场等地点","收货地址")).isEmpty(),"saved-address editor not promoted");
  require(!ReadOnlyGuard.blocked(a,location.replace("\"visible\":true","\"visible\":false")).isEmpty(),"hidden location editor rejected");
  String visible=AnswerVerifier.visible(ui,"");require(!visible.contains("SECRET"),"password omitted");require(!AnswerVerifier.grounded("inputIndex",visible),"JSON schema keys are not evidence");
  var answer=AnswerVerifier.extract("物流状态",ui,"",null,(s,p,image)->"{\"found\":true,\"answer\":\"派送中\",\"quote\":\"物流状态：派送中\"}");require(answer.found,"grounded answer");
  boolean rejected=false;try{AnswerVerifier.extract("状态",ui,"",null,(s,p,image)->"{\"found\":true,\"answer\":\"已签收\",\"quote\":\"已签收\"}");}catch(IllegalArgumentException e){rejected=true;}require(rejected,"unsupported extraction rejected");
  require(AnswerVerifier.agrees("正在派送中","派送中",(s,p,i)->{throw new AssertionError("unneeded comparison call");}),"contained answer avoids model comparison");
  JsonObject actionSkill=JsonParser.parseString("{\"type\":\"action\",\"skillId\":\"send\"}").getAsJsonObject();JsonArray active=new JsonArray();active.add(actionSkill);require(new SkillMatcher().match("查询","query",active,p->{throw new AssertionError("wrong-type match model call");})==null,"cross-type skill rejected before model");
  System.out.println("PASS query guard, raw coordinates, search input, handoff, clipboard, private evidence, grounded extraction and type-safe matching");
 }
}
