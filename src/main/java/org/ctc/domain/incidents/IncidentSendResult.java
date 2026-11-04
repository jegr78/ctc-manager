package org.ctc.domain.incidents;

public record IncidentSendResult(Disposition disposition, String messageId, String code) {
    public enum Disposition { SENT, REJECTED, UNCERTAIN }
    @Override public String toString() { return "IncidentSendResult["+disposition+","+code+"]"; }
}
