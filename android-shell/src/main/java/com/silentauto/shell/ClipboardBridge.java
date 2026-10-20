// Modified by Codex for ShadowAuto: expose execution context bridge API. Original: ShadowAuto (Apache-2.0).
package com.silentauto.shell;

import android.content.ClipData;
import android.content.Context;

public final class ClipboardBridge {
    private final android.content.ClipboardManager manager;

    ClipboardBridge() {
        manager = (android.content.ClipboardManager) ShellContext.get().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("clipboard service unavailable");
        }
    }

    public String getText() {
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return "";
        }
        CharSequence text = clip.getItemAt(0).coerceToText(ShellContext.get());
        return text == null ? "" : text.toString();
    }

    public void setText(String text) {
        manager.setPrimaryClip(ClipData.newPlainText("ShadowAuto", text == null ? "" : text));
    }
}
