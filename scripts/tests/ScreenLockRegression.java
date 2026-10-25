import com.silentauto.shell.ext.TaskStatus;
import com.silentauto.shell.ext.guard.MainAppConflictGuard;
import java.util.concurrent.atomic.*;

public final class ScreenLockRegression {
    static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        AtomicBoolean locked=new AtomicBoolean(),expired=new AtomicBoolean(),unreadable=new AtomicBoolean();
        AtomicLong now=new AtomicLong();AtomicReference<TaskStatus> state=new AtomicReference<>(TaskStatus.RUNNING);
        AtomicReference<String> foreground=new AtomicReference<>("other");
        try(MainAppConflictGuard guard=new MainAppConflictGuard("target","应用",()->{
            if(unreadable.get())throw new Exception("No resumed main activity");return foreground.get();
        },state::get,()->"当前步骤",(s,m)->state.set(s),()->expired.set(true),600_000,now::get,false,locked::get)){
            guard.poll();ok(guard.inputAllowed(),"unlocked input allowed");
            locked.set(true);unreadable.set(true);
            ok(!guard.inputAllowed(),"lock blocks input before watcher polls");
            guard.poll();long epoch=guard.epoch();
            ok(state.get()==TaskStatus.PAUSED,"lock pauses task");
            now.set(3_600_000);guard.poll();
            ok(!expired.get()&&state.get()==TaskStatus.PAUSED,"one hour locked cannot truncate task");
            ok(guard.epoch()==epoch,"same lock does not repeatedly invalidate planning");
            locked.set(false);unreadable.set(false);guard.poll();
            ok(state.get()==TaskStatus.RUNNING&&guard.awaitClear(),"unlock resumes with fresh observation");
            ok(!guard.awaitClear(),"recovery consumed once");
            foreground.set("target");guard.poll();now.addAndGet(600_000);guard.poll();
            ok(expired.get()&&state.get()==TaskStatus.FAILED,"real main-app conflict still times out");
        }
        expired.set(false);locked.set(true);state.set(TaskStatus.RUNNING);
        MainAppConflictGuard cancelled=new MainAppConflictGuard("target","应用",()->"",state::get,()->"",(s,m)->state.set(s),()->expired.set(true),600_000,now::get,false,locked::get);
        cancelled.poll();cancelled.close();
        try{cancelled.awaitClear();throw new AssertionError("cancel while locked must stop wait");}catch(InterruptedException expected){}
        ok(!expired.get(),"cancellation is not timeout");
        System.out.println("PASS screen lock input gate, one-hour retention, unlock recovery, conflict timeout and cancellation");
    }
}
