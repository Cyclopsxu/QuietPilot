package com.silentauto.controller.ext.context;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class ScreenPrivacy {
    public static boolean disabled(Context context) { return context.getSharedPreferences("screen_privacy",0).getBoolean("disabled",false); }
    public static void save(Context context,boolean disabled) {
        context.getSharedPreferences("screen_privacy",0).edit().putBoolean("disabled",disabled).apply();
        new Thread(()->{try{sync(context);}catch(Exception ignored){}},"screen-privacy").start();
    }
    public static void sync(Context context) throws Exception {
        try(Socket socket=com.silentauto.controller.RpcConnection.connect("127.0.0.1",43110,3000)) {
            socket.setSoTimeout(3000);
            OutputStream output=socket.getOutputStream();
            output.write((new JSONObject().put("jsonrpc","2.0").put("id",1).put("method","setContextReading")
                    .put("params",new JSONObject().put("enabled",!disabled(context)))+"\n").getBytes(StandardCharsets.UTF_8));output.flush();
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            String line;while((line=reader.readLine())!=null)if(new JSONObject(line).optInt("id",-1)==1)break;
        }
    }
}
