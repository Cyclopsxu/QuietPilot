package com.silentauto.shell;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;

/** A real non-reading socket must not stall a task publication or another client. */
public final class RpcOutputRegression {
    static Class<?> type;
    static Constructor<?> constructor;
    static Method send,close;
    static Object client(Socket socket)throws Exception {return constructor.newInstance(socket);}
    public static void main(String[] args)throws Exception {
        type=Class.forName("com.silentauto.shell.RpcServer$Client");
        constructor=type.getDeclaredConstructor(Socket.class);constructor.setAccessible(true);
        send=type.getDeclaredMethod("send",JsonObject.class);send.setAccessible(true);
        close=type.getDeclaredMethod("close");close.setAccessible(true);
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
            Socket peer=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
            Socket accepted=server.accept()) {
            accepted.setSendBufferSize(1024);peer.setReceiveBufferSize(1024);
            Object slow=client(accepted);JsonObject big=new JsonObject();big.addProperty("data","a".repeat(131072));
            long started=System.nanoTime();
            for(int i=0;i<100;i++)send.invoke(slow,big);
            if((System.nanoTime()-started)/1_000_000>3000)throw new AssertionError("Publisher stalled");
            if(!accepted.isClosed())throw new AssertionError("Slow client was not disconnected");
            close.invoke(slow);close.invoke(slow);
        }
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
            Socket peer=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
            Socket accepted=server.accept()) {
            peer.setSoTimeout(2000);Object normal=client(accepted);
            Field statusOnly=type.getDeclaredField("statusOnly");statusOnly.setAccessible(true);statusOnly.set(normal,true);
            JsonObject sample=new JsonObject(),params=new JsonObject();params.addProperty("type","video.sample");sample.add("params",params);send.invoke(normal,sample);
            for(int i=1;i<=3;i++){JsonObject response=new JsonObject();response.addProperty("id",i);send.invoke(normal,response);}
            BufferedReader reader=new BufferedReader(new InputStreamReader(peer.getInputStream()));
            for(int i=1;i<=3;i++)if(JsonParser.parseString(reader.readLine()).getAsJsonObject().get("id").getAsInt()!=i)throw new AssertionError("FIFO or status filter failed");
            close.invoke(normal);
        }
        System.out.println("PASS non-reading RPC peer bounded disconnect, independent healthy peer, FIFO, status-only filter and repeat close");
    }
}
