package org.ctc.media.job;

import org.ctc.domain.exception.BusinessRuleException;

public class MediaJobBusyException extends BusinessRuleException {

    public MediaJobBusyException(String message) {
        super(message);
    }
}
