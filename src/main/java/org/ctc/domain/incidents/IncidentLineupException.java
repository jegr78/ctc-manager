package org.ctc.domain.incidents;

import org.ctc.domain.exception.BusinessRuleException;

public final class IncidentLineupException extends BusinessRuleException {
    public IncidentLineupException(String message) { super(message); }
}
