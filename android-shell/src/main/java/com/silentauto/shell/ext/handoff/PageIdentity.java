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
            if(key.length()<3 || generic.contains(key) || key.matches(".*榜.*上榜餐厅"))return false;
            boolean found=false;
            for(String value:page.stableTexts)if(normalize(value).contains(key)){found=true;break;}
            if(!found)return false;
        }
        return true;
    }
}
