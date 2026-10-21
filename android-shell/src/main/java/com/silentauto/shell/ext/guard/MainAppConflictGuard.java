package com.silentauto.shell.ext.guard;

import com.silentauto.shell.ext.TaskStatus;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** One-second watch plus a gate immediately before action dispatch. */
public final class MainAppConflictGuard implements AutoCloseable {
    @FunctionalInterface public interface Foreground { String get() throws Exception; }
    public static final class PausedInputException extends RuntimeException { public PausedInputException(){super("Input blocked by main-app conflict");} }
    private final String pkg,name;
    private final Foreground foreground;private final Supplier<TaskStatus> status;private final Supplier<String> step;
    private final BiConsumer<TaskStatus,String> transition;private final Runnable timeout;
    private final LongSupplier clock;private final long timeoutMs;
    private final ScheduledExecutorService watcher;
    private final AtomicBoolean recovery=new AtomicBoolean();
    private volatile boolean conflicted,expired,closed;private volatile long epoch;private long pausedAt;private String pausedStep="";
    public MainAppConflictGuard(String pkg,String name,Foreground foreground,Supplier<TaskStatus> status,Supplier<String> step,BiConsumer<TaskStatus,String> transition,Runnable timeout){
        this(pkg,name,foreground,status,step,transition,timeout,600_000,()->System.nanoTime()/1_000_000,true);
    }
    public MainAppConflictGuard(String pkg,String name,Foreground foreground,Supplier<TaskStatus> status,Supplier<String> step,BiConsumer<TaskStatus,String> transition,Runnable timeout,long timeoutMs,LongSupplier clock,boolean watch){
        this.pkg=pkg;this.name=name;this.foreground=foreground;this.status=status;this.step=step;this.transition=transition;this.timeout=timeout;this.timeoutMs=timeoutMs;this.clock=clock;
        watcher=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"main-app-conflict");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
        if(watch)watcher.scheduleAtFixedRate(this::poll,0,1,TimeUnit.SECONDS);
    }
    public synchronized void poll(){
        TaskStatus state=status.get();if(closed||state.terminal())return;
        if(state!=TaskStatus.RUNNING&&state!=TaskStatus.PAUSED){conflicted=false;return;}
        boolean conflict;String detail;
        try{conflict=pkg.equals(foreground.get());detail="你打开了 "+name+"，我先暂停";}
        catch(Exception unavailable){conflict=true;detail="无法确认主屏前台，暂时暂停";}
        if(conflict&&!conflicted){conflicted=true;epoch++;recovery.set(true);pausedAt=clock.getAsLong();pausedStep=step.get();transition.accept(TaskStatus.PAUSED,detail);}
        if(conflicted&&clock.getAsLong()-pausedAt>=timeoutMs){expired=true;transition.accept(TaskStatus.FAILED,"被中断，停在 "+pausedStep);timeout.run();return;}
        if(!conflict&&conflicted){conflicted=false;transition.accept(TaskStatus.RUNNING,"你已离开 "+name+"，正在重新观察");}
    }
    public boolean awaitClear()throws InterruptedException{
        poll();while(conflicted&&!expired&&!closed){if(Thread.currentThread().isInterrupted())throw new InterruptedException("Task interrupted while paused");Thread.sleep(100);}
        if(expired)throw new IllegalStateException("被中断，停在 "+pausedStep);
        if(closed||Thread.currentThread().isInterrupted())throw new InterruptedException("Task stopped");
        return recovery.getAndSet(false);
    }
    public long epoch(){return epoch;}
    public boolean inputAllowed(){return !conflicted&&!expired&&!closed;}
    @Override public void close(){closed=true;watcher.shutdownNow();}
}
