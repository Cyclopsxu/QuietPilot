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
        System.out.println("LoopDetectorRegression passed");
    }
}
