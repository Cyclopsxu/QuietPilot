package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import com.silentauto.shell.*;
import com.silentauto.shell.ext.*;

/** Resolves controls from the current tree; recorded coordinates are never reused. */
public final class SkillRunner implements ActionSource {
    private final JsonObject skill, args;
    private int cursor, emptyWaits;
    public enum State { FOLLOWING, RESCUING, ABANDONED }
    private State state = State.FOLLOWING;
    private int rescueSteps;
    private final Runnable onAbandon;
    public SkillRunner(JsonObject skill, JsonObject args) { this(skill, args, () -> {}); }
    public SkillRunner(JsonObject skill, JsonObject args, Runnable onAbandon) {
        this.skill = skill.deepCopy(); this.args = args.deepCopy(); this.onAbandon = onAbandon;
    }
    public void retryUnexecuted(ActionDecision action){if("skill".equals(action.source)&&!java.util.Arrays.asList("done","report_answer","wait").contains(action.action)&&cursor>0)cursor--;}
    public void forceRescue(){if(state!=State.ABANDONED){state=State.RESCUING;rescueSteps=0;}}
    public String question(){return SkillCompiler.substitute(UiSnapshot.text(skill,"question"),args);}
    public String skillId() { return UiSnapshot.text(skill, "skillId"); }
    public State state() { return state; }
    public String rescuePrompt() {
        JsonArray steps = UiSnapshot.array(skill, "steps");
        JsonObject expected = expand(steps.get(cursor), args).getAsJsonObject().getAsJsonObject("expect");
        return "\n你正在执行技能『" + UiSnapshot.text(skill, "name")
                + "』，当前界面与预期不符。目标是回到下面这个界面：" + expected
                + "。然后停止救援，不要推进任务本身，不要输入或发送任务消息，不要点击完成。仅恢复界面或关闭打断弹窗。"
                + "这是第 " + (rescueSteps + 1) + "/3 个救援动作。";
    }
    public void rescueAction() { if (state == State.RESCUING) rescueSteps++; }
    private ActionDecision mismatch() {
        if (state == State.FOLLOWING) { state = State.RESCUING; rescueSteps = 0; }
        if (rescueSteps >= 3) { state = State.ABANDONED; onAbandon.run(); }
        return null;
    }

    @Override public ActionDecision next(ExecutionContext ctx, AutomationEngine.TaskState task, int step, String dump) {
        return next(new UiSnapshot(ctx.ui.dump(ctx.displayId, ctx.width, ctx.height, UiBridge.MODE_FULL)), ctx.height);
    }

    public ActionDecision next(UiSnapshot current, int height) {
        if (state == State.ABANDONED) return null;
        boolean visible=false;for(JsonObject node:current.foreground().nodes)if(UiSnapshot.text(skill,"packageName").equals(UiSnapshot.text(node,"package")))visible=true;
        if(!visible&&emptyWaits++<4){ActionDecision waiting=new ActionDecision();waiting.source="skill";waiting.action="wait";waiting.ms=500;waiting.reason="等待技能页面出现";return waiting;}
        if(visible)emptyWaits=0;
        JsonArray steps = UiSnapshot.array(skill, "steps");
        ActionDecision action = new ActionDecision(); action.source = "skill";
        action.reason = UiSnapshot.text(skill, "name") + " · " + (cursor + 1) + "/" + steps.size();
        if (cursor >= steps.size()) {
            action.reason = UiSnapshot.text(skill, "name") + " · 验收";
            action.action = "query".equals(UiSnapshot.text(skill,"type"))?"report_answer":"done"; action.expectedEvidence = SkillCompiler.substitute(UiSnapshot.text(skill, "evidence"), args); return action;
        }
        JsonObject recorded = expand(steps.get(cursor), args).getAsJsonObject();
        ScreenFingerprint expected = ScreenFingerprint.parse(recorded.getAsJsonObject("expect"));
        if (expected.similarity(ScreenFingerprint.from(current, UiSnapshot.text(skill,"packageName"), height)) < .6) {
            return mismatch();
        }
        String type = UiSnapshot.text(recorded, "action");
        action.action = SkillCompiler.isText(type) ? "text" : type;
        action.text = UiSnapshot.text(recorded, "text"); action.direction = UiSnapshot.text(recorded,"direction");
        action.key = UiSnapshot.text(recorded,"key");
        action.distance = Math.max(0, UiSnapshot.integer(recorded,"distance"));
        action.durationMs = Math.max(0, UiSnapshot.integer(recorded,"durationMs"));
        boolean needsTarget = "tap_target".equals(type) || "focus_input".equals(type) || "long_press".equals(type) || SkillCompiler.isText(type) || "clear_text".equals(type);
        if (needsTarget) {
            JsonObject target = recorded.has("target") && recorded.get("target").isJsonObject() ? recorded.getAsJsonObject("target") : null;
            JsonObject node = findTarget(current, target, SkillCompiler.isText(type) || "focus_input".equals(type) || "clear_text".equals(type));
            if(node==null&&"focus_input".equals(type)){
                node=findTarget(current,target,false);
                if(node!=null&&UiSnapshot.integer(node,"inputIndex")<=0){action.action="tap_target";action.targetIndex=UiSnapshot.integer(node,"targetIndex");state=State.FOLLOWING;rescueSteps=0;cursor++;return action;}
            }
            if (node == null) { return mismatch(); }
            if (SkillCompiler.isText(type) || "focus_input".equals(type) || "clear_text".equals(type)) {
                action.inputIndex = UiSnapshot.integer(node,"inputIndex");
                if (action.inputIndex <= 0) { return mismatch(); }
            } else if ("long_press".equals(type)) {
                int[] b = UiSnapshot.bounds(node); action.x = (b[0] + b[2]) / 2; action.y = (b[1] + b[3]) / 2;
            } else {
                action.targetIndex = UiSnapshot.integer(node,"targetIndex");
                if (action.targetIndex <= 0) { return mismatch(); }
            }
        }
        state = State.FOLLOWING; rescueSteps = 0;
        cursor++; return action;
    }

    public static JsonElement expand(JsonElement value,JsonObject args){return SkillCompiler.expand(value,args);}

    public static JsonObject findTarget(UiSnapshot ui, JsonObject target, boolean input) {
        if (target == null) return null;
        JsonObject best = null; int highest = -1, second = -1;
        for (JsonObject node : ui.foreground().nodes) {
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
