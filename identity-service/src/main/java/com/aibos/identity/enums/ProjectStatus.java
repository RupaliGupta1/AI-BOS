package com.aibos.identity.enums;

public enum ProjectStatus {
    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    PARTIAL;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == PARTIAL;
    }
}
