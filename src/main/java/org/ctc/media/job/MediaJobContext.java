package org.ctc.media.job;

public interface MediaJobContext {

    void progress(int step, int steps, String message);

    /** Throws {@link MediaJobAbandonedException} once a committed restore replaced the data the job read. */
    void checkNotAbandoned();
}
