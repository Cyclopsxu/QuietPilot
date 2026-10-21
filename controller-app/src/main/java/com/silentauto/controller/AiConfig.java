// Modified by Codex for ShadowAuto: save and transmit three domestic model profiles. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import org.json.JSONObject;
import java.net.URI;

public final class AiConfig {
    static final String DEEPSEEK = "https://api.deepseek.com/v1";
    static final String QWEN = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    static final class Model {
        final String apiKey, apiBase, model;
        Model(String key, String base, String model) {
            this.apiKey = key == null ? "" : key.trim();
            this.apiBase = AiConfigStore.trimSlash(base);
            this.model = model == null ? "" : model.trim();
        }
        boolean configured() { return !apiKey.isEmpty() && !apiBase.isEmpty() && !model.isEmpty(); }
        JSONObject json() throws Exception {
            return new JSONObject().put("apiKey", apiKey).put("apiBase", apiBase).put("model", model);
        }
        void validate() {
            URI uri = URI.create(apiBase);
            String host = uri.getHost();
            boolean local = "127.0.0.1".equals(host) || "localhost".equals(host);
            if (!configured() || (!local && !("https".equals(uri.getScheme())
                    && ("api.deepseek.com".equals(host) || "dashscope.aliyuncs.com".equals(host))))
                    || (local && !"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("请填写 DeepSeek 官方或阿里云百炼国内接口、API Key 和模型");
            }
        }
    }
    final Model executor, verifier, intake;
    final String apiKey, apiBase, model;
    AiConfig(String key, String base, String model) { this(new Model(key, base, model), new Model(key, base, model), new Model(key, base, model)); }
    AiConfig(Model executor, Model verifier, Model intake) {
        this.executor = executor; this.verifier = verifier; this.intake = intake;
        apiKey = executor.apiKey; apiBase = executor.apiBase; model = executor.model;
    }
    boolean isConfigured() { return executor.configured() && verifier.configured(); }
    public JSONObject profilesJson() throws Exception {
        return new JSONObject().put("executor", executor.json()).put("verifier", verifier.json()).put("intake", intake.json());
    }
}
