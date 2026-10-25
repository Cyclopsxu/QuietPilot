package com.silentauto.shell;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

/** A locked phone can replace the task display with SystemUI's keyguard presentation. */
final class DeviceLockState {
    private final PowerManager power = (PowerManager) ShellContext.get().getSystemService(Context.POWER_SERVICE);
    private final KeyguardManager keyguard = (KeyguardManager) ShellContext.get().getSystemService(Context.KEYGUARD_SERVICE);

    boolean blocked() {
        return !power.isInteractive() || keyguard.isKeyguardLocked();
    }
}
