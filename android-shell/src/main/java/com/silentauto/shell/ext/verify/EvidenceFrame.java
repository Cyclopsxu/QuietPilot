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
    public String dataUrl() { return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP); }
}
