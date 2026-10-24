// Modified by Codex for ShadowAuto: close one-shot notification RPC connections. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

final class RpcClient {
    interface Listener {
        void onLine(String line);

        void onClosed(Exception error);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private final AtomicInteger ids = new AtomicInteger(1);

    private Socket socket;
    private BufferedWriter writer;

    RpcClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    synchronized void send(String method, JSONObject params) throws Exception {
        connect();
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", ids.getAndIncrement());
        request.put("method", method);
        request.put("params", params);
        try {
            writer.write(request.toString());writer.write('\n');writer.flush();
        } catch(java.io.IOException failed) {
            clearConnection(socket);throw failed; // Never replay a possibly delivered mutation.
        }
    }

    private synchronized void connect() throws Exception {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        Socket connected=RpcConnection.connect(host,port,3000);
        try {
            BufferedReader reader=new BufferedReader(new InputStreamReader(connected.getInputStream(),StandardCharsets.UTF_8));
            writer=new BufferedWriter(new OutputStreamWriter(connected.getOutputStream(),StandardCharsets.UTF_8));
            socket=connected;
            new Thread(() -> readLoop(connected,reader),"rpc-reader").start();
        } catch(Exception failed){connected.close();throw failed;}
    }

    private void readLoop(Socket connected,BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLine(line);
            }
        } catch (Exception e) {
            listener.onClosed(e);
        } finally {
            clearConnection(connected);
        }
    }

    synchronized void close() { clearConnection(socket); }

    private synchronized void clearConnection(Socket connected) {
        if(connected==null)return;
        try {connected.close();}catch(java.io.IOException ignored){}
        if(socket==connected){socket=null;writer=null;}
    }
}
