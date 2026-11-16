package org.ctc.media.job;

public class MediaJobAbandonedException extends RuntimeException {

    public MediaJobAbandonedException() {
        super("A backup restore replaced the data; start again.");
    }
}
