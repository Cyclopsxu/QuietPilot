package com.silentauto.shell.ext.handoff;

/** Temporary human control of the existing Android task. Never launches a replacement page. */
public final class ManualTaskTransfer {
    private final TaskDisplayTransfer.Host host;
    private final String pkg;
    private final int source;
    private TaskDisplayTransfer.Root identity;
    public ManualTaskTransfer(TaskDisplayTransfer.Host host, String pkg, int source) {
        this.host=host;this.pkg=pkg;this.source=source;
        if(source<=0)throw new IllegalArgumentException("Invalid background display");
    }
    public void toMain() throws Exception {
        if(identity!=null){move(current().display,0);return;}
        for(TaskDisplayTransfer.Root root:TaskDisplayTransfer.parse(host.command("am","stack","list"))) {
            if(root.display==source&&root.visible&&root.component.startsWith(pkg+"/")) {
                if(identity!=null)throw new IllegalStateException("多个页面，无法确认交接对象");
                identity=root;
            }
        }
        if(identity==null)throw new IllegalStateException("原页面不可用，已保留后台");
        move(source,0);
    }
    public void toBackground() throws Exception { move(0,source); }
    /** Unknown position must never be presented as a restored preview. */
    public int location() {try{return current().display;}catch(Exception unknown){return -1;}}
    private TaskDisplayTransfer.Root current() throws Exception {
        if(identity==null)throw new IllegalStateException("原任务身份尚未确认");
        for(TaskDisplayTransfer.Root root:TaskDisplayTransfer.parse(host.command("am","stack","list")))
            if(root.id==identity.id&&root.task==identity.task&&root.component.startsWith(pkg+"/"))return root;
        throw new IllegalStateException("原任务已变化，请保留当前页面并结束任务");
    }
    private void move(int from,int to) throws Exception {
        if(identity==null)throw new IllegalStateException("原页面身份未知");
        TaskDisplayTransfer.Root before=current();
        if(before.display==to&&before.visible&&(to!=0||pkg.equals(host.foreground())))return;
        if(before.display!=from||(from!=0&&from!=source))throw new IllegalStateException("原页面所在屏幕已变化，请刷新后重试");
        try {
            if(host.cancelled())throw new InterruptedException("Task stopped");
            host.command("am","display","move-stack",String.valueOf(identity.id),String.valueOf(to));
            for(int i=0;i<16;i++) {
                if(host.cancelled())throw new InterruptedException("Task stopped");
                TaskDisplayTransfer.Root root=current();
                if(root.display==to&&root.visible&&(to!=0||pkg.equals(host.foreground())))return;
                host.pause();
            }
            throw new IllegalStateException("未确认页面转移成功");
        } catch(Exception failed) {
            try {
                if(current().display!=from)host.command("am","display","move-stack",String.valueOf(identity.id),String.valueOf(from));
            } catch(Exception rollback) { failed.addSuppressed(rollback); }
            throw failed;
        }
    }
}
