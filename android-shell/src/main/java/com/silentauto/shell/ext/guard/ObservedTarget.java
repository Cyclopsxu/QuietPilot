package com.silentauto.shell.ext.guard;

import com.google.gson.*;
import com.silentauto.shell.ext.skill.UiSnapshot;

/** A target index is local to one observation; never reuse it against a new tree. */
public final class ObservedTarget {
    private ObservedTarget() {}
    public static JsonObject resolve(String plannedDump,String currentDump,int index) {
        JsonObject planned=JsonParser.parseString(plannedDump).getAsJsonObject();
        JsonObject current=JsonParser.parseString(currentDump).getAsJsonObject();
        String pkg=foregroundPackage(planned);
        if(pkg.isEmpty()||!pkg.equals(foregroundPackage(current)))return null;
        JsonObject expected=null;
        for(JsonElement e:UiSnapshot.array(planned,"targets")) {
            JsonObject node=e.getAsJsonObject();
            if(UiSnapshot.integer(node,"targetIndex")==index){expected=node;break;}
        }
        if(expected==null||UiSnapshot.text(expected,"path").isEmpty())return null;
        for(JsonElement e:UiSnapshot.array(current,"targets")) {
            JsonObject node=e.getAsJsonObject();boolean same=true;
            for(String key:new String[]{"path","label","bounds","class","id"})
                if(!UiSnapshot.text(expected,key).equals(UiSnapshot.text(node,key))){same=false;break;}
            if(same)return node;
        }
        return null;
    }
    private static String foregroundPackage(JsonObject dump) {
        for(JsonElement e:UiSnapshot.array(dump,"windows")) {
            JsonObject window=e.getAsJsonObject();
            if(UiSnapshot.integer(window,"type")==1&&window.has("root")&&window.get("root").isJsonObject())
                return UiSnapshot.text(window.getAsJsonObject("root"),"package");
        }
        return "";
    }
}
