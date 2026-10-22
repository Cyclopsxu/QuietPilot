package com.silentauto.shell.ext.voice;
import com.google.gson.*;
import com.silentauto.shell.ext.skill.UiSnapshot;

/** Never lets an ambiguous model guess attach an amendment to an arbitrary task. */
public final class VoiceRouting {
    private VoiceRouting(){}
    public static String route(JsonObject intake,JsonArray active) {
        JsonObject amendment=intake.has("amend")&&intake.get("amend").isJsonObject()?intake.getAsJsonObject("amend"):new JsonObject();
        String target=UiSnapshot.text(amendment,"target");
        if(active.isEmpty())return "new";
        boolean targetActive=false;
        for(JsonElement item:active)if(target.equals(UiSnapshot.text(item.getAsJsonObject(),"taskId")))targetActive=true;
        if(!target.isEmpty()&&!"ask".equals(target)&&!targetActive)return "new";
        if(active.size()==1&&"high".equals(UiSnapshot.text(intake,"confidence")))return UiSnapshot.text(active.get(0).getAsJsonObject(),"taskId");
        return "ask";
    }
    public static JsonObject choice(String id,JsonObject intake,JsonArray candidates,long deadline) {
        JsonObject result=new JsonObject();result.addProperty("taskId",id);result.addProperty("voiceId",id);result.addProperty("type","amend");result.addProperty("event","status");result.addProperty("status","QUEUED");
        result.addProperty("routingRequired",true);result.addProperty("confirmDeadlineMs",deadline);result.addProperty("goal",UiSnapshot.text(intake,"transcript"));result.addProperty("message","追加到哪个任务，还是作为新任务？");result.add("candidates",candidates.deepCopy());return result;
    }
}
