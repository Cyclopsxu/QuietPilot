import com.google.gson.*;
import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.ext.guard.ReadOnlyGuard;
import com.silentauto.shell.ext.guard.ObservedTarget;
import com.silentauto.shell.ext.guard.BackgroundFocusGuard;
import com.silentauto.shell.ext.handoff.*;
import com.silentauto.shell.ext.skill.*;
import java.util.*;

public final class InteractionRegression {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static String route(String pkg,String role,boolean password){return """
        {"windows":[{"type":1,"root":{"package":"%s","children":[
        {"package":"%s","label":"%s","visible":true,"bounds":"175,129,830,238","children":[
        {"package":"%s","class":"android.widget.EditText","editable":true,"focused":true,"password":%s,"inputIndex":1,"text":"康定市人民政府","bounds":"238,129,687,238"}]},
        {"package":"%s","text":"开始导航","clickable":true,"targetIndex":1,"bounds":"0,300,200,400"},
        {"package":"%s","text":"现在出发，最快3分钟接驾 去打车","clickable":true,"targetIndex":2,"bounds":"210,300,400,400"}
        ]}}]}
        """.formatted(pkg,pkg,role,pkg,password,pkg,pkg);}
    static UiSnapshot note(String title,String body){JsonObject root=new JsonObject();root.addProperty("package","com.huawei.notepad");JsonArray children=new JsonArray();
        for(String[] pair:new String[][]{{"com.huawei.notepad:id/title",title},{"com.huawei.notepad:id/notetext_textview",body}}){JsonObject n=new JsonObject();n.addProperty("package","com.huawei.notepad");n.addProperty("id",pair[0]);n.addProperty("text",pair[1]);n.addProperty("visible",true);children.add(n);}root.add("children",children);
        JsonObject window=new JsonObject();window.addProperty("type",1);window.add("root",root);JsonArray windows=new JsonArray();windows.add(window);JsonObject ui=new JsonObject();ui.add("windows",windows);return new UiSnapshot(ui.toString());}
    public static void main(String[] args){
        com.silentauto.shell.ext.guard.BackgroundKeyPolicy.requireAllowed(0,false);
        com.silentauto.shell.ext.guard.BackgroundKeyPolicy.requireAllowed(8,true);
        boolean keyBlocked=false;
        try {com.silentauto.shell.ext.guard.BackgroundKeyPolicy.requireAllowed(8,false);}
        catch(com.silentauto.shell.ext.guard.BackgroundKeyPolicy.Blocked expected){keyBlocked=true;}
        require(keyBlocked,"unverified background keys are blocked before injection");
        BackgroundFocusGuard focus=new BackgroundFocusGuard();
        focus.observe(true,7,0);require(focus.issue().isEmpty(),"main display focus does not pause");
        focus.observe(false,7,7);require(focus.issue().isEmpty(),"explicit handoff does not trigger background alarm");
        focus.observe(true,7,7);require(!focus.issue().isEmpty()&&focus.triggered(),"background stealing global focus latches pause");
        focus.observe(true,7,0);require(!focus.issue().isEmpty(),"focus returning cannot automatically resume input");
        focus.explicitRetry();require(focus.issue().isEmpty()&&focus.triggered(),"explicit retry clears latch but preserves incident history");
        focus.observe(true,7,-1);require(focus.issue().isEmpty(),"unknown focus is not fabricated interference evidence");
        focus.observe(true,7,7);require(!focus.issue().isEmpty(),"repeated interference pauses again");
        ActionDecision a=new ActionDecision();String pkg="com.autonavi.minimap",ui=route(pkg,"终点，康定市人民政府",false);
        for(String action:List.of("focus_input","text","clear_text","submit_search","key")){a.action=action;a.inputIndex=1;a.key="enter";require(ReadOnlyGuard.blocked(a,ui).isEmpty(),"route input blocked: "+action);}
        a.action="text";a.inputIndex=0;require(ReadOnlyGuard.blocked(a,ui).isEmpty(),"focused endpoint accepts text");
        require(!ReadOnlyGuard.blocked(a,route("other.app","终点，康定市人民政府",false)).isEmpty(),"other app must not inherit map permission");
        require(!ReadOnlyGuard.blocked(a,route(pkg,"留言",false)).isEmpty(),"message field must remain blocked");
        require(!ReadOnlyGuard.blocked(a,route(pkg,"终点，康定市人民政府",true)).isEmpty(),"password blocked");
        require(!ReadOnlyGuard.blocked(a,route(pkg,"起点，A 终点，B 切换起终点",false)).isEmpty(),"page-wide route words do not authorize unrelated field");
        a.action="tap_target";a.targetIndex=1;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"navigation must stay blocked");a.targetIndex=2;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"ride hailing must stay blocked");
        a.action="tap";a.x=10;a.y=350;require(!ReadOnlyGuard.blocked(a,ui).isEmpty(),"raw navigation tap must stay blocked");
        String planned="{\"windows\":[{\"type\":1,\"root\":{\"package\":\"com.autonavi.minimap\"}}],\"targets\":[{\"targetIndex\":27,\"path\":\"w0/route/start\",\"label\":\"起点，我的位置\",\"bounds\":\"175,21,830,130\",\"class\":\"android.view.ViewGroup\",\"id\":\"\"}]}";
        String renumbered=planned.replace("27","44");
        require(ObservedTarget.resolve(planned,renumbered,27).get("targetIndex").getAsInt()==44,"renumbered target resolves by observed identity");
        require(ObservedTarget.resolve(planned,planned.replace("起点，我的位置","开始导航"),27)==null,"reused index must not activate navigation");
        require(ObservedTarget.resolve(planned,planned.replace("175,21,830,130","175,2000,830,2100"),27)==null,"moved control requires new observation");
        require(ObservedTarget.resolve(planned,planned.replace("route/start","other/start"),27)==null,"identical label on different path rejected");
        require(ObservedTarget.resolve(planned,planned.replace("com.autonavi.minimap","other.app"),27)==null,"different app with identical controls rejected");
        String title="测试｜川西5天自驾环线",body="成都往返｜2人自驾｜出发日期待定。".repeat(12),goal="新建笔记"+title+"正文："+body;
        UiSnapshot note=note(title,body);String np="com.huawei.notepad";
        List<String> anchors=PageIdentity.validatedAnchors(note,np,goal,List.of());require(anchors.size()==2&&anchors.get(0).equals(title),"empty model anchors recover from goal-bound source title and body");
        require(PageIdentity.contains(PageIdentity.from(note,np),anchors),"resolved anchors present on source");
        require(!PageIdentity.contains(PageIdentity.from(note("另一条笔记",body),np),anchors),"different note cannot pass with same body");
        require(!PageIdentity.contains(PageIdentity.from(note(title,"别的正文"),np),anchors),"different body cannot pass with same title");
        require(PageIdentity.validatedAnchors(note,np,"无关的目标",List.of()).isEmpty(),"unrelated title cannot become identity");
        require(PageIdentity.validatedAnchors(note,np,goal,List.of("D1…D5","共1894字")).equals(anchors),"summarized/count anchors replaced by bound source evidence");
        require(PageIdentity.validatedAnchors(note,np,goal,List.of(title)).equals(List.of(title)),"valid explicit anchors preserved");
        require(PageIdentity.validatedAnchors(note,np,"无关目标",List.of("不存在的名称")).isEmpty(),"invalid anchors cannot announce completion");
        require(!PageIdentity.contains(new ScreenFingerprint(np,"",List.of("1894","共1894字")),List.of("1894")),"numeric count is not identity");
        System.out.println("PASS latched background focus guard, target renumbering/drift, route input/password/transaction guards, goal-bound document anchors and wrong title/body rejection");
    }
}
