package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentDelivery;
import org.ctc.domain.model.IncidentDeliveryOperation;
import org.ctc.domain.model.IncidentReportState;
import org.ctc.domain.model.IncidentWindowState;

public final class IncidentThreadProjection {
    private IncidentThreadProjection() { }

    public static Payload capture(IncidentDelivery operation) {
        if(operation==null || operation.getOperation()!=IncidentDeliveryOperation.DISCLOSURE)throw unavailable();
        var report=operation.getReport();var revision=operation.getRevision();
        if(report==null || revision==null || report.getState()!=IncidentReportState.ACTIVE
                || report.getWindow().getState()!=IncidentWindowState.CLOSED || report.getFrozenRevisionNumber()==null
                || report.getFrozenRevisionNumber()!=revision.getRevisionNumber()
                || !report.getId().equals(revision.getReport().getId()) || operation.getPartyLabel()==null || operation.getPartyLabel().isBlank())throw unavailable();
        var facts=revision.getPayload();if(facts==null)throw unavailable();
        String label=validText(operation.getPartyLabel()).replace('\r',' ').replace('\n',' ');
        String title=bounded("Incident "+operation.getCaseReference()+" · "+label,100);
        String body=validText("Incident "+operation.getCaseReference()+"\nReporting party: "+escapedLabel(label)
                +"\n\nInvolved drivers\n"+facts.getInvolvedDrivers()+"\n\nReplay timestamp\n"+facts.getReplayTimestamp()
                +"\n\nDescription\n"+facts.getDescription()+"\n\nEvidence references\n"+facts.getEvidenceLinks());
        var messages=new ArrayList<String>();
        for(int start=0;start<body.length();) {int end=boundary(body,start,1700);messages.add(body.substring(start,end));start=end;}
        return new Payload(title,messages);
    }
    private static String bounded(String text,int limit) {return text.substring(0,boundary(text,0,limit));}
    private static int boundary(String text,int start,int limit) {
        int end=Math.min(text.length(),start+limit);
        if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1)) && Character.isLowSurrogate(text.charAt(end)))end--;
        return end;
    }
    private static String escapedLabel(String label) {
        var escaped=new StringBuilder();
        for(char character:label.toCharArray()) {if("\\`*_~|><[]()#".indexOf(character)>=0)escaped.append('\\');escaped.append(character);}
        return escaped.toString();
    }
    private static String validText(String text) {
        var valid=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            char current=text.charAt(i);
            if(Character.isHighSurrogate(current)) {
                if(i+1<text.length() && Character.isLowSurrogate(text.charAt(i+1))) {valid.append(current).append(text.charAt(++i));}
                else valid.append('\uFFFD');
            } else if(Character.isLowSurrogate(current) || Character.isISOControl(current) && current!='\n' && current!='\r' && current!='\t')valid.append('\uFFFD');
            else valid.append(current);
        }
        return valid.toString();
    }
    private static BusinessRuleException unavailable() {return new BusinessRuleException("Incident disclosure requires its closed active frozen revision");}
    public record Payload(String title,List<String> messages) {
        public Payload {messages=List.copyOf(messages);}
        @Override public String toString(){return "IncidentThreadPayload[private]";}
    }
}
