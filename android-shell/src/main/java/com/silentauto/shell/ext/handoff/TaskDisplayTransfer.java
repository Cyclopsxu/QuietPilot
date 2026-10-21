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
        default void pause() throws InterruptedException { Thread.sleep(150); }
    }
    public static final class Root {
        public int id, display, task;
        public String component="";
        public boolean visible;
    }
    public static final class Result {
        public final boolean moved;
        public final int rootId, taskId;
        public final String message;
        Result(boolean moved,Root root,String message){this.moved=moved;rootId=root==null?-1:root.id;taskId=root==null?-1:root.task;this.message=message;}
    }
    private final Host host;
    public TaskDisplayTransfer(Host host){this.host=host;}
    public Result move(int source,String pkg) {
        Root original=null;
        try {
            if(source<=0)throw new IllegalStateException("Invalid source display");
            for(Root r:parse(host.command("am","stack","list")))if(r.display==source&&r.visible&&r.component.startsWith(pkg+"/")){
                if(original!=null)throw new IllegalStateException("Ambiguous source tasks");original=r;
            }
            if(original==null)throw new IllegalStateException("Current app task not found on virtual display");
            ScreenFingerprint expected=host.page(source);
            if(!pkg.equals(expected.packageName)||expected.stableTexts.isEmpty())throw new IllegalStateException("Current page has no comparable text");
            host.command("am","display","move-stack",String.valueOf(original.id),"0");
            for(int i=0;i<16;i++){
                Root current=find(parse(host.command("am","stack","list")),original.id);
                if(current!=null&&current.display==0&&current.task==original.task&&current.component.equals(original.component)
                        && current.visible&&pkg.equals(host.foreground())&&expected.similarity(host.page(0))>=.6)
                    return new Result(true,original,"当前页面已切到主屏");
                host.pause();
            }
            throw new IllegalStateException("Task or page verification failed after move");
        } catch(Exception failure) {
            if(original!=null)try {
                Root current=find(parse(host.command("am","stack","list")),original.id);
                if(current!=null&&current.display!=source){
                    host.command("am","display","move-stack",String.valueOf(original.id),String.valueOf(source));
                    current=find(parse(host.command("am","stack","list")),original.id);
                    if(current==null||current.display!=source)throw new IllegalStateException("Rollback not confirmed");
                }
            }catch(Exception rollback){return new Result(false,original,"切换未确认，后台资源已保留，请在预览中核对");}
            return new Result(false,original,"无法切到主屏，已保留原预览："+failure.getClass().getSimpleName());
        }
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
