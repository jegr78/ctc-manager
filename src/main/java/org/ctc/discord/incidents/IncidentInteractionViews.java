package org.ctc.discord.incidents;

import java.util.ArrayList;
import java.util.List;
import org.ctc.discord.picks.PickGatewayModal;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentPayload;

final class IncidentInteractionViews {
    private IncidentInteractionViews() { }
    static PickGatewayModal form(String id, IncidentPayload saved, org.ctc.domain.incidents.IncidentDriverChoices choices,
                                 java.util.Set<java.util.UUID> selected) {
        boolean longDescription = saved != null && tooLong(saved.getDescription(), 4000);
        boolean longLinks = hasLongFootage(saved);
        var fields = new ArrayList<PickGatewayModal.Field>();
        if (choices.drivers().size() <= 25) {
            fields.add(driverMenu("drivers", "Involved drivers", choices.drivers(), selected, 1));
        } else {
            fields.add(driverMenu("drivers-home", "Involved home drivers", choices.drivers().stream().filter(org.ctc.domain.incidents.IncidentDriverChoices.DriverChoice::home).toList(), selected, 0));
            fields.add(driverMenu("drivers-away", "Involved away drivers", choices.drivers().stream().filter(driver -> !driver.home()).toList(), selected, 0));
        }
        fields.add(new PickGatewayModal.Text("replay", "Replay timestamp, e.g. 00:13.500", false, saved == null ? null : saved.getReplayTimestamp(), 1, 200, true));
        fields.add(new PickGatewayModal.Text("description", longDescription ? "Description (blank keeps saved)" : "Description", true,
                saved == null || longDescription ? null : saved.getDescription(), longDescription ? 0 : 1, 4000, !longDescription));
        fields.add(new PickGatewayModal.Text("links", longLinks ? "Footage/links (blank keeps saved)" : "Additional footage / links", true,
                saved == null || longLinks ? null : saved.getEvidenceLinks(), 0, 4000, false));
        return new PickGatewayModal(id, "Incident report", fields);
    }
    private static PickGatewayModal.Selection driverMenu(String id, String label,
            List<org.ctc.domain.incidents.IncidentDriverChoices.DriverChoice> choices, java.util.Set<java.util.UUID> selected, int minimum) {
        return new PickGatewayModal.Selection(id, label, choices.stream().map(driver ->
                new PickGatewayModal.Option(option(driver.label()), driver.id().toString(), selected.contains(driver.id()))).toList(), minimum, choices.size());
    }
    static IncidentPayload retainDraft(java.util.Map<String, List<String>> fields, IncidentPayload saved) {
        var normalized = new java.util.HashMap<String, List<String>>();
        for (String key : java.util.List.of("replay", "description", "links")) if (fields.containsKey(key)) normalized.put(key, fields.get(key));
        normalized.put("drivers", List.of(saved == null ? "Unconfirmed driver selection" : saved.getInvolvedDrivers()));
        return payload(normalized, saved);
    }
    static Submitted submitted(java.util.Map<String, List<String>> fields, IncidentPayload saved,
                               org.ctc.domain.incidents.IncidentDriverChoices choices) {
        var keys = choices.drivers().size() <= 25 ? java.util.Set.of("drivers") : java.util.Set.of("drivers-home", "drivers-away");
        var allowed = new java.util.HashSet<>(java.util.Set.of("replay", "description", "links"));allowed.addAll(keys);
        if (!allowed.containsAll(fields.keySet())) throw invalid();
        var selected = new ArrayList<java.util.UUID>();
        for (String key : keys) {
            for (String value : fields.getOrDefault(key, List.of())) {
                final java.util.UUID id;
                try { id = java.util.UUID.fromString(value); }
                catch (IllegalArgumentException invalidId) { throw invalid(); }
                var driver = choices.drivers().stream().filter(choice -> choice.id().equals(id)).findFirst().orElseThrow(IncidentInteractionViews::invalid);
                if (key.equals("drivers-home") && !driver.home() || key.equals("drivers-away") && driver.home()) throw invalid();
                selected.add(id);
            }
        }
        String drivers = choices.selectedText(selected);
        var normalized = new java.util.HashMap<>(fields);keys.forEach(normalized::remove);normalized.put("drivers", List.of(drivers));
        return new Submitted(payload(normalized, saved), selected);
    }
    record Submitted(IncidentPayload payload, List<java.util.UUID> selected) {
        Submitted { selected = List.copyOf(selected); }
        @Override public String toString() { return "SubmittedIncidentFields[private]"; }
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
