package org.ctc.discord.incidents;

import java.util.ArrayList;
import java.util.List;
import org.ctc.discord.picks.PickGatewayModal;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentPayload;

final class IncidentInteractionViews {
    private IncidentInteractionViews() { }
    static PickGatewayModal form(String id) { return form(id, null); }
    static PickGatewayModal form(String id, IncidentPayload saved) {
        boolean longDescription = saved != null && tooLong(saved.getDescription(), 4000);
        boolean longLinks = hasLongFootage(saved);
        return new PickGatewayModal(id, "Incident report", List.of(
                new PickGatewayModal.Text("drivers", "Involved drivers", true, saved == null ? null : saved.getInvolvedDrivers(), 1, 2000, true),
                new PickGatewayModal.Text("replay", "Replay timestamp", false, saved == null ? null : saved.getReplayTimestamp(), 1, 200, true),
                new PickGatewayModal.Text("description", longDescription ? "Description (blank keeps saved)" : "Description", true,
                        saved == null || longDescription ? null : saved.getDescription(), longDescription ? 0 : 1, 4000, !longDescription),
                new PickGatewayModal.Text("links", longLinks ? "Footage/links (blank keeps saved)" : "Additional footage / links", true,
                        saved == null || longLinks ? null : saved.getEvidenceLinks(), 0, 4000, false)));
    }
    static IncidentPayload payload(java.util.Map<String, List<String>> fields) { return payload(fields, null); }
    static IncidentPayload payload(java.util.Map<String, List<String>> fields, IncidentPayload saved) {
        var allowed = java.util.Set.of("drivers", "replay", "description", "links");
        boolean longDescription = saved != null && tooLong(saved.getDescription(), 4000);
        var required = longDescription ? java.util.Set.of("drivers", "replay") : java.util.Set.of("drivers", "replay", "description");
        if (!fields.keySet().containsAll(required) || !allowed.containsAll(fields.keySet())) throw invalid();
        return new IncidentPayload(field(fields, "drivers", 2000, true, null), field(fields, "replay", 200, true, null),
                field(fields, "description", 4000, true, saved == null ? null : saved.getDescription()),
                field(fields, "links", 4000, false, saved == null ? null : saved.getEvidenceLinks()));
    }
    private static String field(java.util.Map<String, List<String>> fields, String key, int maximum, boolean required, String saved) {
        var values = fields.getOrDefault(key, List.of(""));
        if (values == null || values.size() != 1) throw invalid();
        String value = values.getFirst();
        if (value == null || value.length() > maximum) throw invalid();
        if (value.isEmpty() && tooLong(saved, maximum)) return saved;
        if (required && value.isBlank()) throw invalid();
        return value;
    }
    static boolean hasLongText(IncidentPayload saved) {
        return saved != null && (tooLong(saved.getDescription(), 4000) || hasLongFootage(saved));
    }
    static boolean hasLongFootage(IncidentPayload saved) { return saved != null && tooLong(saved.getEvidenceLinks(), 4000); }
    private static boolean tooLong(String value, int maximum) { return value != null && value.length() > maximum; }
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
