// Modified by Codex for ShadowAuto: save and transmit three domestic model profiles. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import android.content.Context;
import android.content.SharedPreferences;

public final class AiConfigStore {
    private final SharedPreferences prefs;
    public AiConfigStore(Context context) { prefs = context.getApplicationContext().getSharedPreferences("ai_config", Context.MODE_PRIVATE); }
    public AiConfig load() {
        String legacyKey = prefs.getString("apiKey", "");
        if (!prefs.contains("executor.model") && !legacyKey.isEmpty()) {
            return new AiConfig(legacyKey, prefs.getString("apiBase", ""), prefs.getString("model", ""));
        }
        return new AiConfig(loadRole("executor", AiConfig.DEEPSEEK, "deepseek-flash"),
                loadRole("verifier", AiConfig.QWEN, "qwen3-vl-plus"),
                loadRole("intake", AiConfig.QWEN, "qwen3.8-omni-flash"));
    }
    private AiConfig.Model loadRole(String role, String base, String model) {
        return new AiConfig.Model(prefs.getString(role + ".apiKey", ""), prefs.getString(role + ".apiBase", base), prefs.getString(role + ".model", model));
    }
    AiConfig save(AiConfig config) {
        SharedPreferences.Editor editor = prefs.edit();
        String[] roles = {"executor", "verifier", "intake"};
        AiConfig.Model[] models = {config.executor, config.verifier, config.intake};
        for (int i = 0; i < roles.length; i++) {
            editor.putString(roles[i] + ".apiKey", models[i].apiKey).putString(roles[i] + ".apiBase", models[i].apiBase).putString(roles[i] + ".model", models[i].model);
        }
        editor.remove("apiKey").remove("apiBase").remove("model").apply();
        return config;
    }
    static String trimSlash(String value) { return value == null ? "" : value.trim().replaceAll("/+$", ""); }
}
