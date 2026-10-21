// Modified by Codex for ShadowAuto: copy live image rows into owned memory before bitmap conversion. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

final class ScreenCapture {
    private ScreenCapture() {
    }

    static Bitmap latestBitmap(VirtualDisplaySession display, int retries, long delayMs) {
        return display.captureBitmap(retries, delayMs);
    }

    static Bitmap bitmapFromImage(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowBytes = Math.multiplyExact(width, 4);
        if (pixelStride != 4 || rowStride < rowBytes)
            throw new IllegalArgumentException("Unsupported RGBA image stride");
        byte[] pixels = new byte[Math.multiplyExact(rowBytes, height)];
        for (int y = 0; y < height; y++) {
            int offset = Math.multiplyExact(y, rowStride);
            if (offset > buffer.limit() - rowBytes) throw new IllegalArgumentException("Truncated RGBA image row");
            buffer.position(offset);
            buffer.get(pixels, y * rowBytes, rowBytes);
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }
}
