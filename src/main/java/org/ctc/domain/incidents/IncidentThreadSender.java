package org.ctc.domain.incidents;

public interface IncidentThreadSender {
    IncidentThreadResult send(IncidentThreadClaim claim);
    IncidentThreadResult verify(IncidentThreadClaim claim, String remoteId);
}
