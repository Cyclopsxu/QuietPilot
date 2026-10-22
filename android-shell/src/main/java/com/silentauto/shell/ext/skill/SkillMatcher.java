package com.silentauto.shell.ext.skill;

import com.google.gson.*;

public final class SkillMatcher {
    public static final class Match {
        public final JsonObject skill, args;
        Match(JsonObject skill, JsonObject args) { this.skill = skill; this.args = args; }
    }
    public Match match(String goal, JsonArray active, SkillCompiler.Model model) throws Exception {
        return match(goal,"action",active,model);
    }
    public Match match(String goal,String type,JsonArray all,SkillCompiler.Model model)throws Exception {
        JsonArray active=new JsonArray();
        for(JsonElement item:all){String kind=UiSnapshot.text(item.getAsJsonObject(),"type");if(type.equals(kind.isEmpty()?"action":kind))active.add(item);}
        if (active.isEmpty()) return null;
        JsonArray catalog = new JsonArray();
        for (JsonElement item : active) {
            JsonObject source = item.getAsJsonObject(), metadata = new JsonObject();
            for (String field : new String[]{"type", "skillId", "name", "description", "params"}) metadata.add(field, source.get(field));
            catalog.add(metadata);
        }
        String raw = model.complete("SKILL_MATCH 从 active 技能中匹配任务。仅明确覆盖任务时匹配，参数必须来自本次任务，禁止沿用示例。"
                + "只输出 {\"skillId\":字符串或null,\"args\":{参数名:字符串}}。任务：" + goal + "\n技能：" + catalog);
        int begin = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (begin < 0 || end <= begin) return null;
        JsonObject result = JsonParser.parseString(raw.substring(begin, end + 1)).getAsJsonObject();
        String id = UiSnapshot.text(result, "skillId");
        if (id.isEmpty() || !result.has("args") || !result.get("args").isJsonObject()) return null;
        JsonObject args = result.getAsJsonObject("args"), found = null;
        for (JsonElement item : active) {
            JsonObject skill = item.getAsJsonObject();
            if (!id.equals(UiSnapshot.text(skill, "skillId"))) continue;
            if (found != null) return null; // Ambiguous IDs across apps cannot choose a package.
            found = skill;
        }
        if (found == null) return null;
        for (JsonElement value : UiSnapshot.array(found, "params")) {
            String key = UiSnapshot.text(value.getAsJsonObject(), "name");
            if (!args.has(key) || !args.get(key).isJsonPrimitive() || !args.getAsJsonPrimitive(key).isString()) return null;
        }
        return new Match(found.deepCopy(), args.deepCopy());
    }
}
