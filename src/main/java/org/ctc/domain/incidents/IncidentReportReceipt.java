package org.ctc.domain.incidents;

import java.time.Instant;
import java.util.UUID;
import org.ctc.domain.model.IncidentEventKind;

public record IncidentReportReceipt(UUID reportId, String caseReference, long version, int revisionNumber,
                                    IncidentEventKind action, Instant acceptedAt, UUID requestId) { }
