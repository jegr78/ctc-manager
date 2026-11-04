package org.ctc.domain.incidents;

@FunctionalInterface
public interface IncidentReceiptSender {
    IncidentSendResult send(IncidentDeliveryClaim claim);
    default IncidentSendResult verify(IncidentDeliveryClaim claim,String messageId) { return new IncidentSendResult(IncidentSendResult.Disposition.REJECTED,null,"VERIFY_UNAVAILABLE"); }
}
