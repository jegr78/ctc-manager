package org.ctc.domain.stats;

import org.ctc.domain.exception.BusinessRuleException;

/** A user-facing reason why a stats query has no answer; its message is safe to show in Discord. */
public class StatsLookupException extends BusinessRuleException {
    public StatsLookupException(String message) { super(message); }
}
