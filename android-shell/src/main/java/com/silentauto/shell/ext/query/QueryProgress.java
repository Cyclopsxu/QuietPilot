package com.silentauto.shell.ext.query;

import com.google.gson.JsonObject;
import com.silentauto.shell.ext.skill.UiSnapshot;
import java.util.*;

/** Detect repeated scroll viewports without storing screen content outside the task. */
public final class QueryProgress {
    private final Map<String,Integer> visits=new LinkedHashMap<>();
    private final Set<String> checked=new HashSet<>();
    public void reset(){visits.clear();checked.clear();}
    public boolean observe(String dump,String previousAction) {
        SortedSet<String> text=new TreeSet<>();
        for(JsonObject node:new UiSnapshot(dump).foreground().nodes) {
            if(UiSnapshot.bool(node,"password")||(node.has("visible")&&!UiSnapshot.bool(node,"visible")))continue;
            String value=UiSnapshot.text(node,"text")+"\n"+UiSnapshot.text(node,"desc");
            if(!value.trim().isEmpty())text.add(value.trim());
        }
        if(text.isEmpty())return false;
        String key=UUID.nameUUIDFromBytes(String.join("\n",text).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();int count=visits.getOrDefault(key,0)+1;visits.put(key,count);
        if(visits.size()>12)visits.remove(visits.keySet().iterator().next());
        return "scroll".equals(previousAction)&&count>=2&&checked.size()<3&&checked.add(key);
    }
}
