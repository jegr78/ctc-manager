package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentRulingRevision;

public final class IncidentRulingProjection {
    private IncidentRulingProjection() { }
    public static String render(UUID reportId, String caseReference, String text) {
        if (reportId == null || caseReference == null || caseReference.isBlank() || caseReference.length() > 64) {
            throw new BusinessRuleException("The permanent ruling case reference could not be verified");
        }
        return "Ruling " + caseReference + "\n\n" + IncidentRulingRevision.acceptedText(text) + "\n[CTC " + reportId + " RULING]";
    }
}
