package com.silentauto.shell.ext;

public enum TaskStatus {
    QUEUED, RUNNING, PAUSED, WAITING_USER, VERIFIED, ANSWERED, NOT_FOUND, FAILED, CANCELLED, HANDOFF_TIMEOUT;

    public boolean terminal() {
        return this == VERIFIED || this == ANSWERED || this == NOT_FOUND || this == FAILED || this == CANCELLED || this == HANDOFF_TIMEOUT;
    }
}
