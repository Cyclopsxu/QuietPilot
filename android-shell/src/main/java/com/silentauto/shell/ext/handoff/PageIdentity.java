package com.silentauto.shell.ext.handoff;

import com.silentauto.shell.ext.skill.ScreenFingerprint;
import com.silentauto.shell.ext.skill.UiSnapshot;
import com.google.gson.JsonObject;
import java.text.Normalizer;
import java.util.*;

/** Exact, source-validated result anchors; shared navigation labels are not identity. */
public final class PageIdentity {
    private PageIdentity() {}
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\s\\p{P}\\p{Cf}]", "");
    }
    public static ScreenFingerprint from(UiSnapshot ui, String pkg) {
        SortedSet<String> texts = new TreeSet<>(); String actual = "";
        for (JsonObject node : ui.foreground().nodes) {
            if (node.has("visible") && !UiSnapshot.bool(node,"visible")) continue;
            String nodePkg = UiSnapshot.text(node,"package");
            if (!nodePkg.isEmpty() && actual.isEmpty()) actual = nodePkg;
            if (!pkg.equals(nodePkg)) continue;
            for (String key : new String[]{"text","desc"}) {
                String value=UiSnapshot.text(node,key).trim(); if(!value.isEmpty())texts.add(value);
            }
        }
        return new ScreenFingerprint(actual,"",texts);
    }
    public static boolean contains(ScreenFingerprint page, List<String> anchors) {
        if (anchors.isEmpty()) return false;
        Set<String> generic = new HashSet<>(Arrays.asList("返回","搜索","收藏","评价","优惠","订座","买单","分享","餐厅详情","约会榜"));
        for (String anchor : anchors) {
            String key=normalize(anchor);
            if(key.length()<3 || generic.contains(key) || key.matches(".*榜.*上榜餐厅")
                    || key.matches("[\\d.]+") || key.matches("共?\\d+字")
                    || key.matches("(?:今天|昨天)?\\d{1,2}\\d{2}"))return false;
            boolean found=false;
            for(String value:page.stableTexts)if(normalize(value).contains(key)){found=true;break;}
            if(!found)return false;
        }
        return true;
    }

    /** Resolve anchors before announcing completion, against the same source UI. */
    public static List<String> validatedAnchors(UiSnapshot ui,String pkg,String goal,List<String> proposed) {
        ScreenFingerprint page=from(ui,pkg);
        if(!pkg.equals(page.packageName))return List.of();
        if(contains(page,proposed))return List.copyOf(proposed);
        // A saved document can expose a stable title even when the model emits no
        // anchors or summarizes its long body with ellipses. Require the exact
        // source title to be named in the goal; generic app chrome cannot qualify.
        String expected=normalize(goal);
        for(JsonObject node:ui.foreground().nodes) {
            if(!pkg.equals(UiSnapshot.text(node,"package"))
                    || (node.has("visible")&&!UiSnapshot.bool(node,"visible"))
                    || UiSnapshot.bool(node,"password") || !UiSnapshot.text(node,"id").endsWith("/title"))continue;
            String title=UiSnapshot.text(node,"text").trim(),key=normalize(title);
            if(key.length()<6||title.length()>160||!expected.contains(key)||!contains(page,List.of(title)))continue;
            List<String> anchors=new ArrayList<>();anchors.add(title);
            for(JsonObject body:ui.foreground().nodes) {
                if(!pkg.equals(UiSnapshot.text(body,"package"))||UiSnapshot.bool(body,"password")
                        ||(body.has("visible")&&!UiSnapshot.bool(body,"visible")))continue;
                String text=UiSnapshot.text(body,"text").trim();
                if(text.length()<80)continue;
                String excerpt=text.substring(0,Math.min(120,text.length()));
                if(expected.contains(normalize(excerpt))&&contains(page,List.of(excerpt))){anchors.add(excerpt);break;}
            }
            return List.copyOf(anchors);
        }
        return List.of();
    }
}
