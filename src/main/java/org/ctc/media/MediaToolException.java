package org.ctc.media;

public class MediaToolException extends RuntimeException {

    public MediaToolException(String message) {
        super(message);
    }

    public MediaToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
