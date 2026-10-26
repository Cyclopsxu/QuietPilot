import com.silentauto.shell.ext.guard.*;

public final class MainInputIdleRegression {
    static final String UI="{\"windows\":[{\"type\":1,\"root\":{\"package\":\"viewer\",\"visible\":true}}]}";
    static final String HIDDEN="mInputShown=false\nmIsInputViewShown=true";
    static final String EMPTY="RecentQueue: <empty>";
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void blockedLease(String input)throws Exception{
        try{MainInputIdleGuard.checkLease(input,10_000,11_000);throw new AssertionError("lease should yield");}
        catch(MainInputIdleGuard.Busy expected){}
    }
    public static void main(String[] args)throws Exception {
        MainInputIdleGuard g=new MainInputIdleGuard();
        ok(!g.observe(UI,HIDDEN,EMPTY,0),"must establish an idle interval first");
        ok(!g.observe(UI,HIDDEN,EMPTY,1999)&&g.observe(UI,HIDDEN,EMPTY,2000),"two seconds of quiet admits passive viewing");
        ok(!g.observe(UI,"mInputShown=true",EMPTY,3000),"keyboard visible blocks even without text changes");
        ok(!g.observe(UI,HIDDEN,EMPTY,4000)&&g.observe(UI,HIDDEN,EMPTY,6000),"keyboard dismissal requires fresh idle interval");
        String editor=UI.replace("\"visible\":true","\"visible\":true,\"editable\":true,\"focused\":true");
        ok(!g.observe(editor,HIDDEN,EMPTY,7000),"focused editor blocks hardware/custom input with keyboard hidden");
        ok(g.reason().contains("输入框"),"focused-editor case must exercise editing guard, not missing visibility");
        ok(!g.observe("{\"rootsUnavailable\":true}",HIDDEN,EMPTY,8000),"unreadable main UI is not idle");
        ok(!g.observe(UI.replace("\"visible\":true","\"visible\":false"),HIDDEN,EMPTY,8000),"invisible app is not evidence of main-screen idle");
        ok(!g.observe(UI.replace("\"type\":1","\"type\":2"),HIDDEN,EMPTY,8000),"IME-only tree cannot stand in for main app");
        ok(!g.observe(UI,"unknown",EMPTY,8000)&&!g.observe(UI,HIDDEN,"unknown",8000),"missing IME or dispatcher data blocks");
        String recent="RecentQueue:\n MotionEvent(displayId=0, age=300ms)";
        ok(!g.observe(UI,HIDDEN,recent,9000),"main touch resets quiet interval");
        String own="RecentQueue:\n MotionEvent(displayId=12, age=1ms)\n MotionEvent(displayId=0, age=4000ms)";
        ok(!g.observe(UI,HIDDEN,own,10_000)&&g.observe(UI,HIDDEN,own,12_000),"background touches cannot masquerade as main input");
        ok(!g.observe(UI,HIDDEN,"RecentQueue:\n KeyEvent(displayId=0, age=10ms)",13_000),"hardware key activity blocks");
        ok(!g.observe(UI,HIDDEN,"RecentQueue:\n MotionEvent(displayId=0)",14_000),"unknown event age blocks");
        MainInputIdleGuard.checkLease(own,10_000,11_000);
        blockedLease(recent);blockedLease("unknown");blockedLease("RecentQueue:\n KeyEvent(displayId=0)");
        class Host implements InputFocusTransaction.Host {
            int focus=0;boolean restored,entered;
            public int mainTask(){return 7;}public int focusedDisplay(){return focus;}
            public void begin(){}public void enter(){focus=12;entered=true;}
            public void check()throws Exception{MainInputIdleGuard.checkLease(recent,10_000,11_000);}
            public void restore(int task){focus=0;restored=true;}public void end(boolean success){ok(success,"main focus must restore");}
        }
        Host host=new Host();
        try{InputFocusTransaction.run(host,12,()->{throw new AssertionError("user input must preempt paste");});throw new AssertionError("lease should abort");}
        catch(MainInputIdleGuard.Busy expected){}
        ok(host.entered&&host.restored&&host.focus==0,"activity during lease restores focus before retry");
        System.out.println("PASS input-idle gate, visible keyboard/focused editor, quiet reset, own-display exclusion, missing readings, main-key/touch preemption and focus restoration");
    }
}
