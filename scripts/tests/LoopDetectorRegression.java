import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.ext.guard.LoopDetector;

public final class LoopDetectorRegression {
    private static String ui(String text) {return "{\"windows\":[{\"root\":{\"text\":\""+text+"\",\"children\":[]}}]}";}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        LoopDetector loop=new LoopDetector();ActionDecision clear=new ActionDecision();clear.action="clear_text";
        loop.record(ui("156"),clear);loop.record(ui("0"),clear);loop.record(ui("0"),clear);
        check(loop.triggers()==0,"different result is progress");loop.record(ui("0"),clear);
        check(loop.triggers()==1&&loop.feedback().contains("finish / report_answer"),"first warning");
        loop.record(ui("0"),clear);loop.record(ui("0"),clear);check(loop.triggers()==1,"fresh window required");
        loop.record(ui("0"),clear);check(loop.triggers()==2,"second trigger requests verification");
        loop.reset();for(String value:new String[]{"A","B","A","B"})loop.record(ui(value),clear);
        check(loop.triggers()==1,"alternating screens");loop.reset();
        clear.source="skill";for(int i=0;i<10;i++)loop.record(ui("0"),clear);
        check(loop.triggers()==0,"skill excluded");clear.source="model";
        for(int i=0;i<8;i++)loop.record(ui(""+i),clear);check(loop.triggers()==0,"changing results excluded");
        loop.reset();clear.action="tap";for(int i=0;i<6;i++){clear.x=i;loop.record(ui("same"),clear);}
        check(loop.triggers()==0,"distinct targets excluded");
        check(com.silentauto.shell.ext.guard.SubmitTargetPolicy.alternateInput("语音搜索"),"voice search is not submit");
        check(com.silentauto.shell.ext.guard.SubmitTargetPolicy.alternateInput("Voice Search"),"English voice search");
        check(!com.silentauto.shell.ext.guard.SubmitTargetPolicy.alternateInput("搜索 android.widget.Button"),"text search remains available");
        check(com.silentauto.shell.ext.guard.NodeWritePolicy.permits(12,12,"app","app"),"own node accepted");
        check(!com.silentauto.shell.ext.guard.NodeWritePolicy.permits(12,0,"app","app"),"main display rejected");
        check(!com.silentauto.shell.ext.guard.NodeWritePolicy.permits(12,12,"memo","amap"),"wrong package rejected");
        check(!com.silentauto.shell.ext.guard.NodeWritePolicy.permits(12,-1,"app","app"),"unknown display rejected");
        var early=new com.silentauto.shell.ext.skill.ScreenFingerprint("memo","备忘录",java.util.List.of("今天 17:05","4 分钟前同步","标题","未分类"));
        var later=new com.silentauto.shell.ext.skill.ScreenFingerprint("memo","备忘录",java.util.List.of("今天 17:10","9 分钟前同步","标题","未分类"));
        check(early.similarity(later)==1,"clock and relative-time metadata must not trigger rescue");
        var content=new com.silentauto.shell.ext.skill.ScreenFingerprint("memo","备忘录",java.util.List.of("正文不同","未分类"));
        check(early.similarity(content)<.6,"semantic content changes remain detectable");
        check(new com.silentauto.shell.ext.skill.ScreenFingerprint("memo","备忘录",java.util.List.of("20字")).stableTexts.contains("20字"),"content counts remain semantic");
        String listUi="{\"windows\":[{\"type\":1,\"root\":{\"package\":\"memo\",\"children\":[{\"id\":\"add\",\"text\":\"新建\"},{\"class\":\"android.widget.ListView\",\"children\":[{\"id\":\"card\",\"text\":\"Old private list item\"}]}]}}]}";
        var beforeList=com.silentauto.shell.ext.skill.ScreenFingerprint.from(new com.silentauto.shell.ext.skill.UiSnapshot(listUi),"memo",2000);
        var afterList=com.silentauto.shell.ext.skill.ScreenFingerprint.from(new com.silentauto.shell.ext.skill.UiSnapshot(listUi.replace("Old private list item","New note and time")),"memo",2000);
        check(beforeList.similarity(afterList)==1,"changing list cards must not invalidate page identity");
        check(beforeList.stableTexts.contains("id:add")&&!beforeList.stableTexts.contains("id:card"),"keep chrome identity, exclude list content");
        System.out.println("LoopDetectorRegression passed");
    }
}
