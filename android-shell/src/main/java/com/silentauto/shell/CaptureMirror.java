// Modified by Codex for ShadowAuto: capture a secondary layer stack without swapping its output surface. Apache-2.0.
package com.silentauto.shell;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Surface;


/** Owns only an insecure compositor mirror of this session's secondary display. */
final class CaptureMirror implements AutoCloseable {
    private final Object frames = new Object();
    private final ImageReader reader;
    private final HandlerThread drain = new HandlerThread("capture-mirror");
    private final Class<?> surfaceControl;
    private final IBinder token;
    private Image latest;
    private boolean closed;
    private long sequence;

    @SuppressLint("SoonBlockedPrivateApi") // Existing shell/app_process privileges; unavailable APIs fail closed.
    CaptureMirror(int displayId, int width, int height, ImageReader reader) throws Exception {
        if (displayId <= 0) throw new IllegalArgumentException("Secondary display required");
        Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object manager = global.getMethod("getInstance").invoke(null);
        Object info = global.getMethod("getDisplayInfo", int.class).invoke(manager, displayId);
        if (info == null) throw new IllegalStateException("Source display missing");
        int stack = info.getClass().getField("layerStack").getInt(info);
        if (stack <= 0) throw new IllegalStateException("Primary layer stack is forbidden");
        this.reader = reader;
        surfaceControl = Class.forName("android.view.SurfaceControl");
        token = (IBinder) surfaceControl.getMethod("createDisplay", String.class, boolean.class)
                .invoke(null, "ShadowAutoCapture-" + displayId, false);
        try {
            drain.start();
            reader.setOnImageAvailableListener(this::onFrame, new Handler(drain.getLooper()));
            surfaceControl.getMethod("openTransaction").invoke(null);
            try {
                surfaceControl.getMethod("setDisplaySurface", IBinder.class, Surface.class)
                        .invoke(null, token, reader.getSurface());
                surfaceControl.getMethod("setDisplayLayerStack", IBinder.class, int.class)
                        .invoke(null, token, stack);
                surfaceControl.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                        .invoke(null, token, 0, new Rect(0, 0, width, height), new Rect(0, 0, width, height));
            } finally {
                surfaceControl.getMethod("closeTransaction").invoke(null);
            }
        } catch (Exception failed) {
            close();
            throw failed;
        }
    }

    private void onFrame(ImageReader source) {
        Image image;
        try { image = source.acquireLatestImage(); }
        catch (IllegalStateException released) { return; }
        if (image == null) return;
        synchronized (frames) {
            if (closed) { image.close(); return; }
            if (latest != null) latest.close();
            latest = image;
            sequence++;
            frames.notifyAll();
        }
    }

    Bitmap capture(int retries, long delayMs) {
        synchronized (frames) {
            long began = sequence;
            for (int i = 0; i <= retries && !closed; i++) {
                // Wait briefly for a newer rendered frame; a static page can reuse its last frame.
                if (latest != null && (sequence > began || i > 0))
                    return ScreenCapture.bitmapFromImage(latest);
                try { frames.wait(Math.max(1, delayMs)); }
                catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); return null; }
            }
            return null;
        }
    }

    long frameCount() { synchronized (frames) { return sequence; } }

    @Override public void close() {
        synchronized (frames) {
            if (closed) return;
            closed = true;
            if (latest != null) { latest.close(); latest = null; }
            frames.notifyAll();
        }
        reader.setOnImageAvailableListener(null, null);
        try { surfaceControl.getMethod("destroyDisplay", IBinder.class).invoke(null, token); }
        catch (ReflectiveOperationException ignored) { }
        drain.quitSafely();
    }
}
