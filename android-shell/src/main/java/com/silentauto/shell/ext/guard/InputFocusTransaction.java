package com.silentauto.shell.ext.guard;

import java.util.concurrent.Callable;

/** A compatibility input operation owns global focus briefly and must restore it. */
public final class InputFocusTransaction {
    public interface Host {
        int mainTask() throws Exception;
        int focusedDisplay() throws Exception;
        void begin() throws Exception;
        void enter() throws Exception;
        void check() throws Exception;
        void restore(int task) throws Exception;
        void end(boolean restored);
    }
    private static final Object LOCK=new Object();
    private InputFocusTransaction(){}
    public static <T> T run(Host host,int display,Callable<T> input)throws Exception {
        synchronized(LOCK){
            int main=host.mainTask();
            if(display<=0||main<0||host.focusedDisplay()!=0)throw new IllegalStateException("Cannot reserve main-display input focus");
            host.begin();boolean restored=false;
            try {
                host.enter();
                if(host.focusedDisplay()!=display||host.mainTask()!=main)throw new IllegalStateException("Input focus target changed");
                host.check();T result=input.call();host.check();return result;
            } finally {
                boolean interrupted=Thread.interrupted();
                try {
                    // Restore the user's CURRENT main task, never reopen an old app.
                    int current=host.mainTask();
                    if(current<0)throw new IllegalStateException("Main task disappeared during input");
                    host.restore(current);restored=host.focusedDisplay()==0;
                    if(!restored)throw new IllegalStateException("Main input focus was not restored");
                } finally {host.end(restored);if(interrupted)Thread.currentThread().interrupt();}
            }
        }
    }
}
