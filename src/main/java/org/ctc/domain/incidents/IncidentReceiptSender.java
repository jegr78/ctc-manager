package org.ctc.domain.incidents;

@FunctionalInterface
public interface IncidentReceiptSender { IncidentSendResult send(IncidentDeliveryClaim claim); }
