package org.ctc.media.job;

public enum MediaJobState {
    QUEUED, RUNNING, DONE, FAILED, ABANDONED;

    public boolean finished() {
        return this == DONE || this == FAILED || this == ABANDONED;
    }
}
