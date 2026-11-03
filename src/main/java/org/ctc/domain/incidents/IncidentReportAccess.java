package org.ctc.domain.incidents;

import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.IncidentParty;

@FunctionalInterface
public interface IncidentReportAccess {
    void requireCurrent(AuthorizedIncidentActor actor, IncidentMatchContext context, IncidentParty originalParty);
}
