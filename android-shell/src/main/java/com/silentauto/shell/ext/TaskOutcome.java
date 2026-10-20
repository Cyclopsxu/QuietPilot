package com.silentauto.shell.ext;

public final class TaskOutcome {
    public final String status;
    public final String message;
    public final String evidence;
    public final String boundary;

    public TaskOutcome(String status, String message, String evidence, String boundary) {
        this.status = status;
        this.message = message;
        this.evidence = evidence;
        this.boundary = boundary;
    }
}
