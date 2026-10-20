package com.silentauto.shell.ext.guard;

import android.content.ClipData;
import android.os.SystemClock;
import com.silentauto.shell.ClipboardBridge;
import java.util.concurrent.Callable;

public final class ClipboardGuard {
    private static final Object LOCK = new Object();

    private ClipboardGuard() {}

    public static <T> T withTemporaryClip(ClipboardBridge cb, String text, Callable<T> body) throws Exception {
        return preservingClipboard(cb, () -> {
            cb.setText(text);
            return body.call();
        });
    }

    // Copy-selection also uses the same lock and restoration path.
    public static <T> T preservingClipboard(ClipboardBridge cb, Callable<T> body) throws Exception {
        synchronized (LOCK) {
            ClipData saved;
            try {
                saved = cb.getClip();
            } catch (RuntimeException unavailable) {
                // If even text cannot be read, fail before changing the user's clipboard.
                saved = ClipData.newPlainText("ShadowAuto backup", cb.getText());
            }
            try {
                return body.call();
            } finally {
                SystemClock.sleep(300);
                cb.restoreClip(saved);
                android.util.Log.i("ShadowAutoShell", "clipboard restored");
            }
        }
    }
}
