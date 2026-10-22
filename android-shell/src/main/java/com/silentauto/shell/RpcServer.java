// Modified by Codex for ShadowAuto: accept user handoff outcomes. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class RpcServer {
    private final int port;
    private final AutomationEngine engine;
    private final LogHub logs;

    RpcServer(int port, AutomationEngine engine, LogHub logs) {
        this.port = port;
        this.engine = engine;
        this.logs = logs;
    }

    void run() throws Exception {
        ServerSocket server = new ServerSocket(port, 20, InetAddress.getByName("127.0.0.1"));
        while (true) {
            Socket socket = server.accept();
            new Thread(() -> handle(socket), "rpc-client").start();
        }
    }

    private void handle(Socket socket) {
        Client client = null;
        try {
            client = new Client(socket);
            logs.add(client);
            client.send(JsonRpc.result(null, engine.statusJson()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                if ("subscribeStatus".equals(JsonRpc.text(request,"method"))) {
                    client.statusOnly = true; client.send(JsonRpc.result(JsonRpc.id(request),engine.statusJson()));
                } else client.send(dispatch(request));
            }
        } catch (Throwable e) {
            logs.error("rpc client closed", e);
        } finally {
            if (client != null) {
                logs.remove(client);
            }
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private JsonObject dispatch(JsonObject request) {
        JsonElement id = JsonRpc.id(request);
        try {
            String method = JsonRpc.text(request, "method");
            JsonObject params = JsonRpc.params(request);
            if ("ping".equals(method)) {
                return JsonRpc.result(id, "pong");
            }
            if ("status".equals(method)) {
                return JsonRpc.result(id, engine.statusJson());
            }
            if ("submitVoiceTask".equals(method)) return JsonRpc.result(id,engine.submitVoiceTask(params));
            if ("confirmTask".equals(method)) { engine.confirmTask(JsonRpc.text(params,"taskId"),params.get("ok").getAsBoolean()); return JsonRpc.result(id,"confirmed"); }
            if ("captureContext".equals(method)) return JsonRpc.result(id, engine.captureContext());
            if ("discardContext".equals(method)) { engine.discardContext(JsonRpc.text(params,"contextId")); return JsonRpc.result(id,"discarded"); }
            if ("setContextReading".equals(method)) { engine.setContextReading(params.get("enabled").getAsBoolean()); return JsonRpc.result(id,"saved"); }
            if ("listHistory".equals(method)) return JsonRpc.result(id,engine.listHistory());
            if ("getTask".equals(method)) return JsonRpc.result(id,engine.getTask(JsonRpc.text(params,"taskId")));
            if ("listSkills".equals(method)) return JsonRpc.result(id,engine.listSkills());
            if ("setSkillStatus".equals(method)) { engine.setSkillStatus(JsonRpc.text(params,"packageName"),JsonRpc.text(params,"skillId"),JsonRpc.text(params,"status")); return JsonRpc.result(id,"saved"); }
            if ("deleteSkill".equals(method)) { engine.deleteSkill(JsonRpc.text(params,"packageName"),JsonRpc.text(params,"skillId")); return JsonRpc.result(id,"deleted"); }
            if ("listTasks".equals(method)) {
                return JsonRpc.result(id, engine.listTasks());
            }
            if ("logs".equals(method)) {
                return JsonRpc.result(id, JsonRpc.stringArray(logs.snapshot()));
            }
            if ("dumpUi".equals(method)) {
                return JsonRpc.result(id, engine.dumpUiJson(
                        integer(params, "displayId", 0),
                        integer(params, "width", 0),
                        integer(params, "height", 0),
                        JsonRpc.text(params, "mode")
                ));
            }
            if ("forceStart".equals(method)) {
                engine.forceStart(JsonRpc.text(params, "taskId"));
                return JsonRpc.result(id, "queued start requested");
            }
            if ("injectTouch".equals(method)) {
                engine.injectTouch(JsonRpc.text(params, "taskId"), JsonRpc.text(params, "action"),
                        params.get("x").getAsFloat(), params.get("y").getAsFloat());
                return JsonRpc.result(id, "injected");
            }
            if ("openTaskOnMain".equals(method)) {
                engine.openTaskOnMain(JsonRpc.text(params, "taskId"));
                return JsonRpc.result(id, "opening");
            }
            if("openQuerySource".equals(method)){engine.openQuerySource(JsonRpc.text(params,"taskId"));return JsonRpc.result(id,"opened");}
            if ("takeResultOnMain".equals(method)) return JsonRpc.result(id,engine.takeResultOnMain(JsonRpc.text(params,"taskId")));
            if ("resumeTask".equals(method)) {
                engine.resumeTask(JsonRpc.text(params, "taskId"), JsonRpc.text(params, "outcome"));
                return JsonRpc.result(id, "resumed");
            }
            if ("stopTask".equals(method)) {
                String taskId = JsonRpc.text(params, "taskId");
                if (taskId.isEmpty()) {
                    engine.stopTask();
                } else {
                    engine.stopTask(taskId);
                }
                return JsonRpc.result(id, "stopped");
            }
            if ("startTask".equals(method)) {
                AutomationEngine.TaskRequest taskRequest = new AutomationEngine.TaskRequest(
                        JsonRpc.text(params, "taskId"),
                        JsonRpc.text(params, "goal"),
                        JsonRpc.text(params, "apiKey"),
                        JsonRpc.text(params, "apiBase"),
                        JsonRpc.text(params, "model")
                );
                taskRequest.type=com.silentauto.shell.ext.query.TaskType.parse(JsonRpc.text(params,"type"));
                taskRequest.models = params.getAsJsonObject("models");
                String taskId = engine.startTask(taskRequest);
                JsonObject value = new JsonObject();
                value.addProperty("taskId", taskId);
                value.addProperty("value", "started");
                return JsonRpc.result(id, value);
            }
            return JsonRpc.error(id, -32601, "unknown method: " + method);
        } catch (Throwable e) {
            return JsonRpc.error(id, -32000, e.getMessage());
        }
    }

    private int integer(JsonObject object, String name, int fallback) {
        try {
            if (object.has(name) && !object.get(name).isJsonNull()) {
                return object.get(name).getAsInt();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static final class Client implements LogHub.Sink {
        private final BufferedWriter writer;
        private volatile boolean statusOnly;

        Client(Socket socket) throws Exception {
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public synchronized void send(JsonObject object) {
            if (statusOnly && object.has("params")) {
                JsonObject event = object.getAsJsonObject("params");
                if (!"status".equals(JsonRpc.text(event,event.has("event")?"event":"type"))) return;
            }
            try {
                writer.write(JsonRpc.GSON.toJson(object));
                writer.write('\n');
                writer.flush();
            } catch (Exception ignored) {
            }
        }
    }
}
