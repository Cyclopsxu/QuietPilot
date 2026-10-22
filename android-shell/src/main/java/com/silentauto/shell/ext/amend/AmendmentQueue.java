package com.silentauto.shell.ext.amend;
import com.google.gson.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Append-only journal; actions consume changes only at execution boundaries. */
public final class AmendmentQueue {
    private final String original;private final JsonArray records=new JsonArray();private int applied;
    public AmendmentQueue(String original){this.original=original;}
    public synchronized JsonObject add(String kind,String text) {
        if(!Arrays.asList("modify","add","cancel").contains(kind))throw new IllegalArgumentException("kind must be modify, add or cancel");
        if(text==null||text.trim().isEmpty()||text.length()>4000)throw new IllegalArgumentException("Amendment text required (max 4000 characters)");
        JsonObject r=new JsonObject();r.addProperty("seq",records.size()+1);r.addProperty("time",System.currentTimeMillis());r.addProperty("kind",kind);r.addProperty("text",text.trim());records.add(r);return r.deepCopy();
    }
    public synchronized long version(){return records.size();}
    public synchronized boolean pending(){return applied<records.size();}
    public synchronized JsonArray drain(){JsonArray result=new JsonArray();while(applied<records.size())result.add(records.get(applied++).deepCopy());return result;}
    public synchronized JsonArray records(){return records.deepCopy();}
    public synchronized String goal() {
        StringBuilder result=new StringBuilder(original);
        SimpleDateFormat time=new SimpleDateFormat("HH:mm:ss",Locale.ROOT);
        for(JsonElement value:records){JsonObject r=value.getAsJsonObject();result.append("\n用户追加要求（第 ").append(r.get("seq").getAsInt()).append(" 次，").append(time.format(new Date(r.get("time").getAsLong()))).append("，").append(r.get("kind").getAsString()).append("）：").append(r.get("text").getAsString());}
        if(!records.isEmpty())result.append("\n规则：与已完成步骤冲突时以追加要求为准，需要时回退修改。已完成且不可撤回的操作（消息已发出、订单已提交）不得自行撤回、重做或补救；若修改与之冲突，调用 request_user(kind=confirm) 说明情况并询问用户。add 是新补充，不得重复之前已完成的操作。");
        return result.toString();
    }
}
