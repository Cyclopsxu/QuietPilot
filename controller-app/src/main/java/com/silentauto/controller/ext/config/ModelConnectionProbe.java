package com.silentauto.controller.ext.config;

import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Small real completion probe; never returns credentials, response content, or provider errors. */
public final class ModelConnectionProbe {
    private ModelConnectionProbe() {}
    public static String test(String base,String key,String model) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(base+"/chat/completions").openConnection();
        try {
            connection.setConnectTimeout(15000);connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Authorization","Bearer "+key);
            connection.setRequestProperty("Content-Type","application/json");
            JSONObject body=new JSONObject().put("model",model).put("stream",false)
                .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","Reply OK.")));
            try(java.io.OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();if(code!=200)throw new java.io.IOException("HTTP "+code);
            try(java.io.InputStream input=connection.getInputStream()){
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>262144)throw new java.io.IOException("Response too large");bytes.write(buffer,0,count);}
                JSONObject result=new JSONObject(bytes.toString("UTF-8"));
                String returned=result.optString("model","");
                return "请求："+model+"\n服务端返回："+(returned.isEmpty()?"未提供 model 字段":returned)+"\n返回名可能仍是别名，不能据此证明权重快照。";
            }
        } finally {connection.disconnect();}
    }
}
