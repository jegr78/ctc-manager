package org.ctc.backup.exception;

/**
 * The uploads directory cannot be replaced by the post-commit swap, so the import must not start.
 */
public class UploadsSwapPreflightException extends Exception {

    public UploadsSwapPreflightException(String message) {
        super(message);
    }

    public UploadsSwapPreflightException(String message, Throwable cause) {
        super(message, cause);
    }
}
