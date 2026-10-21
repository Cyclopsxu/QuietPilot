package com.silentauto.shell.ext.handoff;

import com.silentauto.shell.ext.skill.ScreenFingerprint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Reparents the existing root task; never launches a replacement activity. */
public final class TaskDisplayTransfer {
    public interface Host {
        String command(String... args) throws Exception;
        ScreenFingerprint page(int displayId) throws Exception;
        String foreground() throws Exception;
        default boolean cancelled() { return false; }
        default void pause() throws InterruptedException { Thread.sleep(150); }
    }
    public static final class Root {
        public int id, display, task;
        public String component="";
        public boolean visible;
    }
    public static final class Result {
        public final boolean moved, previewAvailable, restored;
        public final int rootId, taskId;
        public final String outcome, message;
        Result(String outcome, Root root, String message, boolean preview, boolean restored) {
            this.outcome=outcome; moved="moved".equals(outcome); previewAvailable=preview; this.restored=restored;
            rootId=root==null?-1:root.id; taskId=root==null?-1:root.task; this.message=message;
        }
    }
    private final Host host;
    public TaskDisplayTransfer(Host host){this.host=host;}
    public Result move(int source,String pkg,List<String> anchors) {
        Root original=null; ScreenFingerprint expected=null;
        try {
            if(source<=0)throw new IllegalStateException("Invalid source display");
            for(Root r:parse(host.command("am","stack","list")))if(r.display==source&&r.visible&&r.component.startsWith(pkg+"/")){
                if(original!=null)throw new IllegalStateException("Ambiguous source tasks");original=r;
            }
            if(original==null)throw new IllegalStateException("Source task missing");
            expected=host.page(source);
            if(!pkg.equals(expected.packageName)||!PageIdentity.contains(expected,anchors))
                return new Result("preview",original,"关键页面证据不足，已保留预览",true,false);
            checkCancelled();
            host.command("am","display","move-stack",String.valueOf(original.id),"0");
            long deadline=System.nanoTime()+12_000_000_000L;
            for(int i=0;i<16&&System.nanoTime()<deadline;i++){
                checkCancelled();
                Root current=find(parse(host.command("am","stack","list")),original.id);
                if(sameTask(current,original)&&current.display==0&&current.visible&&pkg.equals(host.foreground())) {
                    ScreenFingerprint page=host.page(0);
                    if(pkg.equals(page.packageName)&&PageIdentity.contains(page,anchors)) {
                        checkCancelled();
                        return new Result("moved",original,"当前页面已切到主屏",false,false);
                    }
                }
                host.pause();
            }
            throw new IllegalStateException("Task or result identity not confirmed");
        } catch(Exception failure) {
            if(original!=null)try {
                Root current=find(parse(host.command("am","stack","list")),original.id);
                if(!sameTask(current,original))throw new IllegalStateException("Original task disappeared");
                if(current.display!=source)host.command("am","display","move-stack",String.valueOf(original.id),String.valueOf(source));
                for(int i=0;i<8;i++) {
                    current=find(parse(host.command("am","stack","list")),original.id);
                    if(sameTask(current,original)&&current.display==source&&current.visible) {
                        ScreenFingerprint page=host.page(source);
                        boolean restored=pkg.equals(page.packageName)&&PageIdentity.contains(page,anchors);
                        return new Result("preview",original,restored?"切换未完成，已恢复原页面预览":"原页面内容未能确认，请核对保留的预览",true,restored);
                    }
                    host.pause();
                }
            }catch(Exception rollback){ /* Preserve resources without claiming the page survived. */ }
            return new Result("unavailable",original,"原页面无法恢复，后台资源暂时保留；请重试或结束任务",false,false);
        }
    }
    private void checkCancelled() throws InterruptedException { if(host.cancelled())throw new InterruptedException("Transfer cancelled"); }
    private static boolean sameTask(Root now,Root original) {
        return now!=null&&now.task==original.task&&now.component.equals(original.component);
    }
    private static Root find(List<Root> roots,int id){for(Root r:roots)if(r.id==id)return r;return null;}
    public static List<Root> parse(String text){
        Pattern header=Pattern.compile("^(?:RootTask|Stack) id=(\\d+).*?displayId=(\\d+).*");
        Pattern child=Pattern.compile("^\\s+taskId=(\\d+):.*");
        Pattern top=Pattern.compile("topActivity=ComponentInfo\\{([^}]+)\\}");
        List<Root> roots=new ArrayList<>();Root current=null;
        for(String line:text.split("\\r?\\n")){
            Matcher h=header.matcher(line);
            if(h.matches()){current=new Root();current.id=Integer.parseInt(h.group(1));current.display=Integer.parseInt(h.group(2));roots.add(current);continue;}
            Matcher c=child.matcher(line);if(current==null||!c.matches())continue;
            Matcher component=top.matcher(line);
            if(component.find()&&(current.component.isEmpty()||line.contains("visible=true"))){current.task=Integer.parseInt(c.group(1));current.component=component.group(1);current.visible=line.contains("visible=true");}
        }
        return roots;
    }
    public static String command(String...args)throws Exception{
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))>=0){if(bytes.size()+count>262144)break;bytes.write(buffer,0,count);}}catch(IOException ignored){}},"task-transfer-output");reader.setDaemon(true);reader.start();
        try{
            if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Task transfer command timed out");}
            reader.join(500);if(reader.isAlive()||p.exitValue()!=0)throw new IOException("Task transfer command rejected");
            return bytes.toString(StandardCharsets.UTF_8.name());
        }finally{if(p.isAlive())p.destroyForcibly();}
    }
}
