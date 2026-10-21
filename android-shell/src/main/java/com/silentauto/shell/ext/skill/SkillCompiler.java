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
                || !"VERIFIED".equals(UiSnapshot.text(trajectory.getAsJsonObject("outcome"), "status"))
                || UiSnapshot.bool(trajectory, "handedOff")) return null;
        JsonArray steps = preprocess(UiSnapshot.array(trajectory, "steps"));
        if (steps.isEmpty()) return null;
        JsonObject input = new JsonObject(); input.addProperty("goal", UiSnapshot.text(trajectory, "goal"));
        input.addProperty("packageName", UiSnapshot.text(trajectory, "packageName")); input.add("steps", steps);
        input.add("outcome", trajectory.get("outcome"));
        String raw = model.complete("""
                SKILL_COMPILE 技能编译。把已验收轨迹编译为可复用技能，只输出 JSON。
                字段：skillId(英文点分标识)、name、description、params[{name,desc,example}]、
                steps[{expect:原始 fingerprint 对象,action:动作名,target:原始 target 对象或null,text:字符串或null,其余必要动作参数}]、evidence。
                联系人、消息、搜索词等任务变量用 {param} 参数化，同时参数化 expect.stableTexts/target/evidence 内相应文字。
                严格保持步骤顺序、动作与输入次数；每步 text 代入 example 必须与轨迹 action.text 原文完全一致。
                非文字动作 text=null；可用动作 tap_target/focus_input/input_text/long_press/scroll/back/key/submit_search/clear_text。
                坐标点击必须改为原 target 特征定位，无法定位则放弃返回 {}。不允许保存坐标、index 作为定位器。
                action 的方向、按键、时长等参数从轨迹 action 保留。不要创建轨迹中没有的操作或交接。
                输入：
                """ + input);
        int begin = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (begin < 0 || end <= begin) throw new IllegalArgumentException("Compiler returned no JSON");
        JsonObject compiled = JsonParser.parseString(raw.substring(begin, end + 1)).getAsJsonObject();
        validate(compiled, steps);
        return store.learn(UiSnapshot.text(trajectory, "packageName"), compiled, elapsed, callsBefore + 1);
    }

    public static JsonArray preprocess(JsonArray source) {
        List<JsonObject> kept = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            JsonObject step = source.get(i).getAsJsonObject();
            if (i + 1 < source.size()) {
                String following = UiSnapshot.text(source.get(i + 1).getAsJsonObject().getAsJsonObject("action"), "action");
                if ("back".equals(following) || "press_back".equals(following)) { i++; continue; }
            } String action = UiSnapshot.text(step.getAsJsonObject("action"), "action");
            if (Set.of("wait", "get_ui_layout", "screenshot", "ocr", "done", "finish", "request_user").contains(action)) continue;
            if ("back".equals(action) || "press_back".equals(action)) {
                continue;
            }
            if (!UiSnapshot.bool(step, "uiChanged") && !isText(action)) continue;
            kept.add(step.deepCopy());
        }
        JsonArray result = new JsonArray(); kept.forEach(result::add); return result;
    }

    public static boolean isText(String action) { return "text".equals(action) || "input_text".equals(action); }
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
                || UiSnapshot.text(compiled, "evidence").isEmpty()) throw new IllegalArgumentException("Incomplete skill metadata");
        JsonObject examples = new JsonObject();
        for (JsonElement p : UiSnapshot.array(compiled, "params")) {
            JsonObject param = p.getAsJsonObject(); String name = UiSnapshot.text(param, "name");
            if (!name.matches("[A-Za-z][A-Za-z0-9_]*") || examples.has(name)) throw new IllegalArgumentException("Invalid skill parameter");
            examples.addProperty(name, UiSnapshot.text(param, "example"));
        }
        JsonArray steps = UiSnapshot.array(compiled, "steps");
        if (steps.isEmpty() || steps.size() != original.size()) throw new IllegalArgumentException("Compiler changed step count");
        for (int i = 0; i < steps.size(); i++) {
            JsonObject step = steps.get(i).getAsJsonObject(), record = original.get(i).getAsJsonObject();
            JsonObject action = record.getAsJsonObject("action"); String type = UiSnapshot.text(step, "action");
            String before = UiSnapshot.text(action, "action");
            if ("tap".equals(before)) before = "tap_target";
            if (isText(before)) before = "input_text";
            if (isText(type)) type = "input_text";
            if (!Set.of("tap_target", "focus_input", "input_text", "long_press", "scroll", "back", "key", "submit_search", "clear_text").contains(type)
                    || !type.equals(before)) throw new IllegalArgumentException("Compiler changed action at step " + i);
            if (!step.has("expect") || !step.get("expect").isJsonObject()) throw new IllegalArgumentException("Missing fingerprint");
            if (!substitute(UiSnapshot.text(step, "text"), examples).equals(isText(type) ? UiSnapshot.text(action, "text") : ""))
                throw new IllegalArgumentException("Input consistency check failed at step " + i);
            if (Set.of("tap_target", "focus_input", "long_press").contains(type)
                    && (!step.has("target") || !step.get("target").isJsonObject())) throw new IllegalArgumentException("Unlocatable skill target");
            for (String key : List.of("x","y","startX","startY","endX","endY","targetIndex","inputIndex")) step.remove(key);
            step.addProperty("action", type);
        }
    }
}
