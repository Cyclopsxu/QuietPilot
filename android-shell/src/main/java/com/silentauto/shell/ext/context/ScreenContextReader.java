package com.silentauto.shell.ext.context;

import android.graphics.*;
import android.os.SystemClock;
import com.google.gson.*;
import com.silentauto.shell.UiBridge;
import com.silentauto.shell.ext.guard.ForegroundWatcher;
import com.silentauto.shell.ext.skill.UiSnapshot;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Ephemeral main-display context. It never enters trajectories or skills. */
public final class ScreenContextReader {
    @FunctionalInterface public interface Ocr { List<String> read(Bitmap bitmap) throws Exception; }
    private static final long TTL = 120_000;
    private final File directory = new File("/data/local/tmp/shadowauto/context");
    private final File setting = new File("/data/local/tmp/shadowauto/no-screen");
    private final Map<String, Entry> entries = new HashMap<>();
    private final UiBridge ui;
    private final Ocr ocr;
    private final ForegroundWatcher foreground = new ForegroundWatcher();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r,"context-expiry"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
    });
    private static final class Entry {
        final JsonObject json; final long expires = SystemClock.elapsedRealtime() + TTL;
        Entry(JsonObject json) { this.json = json; }
    }
    public ScreenContextReader(UiBridge ui, Ocr ocr) {
        this.ui = ui; this.ocr = ocr;
        directory.mkdirs();
        File[] stale = directory.listFiles((dir,name) -> name.endsWith(".png"));
        if (stale != null) for (File file : stale) file.delete();
        cleaner.scheduleAtFixedRate(this::expire, 5, 5, TimeUnit.SECONDS);
    }
    public synchronized void reading(boolean enabled) throws IOException {
        setting.getParentFile().mkdirs();
        if (enabled) Files.deleteIfExists(setting.toPath());
        else { Files.write(setting.toPath(), "disabled".getBytes(StandardCharsets.UTF_8)); for (String id : new ArrayList<>(entries.keySet())) discard(id); }
    }
    public synchronized JsonObject capture(int width, int height) throws Exception {
        expire();
        String id = UUID.randomUUID().toString(); JsonObject result = empty(id);
        if (setting.exists()) { result.addProperty("screenReading",false); remember(id,result); return result; }
        String component = foreground.foregroundActivity(); String pkg = component.split("/")[0];
        if (excluded(pkg)) { result.addProperty("screenReading",true); remember(id,result); return result; }
        UiSnapshot tree = new UiSnapshot(ui.dump(0,width,height,UiBridge.MODE_SIMPLE));
        LinkedHashSet<String> texts = new LinkedHashSet<>(); List<int[]> masks = new ArrayList<>();
        boolean[] sensitive = {false};
        for (JsonElement value : UiSnapshot.array(tree.dump,"windows")) {
            JsonObject window = value.getAsJsonObject();
            if (!window.has("root") || !window.get("root").isJsonObject()) continue;
            JsonObject root = window.getAsJsonObject("root"); String owner = UiSnapshot.text(root,"package");
            if (excluded(owner) || UiSnapshot.integer(window,"type") != 1) {
                int[] bounds = UiSnapshot.bounds(window);
                if ((long)(bounds[2]-bounds[0])*(bounds[3]-bounds[1]) < (long)width*height/3) masks.add(bounds);
                continue;
            }
            if (!owner.isEmpty() && !owner.equals(pkg)) continue;
            collect(root,texts,masks,sensitive);
        }
        File path = new File(directory,id+".png"); Bitmap bitmap = null;
        try {
            // screencap without -d selects the primary/main display (physical IDs are not logical display IDs).
            Process capture = new ProcessBuilder("screencap","-p",path.getAbsolutePath()).redirectErrorStream(true).start();
            if (!capture.waitFor(5,TimeUnit.SECONDS)) { capture.destroy(); throw new IOException("Main screenshot timed out"); }
            if (capture.exitValue()!=0) throw new IOException("Main screenshot unavailable");
            bitmap = BitmapFactory.decodeFile(path.getAbsolutePath());
            if (bitmap == null) throw new IOException("Main screenshot is protected or unavailable");
            Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888,true); bitmap.recycle(); bitmap = mutable;
            Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(); paint.setColor(Color.BLACK);
            for (int[] b : masks) canvas.drawRect(b[0],b[1],b[2],b[3],paint);
            if (texts.size()<5) {
                try { for (String text : ocr.read(bitmap)) if (text!=null&&!text.trim().isEmpty()&&texts.size()<200) texts.add(text.trim()); }
                catch (Exception | LinkageError error) { result.addProperty("ocrError",error.getClass().getSimpleName()); }
            }
            int edge=Math.max(bitmap.getWidth(),bitmap.getHeight());
            if(edge>1280){Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*1280/edge),Math.max(1,bitmap.getHeight()*1280/edge),true);bitmap.recycle();bitmap=resized;}
            try (OutputStream output = new FileOutputStream(path)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,output); }
            if (!component.equals(foreground.foregroundActivity())) throw new IOException("Main app changed while capturing context");
            result.addProperty("foregroundPackage",pkg);result.addProperty("foregroundActivity",component);
            JsonArray list=new JsonArray();texts.forEach(list::add);result.add("texts",list);
            result.addProperty("screenshotPath",path.getAbsolutePath());result.addProperty("screenReading",true);
            remember(id,result); return result.deepCopy();
        } catch (Exception error) { path.delete(); throw error; }
        finally { if(bitmap!=null)bitmap.recycle(); }
    }
    private static boolean excluded(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.startsWith("com.silentauto.controller") || pkg.startsWith("com.silentauto.shell");
    }
    private static void collect(JsonObject node,Set<String> texts,List<int[]> masks,boolean[] sensitive) {
        if (UiSnapshot.bool(node,"password") || excluded(UiSnapshot.text(node,"package"))) {
            masks.add(UiSnapshot.bounds(node)); sensitive[0]=true; return;
        }
        if (!node.has("visible") || UiSnapshot.bool(node,"visible")) {
            String text = UiSnapshot.text(node,"text").trim();
            if (!text.isEmpty() && texts.size()<200) texts.add(text);
            String desc=UiSnapshot.text(node,"desc").trim(); if(!desc.isEmpty()&&texts.size()<200)texts.add(desc);
        }
        for(JsonElement child:UiSnapshot.array(node,"children"))if(child.isJsonObject())collect(child.getAsJsonObject(),texts,masks,sensitive);
    }
    private static JsonObject empty(String id) {
        JsonObject result=new JsonObject();result.addProperty("contextId",id);result.addProperty("foregroundPackage","");
        result.addProperty("foregroundActivity","");result.add("texts",new JsonArray());result.addProperty("screenshotPath","");return result;
    }
    public synchronized JsonObject take(String id) {
        expire(); Entry entry=entries.remove(id);return entry==null?empty(""):entry.json.deepCopy();
    }
    public synchronized void discard(String id) {
        Entry entry=entries.remove(id);if(entry!=null){String path=UiSnapshot.text(entry.json,"screenshotPath");if(!path.isEmpty())new File(path).delete();}
    }
    public void deleteConsumed(JsonObject context) { String path=UiSnapshot.text(context,"screenshotPath"); if(!path.isEmpty())new File(path).delete(); }
    private void remember(String id,JsonObject json) {
        entries.put(id,new Entry(json)); cleaner.schedule(()->discard(id),TTL,TimeUnit.MILLISECONDS);
    }
    private synchronized void expire(){for(String id:new ArrayList<>(entries.keySet()))if(entries.get(id).expires<=SystemClock.elapsedRealtime())discard(id);}
}
