package org.ctc.media.job;

@FunctionalInterface
public interface MediaJob {
    void run(MediaJobContext ctx) throws Exception;
}
