package org.ctc.domain.incidents;

public record IncidentThreadResult(IncidentSendResult.Disposition disposition, String messageId, String threadId, String code) {
    @Override public String toString() {return "IncidentThreadResult["+disposition+","+code+"]";}
}
