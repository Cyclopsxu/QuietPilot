package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import java.util.*;

public final class ScreenFingerprint {
    public final String packageName, title;
    public final SortedSet<String> stableTexts;
    public ScreenFingerprint(String packageName, String title, Collection<String> texts) {
        this.packageName=packageName; this.title=title; this.stableTexts=new TreeSet<>();
        for(String text:texts)if(!volatileText(text))this.stableTexts.add(text);
    }
    private static boolean volatileText(String text) {
        String value=text.replaceAll("\\s+","");
        return value.matches("(?:今天|昨天|前天)?[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?")
                || value.matches("[0-9]+(?:秒|分钟|小时|天)前(?:同步)?");
    }
    public static ScreenFingerprint from(UiSnapshot ui, String fallbackPackage, int height) {
        String pkg="",title="";
        for (JsonElement value : UiSnapshot.array(ui.dump,"windows")) {
            JsonObject window=value.getAsJsonObject();
            if (UiSnapshot.integer(window,"type") != 1 || !window.has("root")) continue;
            pkg=UiSnapshot.text(window.getAsJsonObject("root"),"package"); title=UiSnapshot.text(window,"title");
            if (!pkg.isEmpty()) break;
        }
        if (pkg.isEmpty()) pkg=fallbackPackage;
        SortedSet<String> texts=new TreeSet<>();
        for (JsonObject node:ui.foreground().nodes) {
            if(UiSnapshot.bool(node,"inCollection")||(node.has("visible")&&!UiSnapshot.bool(node,"visible")))continue;
            String nodePackage=UiSnapshot.text(node,"package");
            if (!nodePackage.isEmpty() && !pkg.equals(nodePackage)) continue;
            String id=UiSnapshot.text(node,"id");if(!id.isEmpty())texts.add("id:"+id);
            String text=UiSnapshot.text(node,"text").trim(); int[] b=UiSnapshot.bounds(node);
            if (!text.isEmpty() && (!UiSnapshot.text(node,"id").isEmpty() || b[1] < height*.15 || b[3] > height*.85)) texts.add(text);
        }
        // List entries and timestamps are dynamic content; controls identify the reusable page.
        while(texts.size()>48) texts.remove(texts.last());
        return new ScreenFingerprint(pkg,title,texts);
    }
    public double similarity(ScreenFingerprint other) {
        if (!packageName.equals(other.packageName)) return 0;
        Set<String> union=new HashSet<>(stableTexts); union.addAll(other.stableTexts);
        if(union.isEmpty()) return 1;
        Set<String> common=new HashSet<>(stableTexts); common.retainAll(other.stableTexts);
        return common.size()/(double)union.size();
    }
    public JsonObject json() {
        JsonObject o=new JsonObject(); o.addProperty("packageName",packageName);o.addProperty("title",title);
        JsonArray texts=new JsonArray();for(String text:stableTexts)texts.add(text);o.add("stableTexts",texts);return o;
    }
    public static ScreenFingerprint parse(JsonObject o) {
        List<String> texts=new ArrayList<>();for(JsonElement t:UiSnapshot.array(o,"stableTexts"))texts.add(t.getAsString());
        return new ScreenFingerprint(UiSnapshot.text(o,"packageName"),UiSnapshot.text(o,"title"),texts);
    }
}
