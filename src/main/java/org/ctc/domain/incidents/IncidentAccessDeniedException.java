package org.ctc.domain.incidents;

import org.ctc.domain.exception.BusinessRuleException;

public final class IncidentAccessDeniedException extends BusinessRuleException {
    public IncidentAccessDeniedException(Reason reason) { super(reason.message); }
    public enum Reason {
        UNAVAILABLE("Incident reporting is unavailable in this channel for this reporter"),
        NO_ROLE("Incident reporting is unavailable: no current participant role matches. Ask management to review your match roles."),
        OPPOSING_ROLES("Incident reporting is unavailable: your current roles match both opponents. Ask management to resolve the role setup."),
        SHARED_ROLE("Incident reporting is unavailable: the shared role maps to unrelated organizations. Ask management to correct the role setup.");
        private final String message;
        Reason(String message) { this.message = message; }
    }
}
