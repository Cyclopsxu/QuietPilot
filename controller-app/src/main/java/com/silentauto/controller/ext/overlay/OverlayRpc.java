package com.silentauto.controller.ext.overlay;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Status-only subscription plus correlated RPCs; no video stream is sent to overlays. */
public final class OverlayRpc implements AutoCloseable {
    private final Consumer<JSONObject> status;
    private final AtomicInteger ids=new AtomicInteger(1);
    private final ConcurrentHashMap<Integer,CompletableFuture<JSONObject>> waiting=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private Socket socket;private BufferedWriter writer;
    public OverlayRpc(Consumer<JSONObject> status){this.status=status;new Thread(this::loop,"overlay-rpc").start();}
    private void loop(){while(!closed){try{
        Socket connected=com.silentauto.controller.RpcConnection.connect("127.0.0.1",43110,3000);
        synchronized(this){if(closed){connected.close();return;}socket=connected;writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));write("subscribeStatus",new JSONObject(),0);}
        BufferedReader reader=new BufferedReader(new InputStreamReader(connected.getInputStream(),StandardCharsets.UTF_8));String line;
        while(!closed&&(line=reader.readLine())!=null){JSONObject value=new JSONObject(line);
            if(value.has("id")){int id=value.optInt("id",-1);CompletableFuture<JSONObject> future=waiting.remove(id);if(future!=null){if(value.has("error"))future.completeExceptionally(new IOException(value.getJSONObject("error").optString("message")));else future.complete(value);} }
            JSONObject event=value.optJSONObject("params");if(event!=null&&"status".equals(event.optString("event",event.optString("type"))))status.accept(event);
            JSONObject result=value.optJSONObject("result");JSONArray tasks=result==null?value.optJSONArray("result"):result.optJSONArray("tasks");
            if(tasks!=null)for(int i=0;i<tasks.length();i++)status.accept(tasks.getJSONObject(i));
        }
    }catch(Exception ignored){}finally{disconnect();}
        if(!closed)try{Thread.sleep(1000);}catch(InterruptedException e){return;}
    }}
    public JSONObject request(String method,JSONObject params)throws Exception{return request(method,params,110);}
    public JSONObject request(String method,JSONObject params,long timeoutSeconds)throws Exception{
        for(int i=0;i<60;i++){synchronized(this){if(writer!=null)break;if(closed)throw new IOException("连接已关闭");}Thread.sleep(50);}
        int id=ids.getAndIncrement();CompletableFuture<JSONObject> future=new CompletableFuture<>();waiting.put(id,future);
        try{ synchronized(this){if(writer==null)throw new IOException("后台未连接，请先启动 shell");write(method,params,id);}return future.get(timeoutSeconds,TimeUnit.SECONDS);}
        finally{waiting.remove(id);}
    }
    private void write(String method,JSONObject params,int id)throws Exception{writer.write(new JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method).put("params",params).toString());writer.newLine();writer.flush();}
    private synchronized void disconnect(){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;writer=null;for(CompletableFuture<JSONObject> pending:waiting.values())pending.completeExceptionally(new IOException("后台连接断开"));waiting.clear();}
    @Override public void close(){closed=true;disconnect();}
}
