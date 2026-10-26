package com.silentauto.shell.ext.verify;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

public final class EvidenceFrame {
    public final byte[] png;
    public final boolean protectedScreen;
    public final long fingerprint;

    private EvidenceFrame(byte[] png, boolean protectedScreen, long fingerprint) {
        this.png = png; this.protectedScreen = protectedScreen; this.fingerprint = fingerprint;
    }
    public static EvidenceFrame from(Bitmap bitmap) {
        if (bitmap == null) return new EvidenceFrame(new byte[0], true, 0);
        try {
            int bright = 0;
            long hash = 1;
            // Downsample for stable observation; no UI events are injected.
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int pixel = bitmap.getPixel(x * bitmap.getWidth() / 32, y * bitmap.getHeight() / 32);
                int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
                if (r > 12 || g > 12 || b > 12) bright++;
                hash = 31 * hash + ((r / 8) * 1024 + (g / 8) * 32 + b / 8);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) throw new IllegalStateException("PNG compression failed");
            return new EvidenceFrame(bytes.toByteArray(), bright == 0, hash);
        } finally { bitmap.recycle(); }
    }
    /** A labeled-in-metadata inspection view; the original full PNG remains the saved evidence. */
    public EvidenceFrame messageInspection(String ocrJson,String message) {
        Bitmap original=null,view=null;
        try {
            com.google.gson.JsonArray rows=com.google.gson.JsonParser.parseString(ocrJson).getAsJsonObject().getAsJsonArray("results");
            int[] marker=com.silentauto.shell.ext.guard.VisualActionPolicy.messageMarker(rows,message);
            if(marker==null)return this;
            original=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length);if(original==null)return this;
            int w=original.getWidth(),h=original.getHeight(),header=Math.max(1,(int)(h*.07));
            int top=Math.max(header,marker[1]-h/10),bottom=Math.min(h,marker[1]+h/10);
            if(marker[0]<0||marker[0]>=w||bottom<=top||marker[1]<top||marker[1]>=bottom)return this;
            view=Bitmap.createBitmap(w,header+bottom-top,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(view);
            canvas.drawBitmap(original,new android.graphics.Rect(0,0,w,header),new android.graphics.Rect(0,0,w,header),null);
            canvas.drawBitmap(original,new android.graphics.Rect(0,top,w,bottom),new android.graphics.Rect(0,header,w,view.getHeight()),null);
            EvidenceFrame inspection=from(view);view=null;return inspection;
        } catch(RuntimeException unavailable){return this;}
        finally {if(original!=null)original.recycle();if(view!=null&&!view.isRecycled())view.recycle();}
    }

    public String dataUrl() { return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP); }
}
