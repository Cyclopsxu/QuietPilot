package com.silentauto.shell.ext.history;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.silentauto.shell.ext.skill.UiSnapshot;

public final class TaskArchive {
    private final File root=new File("/data/local/tmp/shadowauto/history");
    private final File trajectories=new File("/data/local/tmp/shadowauto/trajectories");
    private static String safe(String id){if(!id.matches("[A-Za-z0-9_.-]{1,100}")||id.equals(".")||id.equals(".."))throw new IllegalArgumentException("Invalid task id");return id;}
    public synchronized void save(JsonObject metrics)throws IOException{
        String id=safe(UiSnapshot.text(metrics,"taskId"));JsonObject item=metrics.deepCopy();JsonObject trace=read(new File(trajectories,id+".json"));
        item.add("steps",trace==null?new JsonArray():UiSnapshot.array(trace,"steps"));root.mkdirs();
        Path temp=Files.createTempFile(root.toPath(),"task-",".tmp");
        try{Files.write(temp,item.toString().getBytes(StandardCharsets.UTF_8));Files.move(temp,new File(root,id+".json").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    public synchronized JsonObject get(String id)throws IOException{
        safe(id);JsonObject item=read(new File(root,id+".json"));if(item!=null)return item;
        File legacy=new File(trajectories,id+".json");JsonObject trace=read(legacy);if(trace==null)return null;
        item=new JsonObject();item.addProperty("taskId",id);item.addProperty("goal",UiSnapshot.text(trace,"goal"));item.addProperty("app",UiSnapshot.text(trace,"packageName"));
        JsonObject outcome=trace.has("outcome")?trace.getAsJsonObject("outcome"):new JsonObject();
        for(String field:new String[]{"status","message","evidence"})item.addProperty(field,UiSnapshot.text(outcome,field));
        item.addProperty("finishedAtMs",legacy.lastModified());item.addProperty("statsUnavailable",true);item.add("steps",UiSnapshot.array(trace,"steps"));return item;
    }
    public synchronized JsonArray steps(String id)throws IOException {
        JsonObject trace=read(new File(trajectories,safe(id)+".json"));return trace==null?new JsonArray():UiSnapshot.array(trace,"steps");
    }
    public synchronized JsonArray list()throws IOException{
        Set<String> ids=new HashSet<>();for(File dir:new File[]{root,trajectories}){File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files)ids.add(f.getName().substring(0,f.getName().length()-5));}
        List<JsonObject> items=new ArrayList<>();for(String id:ids)try{JsonObject item=get(id);if(item!=null&&!UiSnapshot.text(item,"status").isEmpty())items.add(item);}catch(Exception ignored){}
        items.sort(Comparator.comparingLong((JsonObject o)->o.has("finishedAtMs")?o.get("finishedAtMs").getAsLong():0).reversed());
        JsonArray result=new JsonArray();for(int i=0;i<Math.min(50,items.size());i++){JsonObject summary=items.get(i).deepCopy();summary.remove("steps");result.add(summary);}return result;
    }
    public JsonObject withEvidence(String id,JsonObject item)throws IOException{
        File screenshot=new File("/data/local/tmp/shadowauto/evidence",safe(id)+".png");
        if("VERIFIED".equals(UiSnapshot.text(item,"status")) && screenshot.isFile()) {
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(screenshot.getAbsolutePath());
            if(bitmap!=null)try{
                int edge=Math.max(bitmap.getWidth(),bitmap.getHeight());
                if(edge>1280){android.graphics.Bitmap resized=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*1280/edge),Math.max(1,bitmap.getHeight()*1280/edge),true);bitmap.recycle();bitmap=resized;}
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,bytes);
                item.addProperty("evidenceBase64",Base64.getEncoder().encodeToString(bytes.toByteArray()));
            }finally{bitmap.recycle();}
        }
        return item;
    }
    private static JsonObject read(File file)throws IOException{if(!file.isFile())return null;try(Reader input=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){return JsonParser.parseReader(input).getAsJsonObject();}}
}
