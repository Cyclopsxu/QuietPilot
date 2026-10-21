package com.silentauto.shell.ext.skill;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent, synchronized skill collection. All returned objects are detached from disk state. */
public final class SkillStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final File root;

    public SkillStore() { this(new File("/data/local/tmp/shadowauto/skills")); }
    public SkillStore(File root) { this.root = root; }

    private File file(String pkg, String id) {
        if (!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || !id.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("Invalid skill package or id");
        return new File(new File(root, pkg), id + ".json");
    }

    public synchronized JsonObject get(String pkg, String id) throws IOException {
        File path = file(pkg, id);
        if (!path.isFile()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public synchronized JsonArray list(boolean activeOnly) {
        JsonArray result = new JsonArray();
        File[] packages = root.listFiles(File::isDirectory);
        if (packages == null) return result;
        Arrays.sort(packages, Comparator.comparing(File::getName));
        for (File pkg : packages) {
            File[] files = pkg.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File path : files) try {
                JsonObject skill = get(pkg.getName(), path.getName().replaceFirst("\\.json$", ""));
                if (!activeOnly || "active".equals(UiSnapshot.text(skill, "status"))) result.add(skill);
            } catch (Exception ignored) { /* An incomplete/corrupt file never enters matching. */ }
        }
        return result;
    }

    public synchronized JsonObject learn(String pkg, JsonObject compiled, long elapsed, int calls) throws IOException {
        String id = UiSnapshot.text(compiled, "skillId");
        JsonObject old = get(pkg, id);
        JsonObject skill = compiled.deepCopy();
        if (old != null && UiSnapshot.array(old, "steps").size() - UiSnapshot.array(compiled, "steps").size() < 2) {
            skill = old.deepCopy(); // Preserve the entire parameter/step contract when keeping an old route.
        }
        JsonObject stats = old == null ? new JsonObject() : old.getAsJsonObject("stats").deepCopy();
        if (old == null) {
            stats.addProperty("uses", 0); stats.addProperty("successes", 0);
            stats.addProperty("avgElapsedMs", 0); stats.addProperty("avgLlmCalls", 0);
            stats.addProperty("firstElapsedMs", elapsed); stats.addProperty("firstLlmCalls", calls);
        }
        addUse(stats, elapsed, calls, true);
        stats.addProperty("consecutiveAbandons", 0);
        skill.add("stats", stats); skill.addProperty("packageName", pkg);
        // A user-disabled skill stays disabled. A successful new exploration rehabilitates suspect skills.
        skill.addProperty("status", old != null && "disabled".equals(UiSnapshot.text(old, "status")) ? "disabled" : "active");
        write(skill); return skill;
    }

    public synchronized void abandoned(String pkg, String id) throws IOException {
        JsonObject skill = get(pkg, id);
        if (skill == null) return;
        JsonObject stats = skill.getAsJsonObject("stats");
        int count = Math.max(0, UiSnapshot.integer(stats, "consecutiveAbandons")) + 1;
        stats.addProperty("consecutiveAbandons", count);
        if (count >= 3 && !"disabled".equals(UiSnapshot.text(skill, "status"))) skill.addProperty("status", "suspect");
        write(skill);
    }
    public synchronized void succeeded(String pkg, String id) throws IOException {
        JsonObject skill = get(pkg, id);
        if (skill == null) return;
        skill.getAsJsonObject("stats").addProperty("consecutiveAbandons", 0);
        write(skill);
    }

    private static void addUse(JsonObject stats, long elapsed, int calls, boolean success) {
        int uses = Math.max(0, UiSnapshot.integer(stats, "uses"));
        double avgMs = stats.has("avgElapsedMs") ? stats.get("avgElapsedMs").getAsDouble() : 0;
        double avgCalls = stats.has("avgLlmCalls") ? stats.get("avgLlmCalls").getAsDouble() : 0;
        stats.addProperty("avgElapsedMs", (avgMs * uses + elapsed) / (uses + 1));
        stats.addProperty("avgLlmCalls", (avgCalls * uses + calls) / (uses + 1));
        stats.addProperty("uses", uses + 1);
        stats.addProperty("successes", Math.max(0, UiSnapshot.integer(stats, "successes")) + (success ? 1 : 0));
        stats.addProperty("lastUsedAt", System.currentTimeMillis());
    }

    private void write(JsonObject skill) throws IOException {
        File path = file(UiSnapshot.text(skill, "packageName"), UiSnapshot.text(skill, "skillId"));
        Files.createDirectories(path.getParentFile().toPath());
        Path temp = Files.createTempFile(path.getParentFile().toPath(), "skill-", ".tmp");
        try {
            Files.write(temp, GSON.toJson(skill).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, path.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
