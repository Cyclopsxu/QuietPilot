// Modified by Codex for ShadowAuto: configure executor, verifier and intake with domestic defaults. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import com.google.gson.JsonObject;
import com.silentauto.shell.ext.config.ModelConfig;
import java.util.HashMap;
import java.util.Map;

final class Config {
    final int port;
    boolean allowUnverifiedIsolation;
    final String apiKey, apiBase, model;
    final ModelConfig executor, verifier, intake;

    Config(int port, String key, String base, String model) {
        this(port, new ModelConfig(empty(base) ? ModelConfig.DEEPSEEK : base, key,
                empty(model) ? "deepseek-flash" : model));
    }

    private Config(int port, ModelConfig legacy) { this(port, legacy, legacy, legacy); }

    Config(int port, ModelConfig executor, ModelConfig verifier, ModelConfig intake) {
        this.port = port;
        this.executor = executor;
        this.verifier = verifier;
        this.intake = intake;
        apiKey = executor.apiKey;
        apiBase = executor.apiBase;
        model = executor.model;
    }

    Config forRole(ModelConfig role) { return new Config(port, role); }

    Config withOverrides(String key, String base, String model) {
        if (empty(key) && empty(base) && empty(model)) return this;
        return new Config(port, empty(key) ? apiKey : key, empty(base) ? apiBase : base, empty(model) ? this.model : model);
    }

    Config withProfiles(JsonObject profiles) {
        if (profiles == null) return this;
        return new Config(port, executor.override(profiles.getAsJsonObject("executor")),
                verifier.override(profiles.getAsJsonObject("verifier")), intake.override(profiles.getAsJsonObject("intake")));
    }

    static Config from(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (arg != null && arg.startsWith("--") && arg.indexOf('=') > 2) {
                int i = arg.indexOf('='); values.put(arg.substring(2, i), arg.substring(i + 1));
            }
        }
        Map<String, String> env = System.getenv();
        int port = 43110;
        try { port = Integer.parseInt(first(values.get("port"), env.get("SILENT_AUTO_PORT"))); } catch (Exception ignored) {}
        String key = first(values.get("api-key"), env.get("OPENAI_API_KEY"));
        String base = first(values.get("api-base"), env.get("OPENAI_BASE_URL"));
        String model = first(values.get("model"), env.get("OPENAI_MODEL"));
        Config config = !empty(key) || !empty(base) || !empty(model) ? new Config(port, key, base, model)
                : new Config(port,
                    new ModelConfig(ModelConfig.DEEPSEEK, env.get("DEEPSEEK_API_KEY"), "deepseek-flash"),
                    new ModelConfig(ModelConfig.QWEN, env.get("DASHSCOPE_API_KEY"), "qwen3-vl-plus"),
                    new ModelConfig(ModelConfig.QWEN, env.get("DASHSCOPE_API_KEY"), "qwen3.8-omni-flash"));
        JsonObject roles = new JsonObject();
        for (String role : new String[]{"executor", "verifier", "intake"}) {
            JsonObject value = new JsonObject();
            for (String field : new String[]{"api-key", "api-base", "model"}) {
                String v = first(values.get(role + "-" + field), env.get("SHADOWAUTO_" + role.toUpperCase(java.util.Locale.ROOT) + "_" + field.toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
                if (!empty(v)) value.addProperty(field.equals("api-key") ? "apiKey" : field.equals("api-base") ? "apiBase" : "model", v);
            }
            roles.add(role, value);
        }
        Config result=config.withProfiles(roles);
        result.allowUnverifiedIsolation="true".equals(values.get("allow-unverified-isolation"));
        return result;
    }

    static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static String first(String a, String b) { return empty(a) ? b : a; }
}
