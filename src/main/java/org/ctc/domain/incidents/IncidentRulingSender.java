package org.ctc.domain.incidents;

public interface IncidentRulingSender {
    IncidentSendResult send(IncidentRulingClaim claim);
    IncidentSendResult verify(IncidentRulingClaim claim, String remoteId);
}
