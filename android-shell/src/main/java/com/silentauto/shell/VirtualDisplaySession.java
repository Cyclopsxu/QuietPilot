// Modified by Codex for ShadowAuto: preserve main focus and destroy virtual content on removal. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

final class VirtualDisplaySession {
    final int displayId;
    final int width;
    final int height;
    final int dpi;
    private final ImageReader captureReader;
    private final Surface captureSurface;
    private Surface outputSurface;
    private final VirtualDisplay display;
    private final WindowManagerBridge windowManager;
    private final int previousImePolicy;
    private final boolean localImeEnabled;
    private boolean released;
    private CaptureMirror captureMirror;
    private String captureFallback="";
    private long captureRequests,captureMisses,surfaceSwitches;
    private int selectedFlags,candidateIndex,ownFocusFlag,noStealFlag;
    private boolean localImeVerified;
    private com.google.gson.JsonArray creationAttempts;

    private VirtualDisplaySession(int width, int height, int dpi, ImageReader captureReader, Surface outputSurface, VirtualDisplay display) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.captureReader = captureReader;
        this.captureSurface = captureReader.getSurface();
        this.outputSurface = outputSurface;
        this.display = display;
        this.displayId = display.getDisplay().getDisplayId();
        this.windowManager = new WindowManagerBridge();
        this.previousImePolicy = windowManager.getDisplayImePolicy(displayId);
        this.localImeEnabled = windowManager.setDisplayImePolicy(displayId, WindowManagerBridge.DISPLAY_IME_POLICY_LOCAL);
        this.localImeVerified=localImeEnabled&&windowManager.getDisplayImePolicy(displayId)==WindowManagerBridge.DISPLAY_IME_POLICY_LOCAL;
        try { captureMirror = new CaptureMirror(displayId, width, height, captureReader); }
        catch (Exception unsupported) { captureFallback = unsupported.getClass().getSimpleName(); }
    }

    boolean localImeEnabled() {
        return localImeEnabled;
    }

    static VirtualDisplaySession create(int width, int height, int dpi, Surface outputSurface) {
        DisplayManager manager = displayManager();
        ImageReader captureReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        int baseFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | hiddenFlag("VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL");
        int touchFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH");
        int focusFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS");
        int trustedFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_TRUSTED");
        int noStealFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED");
        int[] candidates = new int[]{
                baseFlags | touchFlag | focusFlag | trustedFlag | noStealFlag,
                baseFlags | touchFlag | focusFlag | trustedFlag,
                baseFlags | touchFlag | focusFlag,
                baseFlags | touchFlag,
                baseFlags
        };
        Throwable lastError = null;
        com.google.gson.JsonArray attempts=new com.google.gson.JsonArray();
        for (int index=0;index<candidates.length;index++) {
            int flags=candidates[index];
            VirtualDisplay display=null;
            com.google.gson.JsonObject attempt=new com.google.gson.JsonObject();attempt.addProperty("candidateIndex",index);attempt.addProperty("flags",flags);attempts.add(attempt);
            try {
                display = manager.createVirtualDisplay("ShadowAuto", width, height, dpi, outputSurface, flags);
                if (display != null && display.getDisplay() != null) {
                    VirtualDisplaySession session=new VirtualDisplaySession(width,height,dpi,captureReader,outputSurface,display);
                    session.selectedFlags=flags;session.candidateIndex=index;session.ownFocusFlag=focusFlag;session.noStealFlag=noStealFlag;
                    attempt.addProperty("accepted",true);session.creationAttempts=attempts.deepCopy();
                    return session;
                }
                attempt.addProperty("accepted",false);
                if(display!=null)display.release();
            } catch (Throwable e) {
                attempt.addProperty("accepted",false);attempt.addProperty("error",e.getClass().getSimpleName());
                if(display!=null)try{display.release();}catch(Throwable ignored){}
                lastError = e;
            }
        }
        captureReader.close();
        throw new IllegalStateException("createVirtualDisplay failed", lastError);
    }

    com.google.gson.JsonObject isolation(boolean experimental) {
        com.google.gson.JsonObject report=com.silentauto.shell.ext.guard.DisplayIsolation.assess(selectedFlags,candidateIndex,ownFocusFlag,noStealFlag,localImeVerified,experimental);
        report.addProperty("touchNoFocusChangeFlag",InputBridge.noFocusChangeFlag());
        report.addProperty("touchNoFocusChangeAvailable",InputBridge.noFocusChangeFlag()!=0);
        report.add("capture",captureStats());
        report.add("creationAttempts",creationAttempts.deepCopy());report.addProperty("platformDisplayFlags",display.getDisplay().getFlags());
        report.addProperty("localImeSetSucceeded",localImeEnabled);report.addProperty("imePolicyBefore",previousImePolicy);report.addProperty("imePolicyReadback",windowManager.getDisplayImePolicy(displayId));
        report.addProperty("sdkInt",android.os.Build.VERSION.SDK_INT);report.addProperty("manufacturer",android.os.Build.MANUFACTURER);report.addProperty("model",android.os.Build.MODEL);return report;
    }

    synchronized com.google.gson.JsonObject captureStats() {
        com.google.gson.JsonObject stats = new com.google.gson.JsonObject();
        stats.addProperty("mode", captureMirror == null ? "legacy_surface_swap" : "layer_stack_mirror");
        stats.addProperty("fallback", captureFallback);
        stats.addProperty("requests", captureRequests);
        stats.addProperty("misses", captureMisses);
        stats.addProperty("surfaceSwitches", surfaceSwitches);
        stats.addProperty("frames", captureMirror == null ? 0 : captureMirror.frameCount());
        return stats;
    }

    synchronized Bitmap captureBitmap(int retries, long delayMs) {
        if (released) return null;
        captureRequests++;
        if (captureMirror != null) {
            Bitmap frame = captureMirror.capture(retries, delayMs);
            if (frame == null) captureMisses++;
            return frame; // Never switch the source surface after a mirror capture failure.
        }
        Surface restoreSurface = outputSurface;
        Image image = null;
        try {
            drainCaptureReader();
            display.setSurface(captureSurface);
            surfaceSwitches++;
            for (int i = 0; i <= retries; i++) {
                SystemClock.sleep(delayMs);
                image = captureReader.acquireLatestImage();
                if (image != null) {
                    return ScreenCapture.bitmapFromImage(image);
                }
            }
            captureMisses++;
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
            try {
                display.setSurface(restoreSurface);
                surfaceSwitches++;
                outputSurface = restoreSurface;
            } catch (Throwable ignored) {
            }
        }
    }

    private void drainCaptureReader() {
        while (true) {
            Image image = captureReader.acquireLatestImage();
            if (image == null) {
                return;
            }
            image.close();
        }
    }

    private static DisplayManager displayManager() {
        try {
            Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            return ctor.newInstance(ShellContext.get());
        } catch (Throwable ignored) {
            return (DisplayManager) ShellContext.get().getSystemService(Context.DISPLAY_SERVICE);
        }
    }

    synchronized void release() {
        if (released) return;
        released = true;
        if (captureMirror != null) captureMirror.close();
        try {
            if (previousImePolicy != -1) {
                windowManager.setDisplayImePolicy(displayId, previousImePolicy);
            }
        } catch (Throwable ignored) {
        }
        try {
            display.release();
        } catch (Throwable ignored) {
        }
        try {
            captureSurface.release();
        } catch (Throwable ignored) {
        }
        try {
            captureReader.close();
        } catch (Throwable ignored) {
        }
    }

    private static int hiddenFlag(String name) {
        try {
            Field field = DisplayManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
