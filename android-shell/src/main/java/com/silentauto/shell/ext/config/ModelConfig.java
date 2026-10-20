package com.silentauto.shell.ext.config;

import com.google.gson.JsonObject;
import java.net.URI;

public final class ModelConfig {
    public static final String DEEPSEEK = "https://api.deepseek.com/v1";
    public static final String QWEN = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public final String apiBase, apiKey, model;

    public ModelConfig(String base, String key, String model) {
        this.apiBase = base == null ? "" : base.trim().replaceAll("/+$", "");
        this.apiKey = key == null ? "" : key.trim();
        this.model = model == null ? "" : model.trim();
    }

    public ModelConfig override(JsonObject object) {
        if (object == null) return this;
        return new ModelConfig(value(object, "apiBase", apiBase), value(object, "apiKey", apiKey), value(object, "model", model));
    }

    public void validate() {
        if (apiKey.isEmpty() || model.isEmpty()) throw new IllegalArgumentException("model and apiKey are required");
        validateEndpoint(apiBase);
    }

    public static void validateEndpoint(String base) {
        URI uri = URI.create(base);
        String host = uri.getHost();
        boolean local = "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
        boolean official = "api.deepseek.com".equals(host) || "dashscope.aliyuncs.com".equals(host);
        if ((!local && !(official && "https".equals(uri.getScheme())))
                || (local && !"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Use DeepSeek official or Alibaba Cloud mainland endpoint (loopback allowed for local tests)");
        }
    }

    private static String value(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
}
