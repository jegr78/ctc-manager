package org.ctc.discord.incidents;

import java.util.ArrayList;
import java.util.List;
import org.ctc.discord.picks.PickGatewayModal;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentPayload;

final class IncidentInteractionViews {
    private IncidentInteractionViews() { }
    static PickGatewayModal form(String id) {
        return new PickGatewayModal(id, "Incident report", List.of(
                new PickGatewayModal.Text("drivers", "Involved drivers", true, null, 1, 2000, true),
                new PickGatewayModal.Text("replay", "Replay timestamp", false, null, 1, 200, true),
                new PickGatewayModal.Text("description", "Description", true, null, 1, 4000, true),
                new PickGatewayModal.Text("links", "Additional footage / links", true, null, 0, 4000, false)));
    }
    static IncidentPayload payload(java.util.Map<String, List<String>> fields) {
        var allowed = java.util.Set.of("drivers", "replay", "description", "links");
        if (!fields.keySet().containsAll(java.util.Set.of("drivers", "replay", "description")) || !allowed.containsAll(fields.keySet())) throw invalid();
        return new IncidentPayload(field(fields, "drivers", 2000, true), field(fields, "replay", 200, true),
                field(fields, "description", 4000, true), field(fields, "links", 4000, false));
    }
    private static String field(java.util.Map<String, List<String>> fields, String key, int maximum, boolean required) {
        var values = fields.getOrDefault(key, List.of(""));
        if (values == null || values.size() != 1) throw invalid();
        String value = values.getFirst();
        if (value == null || value.length() > maximum || required && value.isBlank()) throw invalid();
        return value;
    }
    static String body(IncidentPayload payload) {
        return "Involved drivers: " + payload.getInvolvedDrivers() + "\nReplay timestamp: " + payload.getReplayTimestamp()
                + "\nDescription:\n" + payload.getDescription() + "\nAdditional footage / links:\n" + payload.getEvidenceLinks();
    }
    static List<String> pages(String text) {
        String escaped = escape(text); var pages = new ArrayList<String>(); int start = 0;
        while (start < escaped.length()) {
            int end = Math.min(start + 1700, escaped.length());
            if (end < escaped.length() && Character.isHighSurrogate(escaped.charAt(end - 1))) end--;
            pages.add(escaped.substring(start, end)); start = end;
        }
        return pages.isEmpty() ? List.of("") : List.copyOf(pages);
    }
    static String escape(String value) { return value.replace("\\", "\\\\").replace("`", "\\`").replace("*", "\\*").replace("_", "\\_").replace("@", "@\u200b"); }
    static String option(String value) {
        String escaped = escape(value); if (escaped.length() <= 100) return escaped;
        int end = Character.isHighSurrogate(escaped.charAt(95)) ? 95 : 96;
        return escaped.substring(0, end) + "...";
    }
    private static BusinessRuleException invalid() { return new BusinessRuleException("Check the required incident fields and their lengths"); }
}
