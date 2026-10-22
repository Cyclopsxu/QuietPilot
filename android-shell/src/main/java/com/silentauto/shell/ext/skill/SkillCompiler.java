package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import java.util.*;

/** One executor request followed by deterministic consistency checks. */
public final class SkillCompiler {
    @FunctionalInterface public interface Model { String complete(String prompt) throws Exception; }
    private final SkillStore store;
    public SkillCompiler(SkillStore store) { this.store = store; }

    public JsonObject compile(JsonObject trajectory, Model model, long elapsed, int callsBefore) throws Exception {
        if (trajectory == null || !trajectory.has("outcome")
                || !java.util.Arrays.asList("VERIFIED","ANSWERED").contains(UiSnapshot.text(trajectory.getAsJsonObject("outcome"), "status"))
                || UiSnapshot.bool(trajectory, "handedOff") || !UiSnapshot.array(trajectory,"amendments").isEmpty()) return null;
        JsonArray steps = preprocess(UiSnapshot.array(trajectory, "steps"));
        if (steps.isEmpty()) return null;
        JsonObject input = new JsonObject(); input.addProperty("goal", UiSnapshot.text(trajectory, "goal"));
        input.addProperty("type",UiSnapshot.text(trajectory,"type").isEmpty()?"action":UiSnapshot.text(trajectory,"type"));
        input.addProperty("packageName", UiSnapshot.text(trajectory, "packageName")); input.add("steps", steps);
        if(!"query".equals(UiSnapshot.text(input,"type")))input.add("outcome", trajectory.get("outcome"));
        String raw = model.complete("""
                SKILL_COMPILE 技能编译。把已验收轨迹编译为可复用技能，只输出 JSON。
                字段：type(与输入相同的action/query)、skillId(英文点分标识)、name、description、params[{name,desc,example}]、
                steps[{expect:原始 fingerprint 对象,action:动作名,target:原始 target 对象或null,text:字符串或null,其余必要动作参数}]、evidence。
                联系人、消息、搜索词等任务变量用 {param} 参数化，同时参数化 expect.stableTexts/target/evidence 内相应文字。
                严格保持步骤顺序、动作与输入次数；每步 text 代入 example 必须与轨迹 action.text 原文完全一致。
                非文字动作 text=null；可用动作 tap_target/focus_input/input_text/long_press/scroll/back/key/submit_search/clear_text。
                坐标点击必须改为原 target 特征定位，无法定位则放弃返回 {}。不允许保存坐标、index 作为定位器。
                查询 type=query 时用 question 替代 evidence，只保存导航路线和参数化问题，不保存答案、quote 或 outcome 中的内容。
                action 的方向、按键、时长等参数从轨迹 action 保留。不要创建轨迹中没有的操作或交接。
                输入：
                """ + input);
        int begin = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (begin < 0 || end <= begin) throw new IllegalArgumentException("Compiler returned no JSON");
        JsonObject compiled = JsonParser.parseString(raw.substring(begin, end + 1)).getAsJsonObject();
        String type=UiSnapshot.text(input,"type");
        if(!compiled.has("type"))compiled.addProperty("type","action");
        if(!type.equals(UiSnapshot.text(compiled,"type")))throw new IllegalArgumentException("Compiler changed task type");
        if("query".equals(type)){compiled.remove("answer");compiled.remove("quote");compiled.remove("evidence");}
        if("query".equals(type)) {
            JsonObject outcome=trajectory.getAsJsonObject("outcome");
            String material=com.silentauto.shell.ext.query.AnswerVerifier.normalize(compiled.toString());
            for(String field:new String[]{"message","evidence"}) {
                String fact=com.silentauto.shell.ext.query.AnswerVerifier.normalize(UiSnapshot.text(outcome,field));
                if(!fact.isEmpty()&&material.contains(fact))throw new IllegalArgumentException("Query skill contains an answer or quote");
            }
        }
        validate(compiled, steps);
        return store.learn(UiSnapshot.text(trajectory, "packageName"), compiled, elapsed, callsBefore + 1);
    }

