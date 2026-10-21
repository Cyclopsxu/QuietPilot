package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import java.util.*;

/** UI structure shared by recording and semantic target matching. Bounds are recorded only as reference, never used as persistent locators. */
public final class UiSnapshot {
    public final List<JsonObject> nodes = new ArrayList<>();
    public final JsonObject dump;
    public UiSnapshot(String json) {
        dump = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement item : array(dump, "windows")) {
            JsonObject window = item.getAsJsonObject();
            if (window.has("root") && window.get("root").isJsonObject()) walk(window.getAsJsonObject("root"), null, 0);
        }
    }
    private void walk(JsonObject node, JsonObject parent, int sibling) {
        JsonObject enriched = node.deepCopy(); enriched.remove("children");
        enriched.addProperty("siblingIndex", sibling);
        enriched.addProperty("parentId", parent == null ? "" : text(parent, "id"));
        enriched.addProperty("parentText", parent == null ? "" : text(parent, "text"));
        nodes.add(enriched);
        Map<String,Integer> counts = new HashMap<>();
        for (JsonElement child : array(node, "children")) {
            if (!child.isJsonObject()) continue;
            JsonObject value = child.getAsJsonObject(); String type = text(value, "class");
            int index = counts.getOrDefault(type, 0); counts.put(type, index + 1);
            walk(value, node, index);
        }
    }
    public JsonObject target(com.silentauto.shell.ActionDecision action) {
        JsonObject best = null; long area = Long.MAX_VALUE;
        for (JsonObject node : nodes) {
            if (("text".equals(action.action) || "input_text".equals(action.action)) && (action.inputIndex > 0
                    ? integer(node,"inputIndex") == action.inputIndex : bool(node,"editable") && bool(node,"focused"))) return features(node);
            if ("tap_target".equals(action.action) && integer(node,"targetIndex") == action.targetIndex && action.targetIndex > 0) return features(node);
            if ("focus_input".equals(action.action) && integer(node,"inputIndex") == action.inputIndex && action.inputIndex > 0) return features(node);
            if ("tap".equals(action.action) || "long_press".equals(action.action)) {
                int[] b = bounds(node); long a = (long)(b[2]-b[0])*(b[3]-b[1]);
                if (bool(node,"clickable") && a > 0 && action.x >= b[0] && action.x < b[2] && action.y >= b[1] && action.y < b[3] && a < area) { best = node; area = a; }
            }
        }
        return best == null ? null : features(best);
    }
    public UiSnapshot foreground() {
        for (JsonElement item : array(dump, "windows")) {
            JsonObject window = item.getAsJsonObject();
            if (integer(window, "type") != 1 || !window.has("root")) continue;
            JsonObject selected = new JsonObject(); JsonArray windows = new JsonArray();
            windows.add(window); selected.add("windows", windows); return new UiSnapshot(selected.toString());
        }
        return this;
    }
    public static JsonObject features(JsonObject node) {
        JsonObject out = new JsonObject();
        String[][] fields = {{"text","text"},{"contentDesc","desc"},{"resourceId","id"},{"className","class"},{"hint","hint"},{"parentResourceId","parentId"},{"parentText","parentText"},{"bounds","bounds"}};
        for (String[] field : fields) out.addProperty(field[0],text(node,field[1]));
        out.addProperty("siblingIndex",integer(node,"siblingIndex")); return out;
    }
    public static String text(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : ""; }
    public static int integer(JsonObject o, String key) { try { return o.get(key).getAsInt(); } catch (Exception e) { return -1; } }
    public static boolean bool(JsonObject o,String key) { return o.has(key) && o.get(key).isJsonPrimitive() && "true".equals(o.get(key).getAsString()); }
    public static JsonArray array(JsonObject o,String key) { return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray(); }
    public static int[] bounds(JsonObject node) {
        try { String[] values = text(node,"bounds").split(","); return new int[]{Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2]),Integer.parseInt(values[3])}; }
        catch (Exception e) { return new int[4]; }
    }
}
