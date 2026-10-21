package com.silentauto.controller.ext.overlay;

import android.media.*;
import java.io.*;
import java.nio.*;

public final class WavRecorder {
    private AudioRecord audio;
    private Thread reader;
    private volatile boolean running;
    private final ByteArrayOutputStream pcm=new ByteArrayOutputStream();
    public volatile float level;
    @android.annotation.SuppressLint("MissingPermission")
    public void start(){
        int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(minimum<=0)throw new IllegalStateException("设备不支持 16kHz 录音");
        audio=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum*2,8192));
        if(audio.getState()!=AudioRecord.STATE_INITIALIZED){audio.release();audio=null;throw new IllegalStateException("麦克风初始化失败");}
        audio.startRecording();
        if(audio.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){audio.release();audio=null;throw new IllegalStateException("麦克风未开始录音");}
        running=true;
        reader=new Thread(()->{byte[] buffer=new byte[4096];while(running&&pcm.size()<960000){int count=audio.read(buffer,0,buffer.length);if(count<=0)break;
            pcm.write(buffer,0,Math.min(count,960000-pcm.size()));long sum=0;for(int i=0;i+1<count;i+=2)sum+=Math.abs((short)((buffer[i]&255)|(buffer[i+1]<<8)));level=Math.min(1,sum/(float)Math.max(1,count/2)/10000);
        }},"voice-pcm");reader.start();
    }
    public byte[] stop()throws Exception{
        running=false;if(audio!=null){try{audio.stop();}catch(IllegalStateException ignored){}}
        if(reader!=null)reader.join(1000);if(audio!=null){audio.release();audio=null;}
        byte[] data=pcm.toByteArray();ByteBuffer wav=ByteBuffer.allocate(44+data.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data.length+36).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data.length).put(data);return wav.array();
    }
}
