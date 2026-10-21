package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import com.silentauto.shell.*;
import com.silentauto.shell.ext.*;

/** Resolves controls from the current tree; recorded coordinates are never reused. */
public final class SkillRunner implements ActionSource {
    private final JsonObject skill, args;
    private int cursor;
    private boolean mismatched;
    public SkillRunner(JsonObject skill, JsonObject args) { this.skill = skill.deepCopy(); this.args = args.deepCopy(); }
    public String skillId() { return UiSnapshot.text(skill, "skillId"); }
    public boolean mismatched() { return mismatched; }

    @Override public ActionDecision next(ExecutionContext ctx, AutomationEngine.TaskState task, int step, String dump) {
        return next(new UiSnapshot(ctx.ui.dump(ctx.displayId, ctx.width, ctx.height, UiBridge.MODE_FULL)), ctx.height);
    }

    public ActionDecision next(UiSnapshot current, int height) {
        if (mismatched) return null;
        JsonArray steps = UiSnapshot.array(skill, "steps");
        ActionDecision action = new ActionDecision(); action.source = "skill";
        action.reason = UiSnapshot.text(skill, "name") + " · " + (cursor + 1) + "/" + steps.size();
        if (cursor >= steps.size()) {
            action.reason = UiSnapshot.text(skill, "name") + " · 验收";
            action.action = "done"; action.expectedEvidence = SkillCompiler.substitute(UiSnapshot.text(skill, "evidence"), args); return action;
        }
        JsonObject recorded = expand(steps.get(cursor), args).getAsJsonObject();
        ScreenFingerprint expected = ScreenFingerprint.parse(recorded.getAsJsonObject("expect"));
        if (expected.similarity(ScreenFingerprint.from(current, UiSnapshot.text(skill,"packageName"), height)) < .6) {
            mismatched = true; return null;
        }
        String type = UiSnapshot.text(recorded, "action");
        action.action = SkillCompiler.isText(type) ? "text" : type;
        action.text = UiSnapshot.text(recorded, "text"); action.direction = UiSnapshot.text(recorded,"direction");
        action.key = UiSnapshot.text(recorded,"key");
        action.distance = Math.max(0, UiSnapshot.integer(recorded,"distance"));
        action.durationMs = Math.max(0, UiSnapshot.integer(recorded,"durationMs"));
        boolean needsTarget = "tap_target".equals(type) || "focus_input".equals(type) || "long_press".equals(type) || SkillCompiler.isText(type);
        if (needsTarget) {
            JsonObject target = recorded.has("target") && recorded.get("target").isJsonObject() ? recorded.getAsJsonObject("target") : null;
            JsonObject node = findTarget(current, target, SkillCompiler.isText(type) || "focus_input".equals(type));
            if (node == null) { mismatched = true; return null; }
            if (SkillCompiler.isText(type) || "focus_input".equals(type)) {
                action.inputIndex = UiSnapshot.integer(node,"inputIndex");
                if (action.inputIndex <= 0) { mismatched = true; return null; }
            } else if ("long_press".equals(type)) {
                int[] b = UiSnapshot.bounds(node); action.x = (b[0] + b[2]) / 2; action.y = (b[1] + b[3]) / 2;
            } else {
                action.targetIndex = UiSnapshot.integer(node,"targetIndex");
                if (action.targetIndex <= 0) { mismatched = true; return null; }
            }
        }
        cursor++; return action;
    }

    public static JsonElement expand(JsonElement value, JsonObject args) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject out = new JsonObject(); for (String key : value.getAsJsonObject().keySet()) out.add(key, expand(value.getAsJsonObject().get(key), args)); return out;
        }
        if (value.isJsonArray()) { JsonArray out = new JsonArray(); for (JsonElement item : value.getAsJsonArray()) out.add(expand(item,args)); return out; }
        if (value.getAsJsonPrimitive().isString()) return new JsonPrimitive(SkillCompiler.substitute(value.getAsString(),args));
        return value.deepCopy();
    }

    public static JsonObject findTarget(UiSnapshot ui, JsonObject target, boolean input) {
        if (target == null) return null;
        JsonObject best = null; int highest = -1, second = -1;
        for (JsonObject node : ui.nodes) {
            if (node.has("visible") && !UiSnapshot.bool(node,"visible")) continue;
            if (node.has("enabled") && !UiSnapshot.bool(node,"enabled")) continue;
            if (input ? UiSnapshot.integer(node,"inputIndex") <= 0 : UiSnapshot.integer(node,"targetIndex") <= 0) continue;
            int score = score(UiSnapshot.features(node),target);
            if (score > highest) { second = highest; highest = score; best = node; }
            else second = Math.max(second, score);
        }
        return highest >= 5 && highest - second >= 2 ? best : null;
    }

    public static int score(JsonObject node, JsonObject target) {
        int score = 0;
        String[] fields = {"resourceId","text","contentDesc","className"}; int[] weights = {4,3,2,1};
        for (int i = 0; i < fields.length; i++) if (same(node,target,fields[i])) score += weights[i];
        if (UiSnapshot.integer(target,"siblingIndex") >= 0 && UiSnapshot.integer(node,"siblingIndex") == UiSnapshot.integer(target,"siblingIndex")) score++;
        if (same(node,target,"parentResourceId") || same(node,target,"parentText")) score++;
        return score;
    }
    private static boolean same(JsonObject a, JsonObject b, String key) {
        String value = UiSnapshot.text(b,key); return !value.isEmpty() && value.equals(UiSnapshot.text(a,key));
    }
}
