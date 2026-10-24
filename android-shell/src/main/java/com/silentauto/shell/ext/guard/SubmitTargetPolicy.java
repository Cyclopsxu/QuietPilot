package com.silentauto.shell.ext.guard;

import java.util.Locale;

/** Alternate search-entry buttons must never be treated as text-search submission. */
public final class SubmitTargetPolicy {
    private SubmitTargetPolicy() {}
    public static boolean alternateInput(String label) {
        String text=label.toLowerCase(Locale.ROOT);
        return text.contains("语音")||text.contains("voice")||text.contains("microphone")
                ||text.contains("拍照")||text.contains("扫码")||text.contains("camera");
    }
}
