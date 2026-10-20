package com.silentauto.shell.ext;

public enum TaskStatus {
    QUEUED, RUNNING, PAUSED, WAITING_USER, VERIFIED, FAILED, CANCELLED, HANDOFF_TIMEOUT;

    public boolean terminal() {
        return this == VERIFIED || this == FAILED || this == CANCELLED || this == HANDOFF_TIMEOUT;
    }
}
