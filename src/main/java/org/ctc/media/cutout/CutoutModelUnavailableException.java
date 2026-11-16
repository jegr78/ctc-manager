package org.ctc.media.cutout;

public class CutoutModelUnavailableException extends RuntimeException {

    public CutoutModelUnavailableException() {
        super("Cutout model not installed");
    }
}