    public static JsonArray preprocess(JsonArray source) {
        List<JsonObject> kept = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            JsonObject step = source.get(i).getAsJsonObject();
            String action = UiSnapshot.text(step.getAsJsonObject("action"), "action");
            if (i + 1 < source.size() && !isText(action)) {
                JsonObject following=source.get(i+1).getAsJsonObject();
                String next=UiSnapshot.text(following.getAsJsonObject("action"),"action");
                if (("back".equals(next)||"press_back".equals(next)) && step.has("fingerprint") && following.has("fingerprintAfter")) {
                    ScreenFingerprint before=ScreenFingerprint.parse(step.getAsJsonObject("fingerprint"));
                    ScreenFingerprint after=ScreenFingerprint.parse(following.getAsJsonObject("fingerprintAfter"));
                    if(before.similarity(after)==1 && before.title.equals(after.title)){i++;continue;}
                }
            }
            if (Arrays.asList("wait", "get_ui_layout", "screenshot", "ocr", "done", "finish", "request_user", "report_answer", "report_not_found").contains(action)) continue;
            if (!UiSnapshot.bool(step, "uiChanged") && !isText(action)) continue;
            kept.add(step.deepCopy());
        }
        JsonArray result = new JsonArray(); kept.forEach(result::add); return result;
    }

    public static boolean isText(String action) { return "text".equals(action) || "input_text".equals(action); }
    public static JsonElement expand(JsonElement value, JsonObject args) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject out = new JsonObject(); for (String key : value.getAsJsonObject().keySet()) out.add(key, expand(value.getAsJsonObject().get(key), args)); return out;
        }
        if (value.isJsonArray()) { JsonArray out = new JsonArray(); for (JsonElement item : value.getAsJsonArray()) out.add(expand(item,args)); return out; }
        if (value.getAsJsonPrimitive().isString()) return new JsonPrimitive(substitute(value.getAsString(),args));
        return value.deepCopy();
    }

    public static String substitute(String text, JsonObject args) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}").matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!args.has(key) || !args.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing parameter: " + key);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(args.get(key).getAsString()));
        }
        matcher.appendTail(result); return result.toString();
    }

    public static void validate(JsonObject compiled, JsonArray original) {
        if (UiSnapshot.text(compiled, "name").isEmpty() || UiSnapshot.text(compiled, "description").isEmpty()
                || UiSnapshot.text(compiled, "query".equals(UiSnapshot.text(compiled,"type"))?"question":"evidence").isEmpty()) throw new IllegalArgumentException("Incomplete skill metadata");
        JsonObject examples = new JsonObject();
        for (JsonElement p : UiSnapshot.array(compiled, "params")) {
            JsonObject param = p.getAsJsonObject(); String name = UiSnapshot.text(param, "name");
            if (!name.matches("[A-Za-z][A-Za-z0-9_]*") || examples.has(name)) throw new IllegalArgumentException("Invalid skill parameter");
            examples.addProperty(name, UiSnapshot.text(param, "example"));
        }
        JsonArray steps = UiSnapshot.array(compiled, "steps");
        if (steps.isEmpty() || steps.size() != original.size()) throw new IllegalArgumentException("Compiler changed step count");
        substitute(UiSnapshot.text(compiled,"query".equals(UiSnapshot.text(compiled,"type"))?"question":"evidence"),examples);
        for(String key:examples.keySet())if(!steps.toString().contains("{"+key+"}"))throw new IllegalArgumentException("Unused skill parameter: "+key);
        for (int i = 0; i < steps.size(); i++) {
            JsonObject step = steps.get(i).getAsJsonObject(), record = original.get(i).getAsJsonObject();
            JsonObject action = record.getAsJsonObject("action"); String type = UiSnapshot.text(step, "action");
            String before = UiSnapshot.text(action, "action");
            if ("tap".equals(before)) before = "tap_target";
            if (isText(before)) before = "input_text";
            if (isText(type)) type = "input_text";
            if (!Arrays.asList("tap_target", "focus_input", "input_text", "long_press", "scroll", "back", "key", "submit_search", "clear_text").contains(type)
                    || !type.equals(before)) throw new IllegalArgumentException("Compiler changed action at step " + i);
            if (!step.has("expect") || !step.get("expect").isJsonObject()) throw new IllegalArgumentException("Missing fingerprint");
            ScreenFingerprint restored=ScreenFingerprint.parse(expand(step.get("expect"),examples).getAsJsonObject());
            ScreenFingerprint observed=ScreenFingerprint.parse(record.getAsJsonObject("fingerprint"));
            if(restored.similarity(observed)!=1||!restored.title.equals(observed.title))throw new IllegalArgumentException("Compiler changed screen fingerprint");
            JsonElement target=expand(step.get("target"),examples),originalTarget=record.has("target")?record.get("target"):JsonNull.INSTANCE;
            if(!target.equals(originalTarget))throw new IllegalArgumentException("Compiler changed target features");
            String required="scroll".equals(type)?"direction":"key".equals(type)?"key":"";
            if(!required.isEmpty()&&!UiSnapshot.text(action,required).equals(UiSnapshot.text(step,required)))throw new IllegalArgumentException("Compiler changed "+required);
            String numeric="scroll".equals(type)?"distance":"long_press".equals(type)?"durationMs":"";
            if(!numeric.isEmpty()&&UiSnapshot.integer(action,numeric)>0&&UiSnapshot.integer(action,numeric)!=UiSnapshot.integer(step,numeric))throw new IllegalArgumentException("Compiler changed "+numeric);
            if (!substitute(UiSnapshot.text(step, "text"), examples).equals(isText(type) ? UiSnapshot.text(action, "text") : ""))
                throw new IllegalArgumentException("Input consistency check failed at step " + i);
            if (Arrays.asList("tap_target", "focus_input", "long_press").contains(type)
                    && (!step.has("target") || !step.get("target").isJsonObject())) throw new IllegalArgumentException("Unlocatable skill target");
            for (String key : Arrays.asList("x","y","startX","startY","endX","endY","targetIndex","inputIndex")) step.remove(key);
            step.addProperty("action", type);
        }
    }
}
