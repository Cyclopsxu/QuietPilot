import com.silentauto.shell.ext.handoff.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class StepBudgetHandoffRegression {
    static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Host implements StepBudgetHandoff.Host {
        final BlockingQueue<HandoffManager> waiting=new LinkedBlockingQueue<>();
        final AtomicInteger inspections=new AtomicInteger(),amendments=new AtomicInteger();
        final String observed;final boolean unavailable;
        volatile String evidence,message;
        Host(String observed,boolean unavailable){this.observed=observed;this.unavailable=unavailable;}
        public String inspect()throws Exception{inspections.incrementAndGet();if(unavailable)throw new Exception("OCR unavailable");return observed;}
        public void waiting(HandoffManager handoff,String proof,String detail){evidence=proof;message=detail;waiting.add(handoff);}
        public void applyAmendments(){amendments.incrementAndGet();}
        HandoffManager next()throws Exception{HandoffManager h=waiting.poll(2,TimeUnit.SECONDS);ok(h!=null,"retained handoff must be published");return h;}
    }
    public static void main(String[] args)throws Exception {
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            for(Host host:new Host[]{new Host("提交订单；大杯、冰；立即支付",false),new Host("",false),new Host("",true)}) {
                Future<String> run=worker.submit(()->StepBudgetHandoff.await(host));
                HandoffManager first=host.next();
                ok(host.inspections.get()==1,"only one final read-only inspection");
                ok(host.evidence.equals(host.observed),"no completion or payment evidence fabricated");
                try{run.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("page released without user action");}catch(TimeoutException retained){}
                first.resume("amend");HandoffManager amended=host.next();
                ok(host.amendments.get()==1&&host.inspections.get()==1,"amendment cannot refill budget or trigger more inspection/actions");
                ok(host.evidence.isEmpty(),"old goal evidence cleared after amendment");
                try{run.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("amendment must retain page");}catch(TimeoutException retained){}
                amended.resume("done");ok("done".equals(run.get(2,TimeUnit.SECONDS)),"explicit acknowledgement ends handoff");
            }
            Host cancelled=new Host("",false);Future<String> run=worker.submit(()->StepBudgetHandoff.await(cancelled));
            cancelled.next().resume("cancel");ok("cancel".equals(run.get(2,TimeUnit.SECONDS)),"explicit cancel ends retention");
            Host interrupted=new Host("",false);run=worker.submit(()->StepBudgetHandoff.await(interrupted));
            interrupted.next();run.cancel(true);
            worker.submit(()->{}).get(2,TimeUnit.SECONDS);
            ok(interrupted.waiting.isEmpty(),"stop interrupts wait without starting another handoff");
            try {
                StepBudgetHandoff.await(new StepBudgetHandoff.Host() {
                    public String inspect()throws Exception{throw new InterruptedException("cancelled during inspection");}
                    public void waiting(HandoffManager handoff,String proof,String detail){throw new AssertionError("cancelled inspection must not retain a new handoff");}
                    public void applyAmendments(){throw new AssertionError("cancelled inspection cannot amend");}
                });
                throw new AssertionError("inspection interruption must propagate");
            } catch(InterruptedException expected) {}
        } finally {worker.shutdownNow();}
        System.out.println("PASS budget final boundary, unknown/OCR failure preservation, explicit acknowledgement/cancel, amendment without more actions, interrupt cleanup");
    }
}
