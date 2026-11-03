package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

public final class IncidentBackupValidator {
    private static final int MAX_VALUES = 100000;
    private static final Set<String> INCIDENT_TABLES = Set.of("incident_match_contexts", "incident_report_windows", "incident_reports", "incident_report_revisions", "incident_report_events");
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("seasons", Set.of("id")), Map.entry("teams", Set.of("id")),
            Map.entry("season_phases", Set.of("id", "season")), Map.entry("matchdays", Set.of("id", "phase")),
            Map.entry("playoffs", Set.of("id", "phase")), Map.entry("playoff_rounds", Set.of("id", "playoff")),
            Map.entry("matches", Set.of("id", "matchday", "homeTeam", "awayTeam", "bye", "discordChannelId")),
            Map.entry("playoff_matchups", Set.of("id", "round", "team1", "team2", "bye")),
            Map.entry("races", Set.of("id", "matchday", "match", "playoffMatchup", "homeTeamOverride", "awayTeamOverride")),
            Map.entry("incident_match_contexts", Set.of("id", "match", "playoffMatchup", "season", "homeTeam", "awayTeam", "guildId", "channelId", "version", "boundBy", "boundAt", "createdAt", "updatedAt")),
            Map.entry("incident_report_windows", Set.of("id", "context", "version", "state", "plannedStartLocal", "plannedStart", "deadline", "openedAt", "openedBy", "closedAt", "createdAt", "updatedAt")),
            Map.entry("incident_reports", Set.of("id", "window", "race", "originalMatchday", "originalHomeTeam", "originalAwayTeam", "caseReference", "authorId", "party", "state", "version", "latestRevisionNumber", "frozenRevisionNumber", "createdAt", "updatedAt")),
            Map.entry("incident_report_revisions", Set.of("id", "report", "revisionNumber", "reportVersion", "payload", "actorId", "acceptedAt", "requestId", "createdAt", "updatedAt")),
            Map.entry("incident_report_events", Set.of("id", "window", "report", "eventKey", "kind", "windowVersion", "reportVersion", "revisionNumber", "actorKind", "actorId", "reason", "acceptedAt", "previousDeadline", "deadline", "requestId", "createdAt", "updatedAt")));
    private final int version;
    private final Map<String, Map<String, JsonNode>> rows = new HashMap<>();
    private int retained;
    private String subject = "Permanent incident history";

    public IncidentBackupValidator(int version) { this.version = version; }
    public static boolean supports(String table) { return FIELDS.containsKey(table); }
    public String subject() { return subject; }
    private void locate(String table, JsonNode row) {
        subject = table;
        if (row != null && row.path("id").isTextual()) {
            try { subject = table + " " + UUID.fromString(row.path("id").textValue()); }
            catch (IllegalArgumentException invalid) { subject = table + " with an invalid identity"; }
        }
    }

    public void accept(String table, JsonNode row) {
        if (!supports(table)) return;
        locate(table, row);
        boolean incident = INCIDENT_TABLES.contains(table);
        if (version < 8) {
            if (incident) fail("Incident history requires schema version 8");
            return;
        }
        if (row == null || !row.isObject()) fail("An incident reference row must be an object");
        if (incident) {
            row.fieldNames().forEachRemaining(field -> { if (!FIELDS.get(table).contains(field)) fail("Unexpected incident history field"); });
            for (String field : FIELDS.get(table)) if (!row.has(field)) fail("Missing incident history field " + field);
            for (String audit : List.of("createdAt", "updatedAt")) if (row.hasNonNull(audit)) local(row, audit);
        }
        var projected = JsonNodeFactory.instance.objectNode();
        for (String field : FIELDS.get(table)) {
            var value = row.get(field);
            if (value == null) continue;
            if (field.equals("payload")) { validatePayload(value); continue; }
            if (field.equals("party")) {
                if (!value.isObject() || value.size() != 4) fail("Invalid incident reporting-party snapshot");
                for (String key : List.of("type", "id", "label", "roleId")) if (!value.has(key)) fail("Incomplete incident reporting-party snapshot");
                text(value, "type", 16); uuid(value, "id"); text(value, "label", 255); snowflake(value, "roleId");
                retained += value.size();
            } else if (!value.isNull() && !(value.isBoolean() || value.isIntegralNumber()
                    || value.isTextual() && value.textValue().length() <= 1000)) {
                fail("Invalid incident retained scalar " + field);
            }
            projected.set(field, value.deepCopy());
        }
        retained += projected.size();
        if (retained > MAX_VALUES) fail("Incident validation exceeds the 100000-reference budget");
        if (index(table).putIfAbsent(uuid(row, "id"), projected) != null) fail("Duplicate incident reference identity in " + table);
    }

    public void validate() {
        if (version < 8 || INCIDENT_TABLES.stream().allMatch(table -> index(table).isEmpty())) return;
        validateContexts();
        var windowEvents = new HashMap<String, SortedMap<Long, JsonNode>>();
        var reportEvents = new HashMap<String, SortedMap<Long, JsonNode>>();
        var keys = new HashSet<String>(); var requests = new HashSet<String>();
        for (var event : index("incident_report_events").values()) {
            locate("incident_report_events", event);
            String window = reference(event, "window", "incident_report_windows");
            long windowVersion = positive(event, "windowVersion"); String kind = text(event, "kind", 24);
            String key = text(event, "eventKey", 120); if (!keys.add(key)) fail("Duplicate incident event key");
            instant(event, "acceptedAt"); instant(event, "deadline"); text(event, "actorId", 128); optionalText(event, "reason", 1000);
            if (event.hasNonNull("report")) {
                String report = reference(event, "report", "incident_reports"); var owner = index("incident_reports").get(report);
                if (!uuid(owner, "window").equals(window) || !text(event, "actorKind", 16).equals("REPORTER")
                        || !text(event, "actorId", 128).equals(text(owner, "authorId", 32))) fail("Incident event ownership contradicts its original report");
                if (!Set.of("SUBMITTED", "EDITED", "WITHDRAWN").contains(kind)) fail("Invalid incident report decision");
                long reportVersion = positive(event, "reportVersion"); positive(event, "revisionNumber");
                if (!key.equals("report:" + report + ":" + reportVersion) || !requests.add(uuid(event, "requestId"))) fail("Invalid incident event key or duplicate durable request");
                if (reportEvents.computeIfAbsent(report, ignored -> new TreeMap<>()).putIfAbsent(reportVersion, event) != null) fail("Duplicate incident report version");
            } else {
                if (!Set.of("OPENED", "DEADLINE_CHANGED", "CLOSED").contains(kind) || event.hasNonNull("requestId")
                        || event.hasNonNull("reportVersion") || event.hasNonNull("revisionNumber")) fail("Invalid incident window decision shape");
                String actor = kind.equals("CLOSED") ? "SYSTEM" : "MANAGEMENT";
                if (!actor.equals(text(event, "actorKind", 16)) || !key.equals("window:" + window + ":" + windowVersion)) fail("Invalid incident window decision provenance");
                text(event, "reason", 1000);
                if (windowEvents.computeIfAbsent(window, ignored -> new TreeMap<>()).putIfAbsent(windowVersion, event) != null) fail("Duplicate incident window version");
            }
        }
        var byReport = new HashMap<String, SortedMap<Long, JsonNode>>(); var revisionRequests = new HashSet<String>();
        for (var revision : index("incident_report_revisions").values()) {
            locate("incident_report_revisions", revision);
            String report = reference(revision, "report", "incident_reports"); long number = positive(revision, "revisionNumber");
            positive(revision, "reportVersion"); instant(revision, "acceptedAt"); snowflake(revision, "actorId");
            if (!revisionRequests.add(uuid(revision, "requestId"))) fail("Duplicate incident revision request");
            if (byReport.computeIfAbsent(report, ignored -> new TreeMap<>()).putIfAbsent(number, revision) != null) fail("Duplicate incident revision number");
        }
        validateWindows(windowEvents);
        var references = new HashSet<String>();
        for (var report : index("incident_reports").values()) {
            locate("incident_reports", report);
            String id = uuid(report, "id"); var window = index("incident_report_windows").get(reference(report, "window", "incident_report_windows"));
            var context = index("incident_match_contexts").get(reference(window, "context", "incident_match_contexts"));
            String caseReference = text(report, "caseReference", 64);
            if (!caseReference.startsWith("INC-") || !references.add(caseReference)) fail("Invalid or duplicate permanent incident reference");
            snowflake(report, "authorId"); validateReportTarget(report, context);
            var history = reportEvents.getOrDefault(id, new TreeMap<>()); var revisions = byReport.getOrDefault(id, new TreeMap<>());
            long expectedVersion = 0, latest = 0; boolean withdrawn = false; Instant previous = null;
            for (var entry : history.entrySet()) {
                var event = entry.getValue(); String kind = text(event, "kind", 24); Instant at = instant(event, "acceptedAt");
                if (entry.getKey() != ++expectedVersion || withdrawn || previous != null && at.isBefore(previous)) fail("Incident report history is not sequential");
                validateReportTime(event, windowEvents.get(uuid(window, "id")));
                if (kind.equals("WITHDRAWN")) {
                    if (latest == 0) fail("An unsubmitted incident cannot be withdrawn"); withdrawn = true;
                } else {
                    if (expectedVersion == 1 && !kind.equals("SUBMITTED") || expectedVersion > 1 && !kind.equals("EDITED")) fail("Incident revision action contradicts its history");
                    var revision = revisions.get(++latest);
                    if (revision == null || positive(revision, "reportVersion") != expectedVersion
                            || !uuid(revision, "requestId").equals(uuid(event, "requestId"))
                            || !text(revision, "actorId", 32).equals(text(event, "actorId", 128))
                            || !instant(revision, "acceptedAt").equals(at)) fail("Incident revision contradicts its immutable decision");
                }
                if (positive(event, "revisionNumber") != latest) fail("Incident decision points to a different revision");
                previous = at;
            }
            if (latest == 0 || revisions.size() != latest || positive(report, "latestRevisionNumber") != latest
                    || positive(report, "version") != expectedVersion || !text(report, "state", 16).equals(withdrawn ? "WITHDRAWN" : "ACTIVE")) {
                fail("Incident workflow pointers contradict immutable history");
            }
            boolean frozen = !withdrawn && text(window, "state", 16).equals("CLOSED");
            if (frozen ? !report.hasNonNull("frozenRevisionNumber") || positive(report, "frozenRevisionNumber") != latest
                    : report.hasNonNull("frozenRevisionNumber")) fail("Incident frozen revision contradicts the closed window");
        }
    }

    private void validateContexts() {
        var targets = new HashSet<String>(); var channels = new HashSet<String>();
        for (var context : index("incident_match_contexts").values()) {
            locate("incident_match_contexts", context);
            String target = contextReference(context);
            if (!targets.add(target) || !channels.add(snowflake(context, "guildId") + ":" + snowflake(context, "channelId"))) fail("Incident context target or channel is not unique");
            String season = reference(context, "season", "seasons"), home = reference(context, "homeTeam", "teams"), away = reference(context, "awayTeam", "teams");
            if (home.equals(away) || positive(context, "version") != 1) fail("Invalid incident binding identity or version");
            text(context, "boundBy", 128); instant(context, "boundAt");
            JsonNode pairing;
            if (context.hasNonNull("match")) {
                String match = reference(context, "match", "matches"); pairing = index("matches").get(match);
                if (!canonicalMatch(match).equals(target) || !Objects.equals(optionalText(pairing, "discordChannelId", 32), snowflake(context, "channelId"))) fail("Incident regular binding contradicts its canonical channel");
                if (!season.equals(seasonForDay(reference(pairing, "matchday", "matchdays")))
                        || !home.equals(reference(pairing, "homeTeam", "teams")) || !away.equals(reference(pairing, "awayTeam", "teams"))) fail("Incident context contradicts its match season or participants");
            } else {
                pairing = index("playoff_matchups").get(reference(context, "playoffMatchup", "playoff_matchups"));
                var round = index("playoff_rounds").get(reference(pairing, "round", "playoff_rounds"));
                var playoff = index("playoffs").get(reference(round, "playoff", "playoffs"));
                var phase = index("season_phases").get(reference(playoff, "phase", "season_phases"));
                if (!season.equals(reference(phase, "season", "seasons")) || !home.equals(reference(pairing, "team1", "teams"))
                        || !away.equals(reference(pairing, "team2", "teams"))) fail("Incident context contradicts its playoff season or participants");
            }
            for (var match : index("matches").values()) {
                if (Objects.equals(optionalText(match, "discordChannelId", 32), snowflake(context, "channelId"))
                        && !canonicalMatch(uuid(match, "id")).equals(target)) fail("The incident channel has ambiguous legacy match bindings");
            }
        }
    }

    private void validateWindows(Map<String, SortedMap<Long, JsonNode>> histories) {
        var contexts = new HashSet<String>();
        for (var window : index("incident_report_windows").values()) {
            locate("incident_report_windows", window);
            String context = reference(window, "context", "incident_match_contexts");
            if (!contexts.add(context)) fail("An incident context has multiple reporting windows");
            var history = histories.getOrDefault(uuid(window, "id"), new TreeMap<>());
            String state = text(window, "state", 16); long version = nonnegative(window, "version");
            if (state.equals("DRAFT")) {
                if (version != 0 || !history.isEmpty()) fail("Draft incident window has confirmed decisions");
                for (String field : List.of("deadline", "plannedStartLocal", "plannedStart", "openedAt", "openedBy", "closedAt")) if (window.hasNonNull(field)) fail("Draft incident window contains confirmed metadata");
                continue;
            }
            if (history.isEmpty()) fail("Incident window has no immutable opening decision");
            long expected = 0; Instant cutoff = null, previous = null; boolean closed = false;
            for (var entry : history.entrySet()) {
                var event = entry.getValue(); String kind = text(event, "kind", 24); Instant at = instant(event, "acceptedAt");
                if (entry.getKey() != ++expected || closed || previous != null && at.isBefore(previous)) fail("Incident window history is not sequential");
                Instant chosen = instant(event, "deadline");
                if (expected == 1) {
                    if (!kind.equals("OPENED") || event.hasNonNull("previousDeadline") || !at.isBefore(chosen)
                            || !at.equals(instant(window, "openedAt")) || !text(event, "actorId", 128).equals(text(window, "openedBy", 128))
                            || at.isBefore(instant(index("incident_match_contexts").get(context), "boundAt"))) fail("Invalid incident window opening evidence");
                } else if (kind.equals("DEADLINE_CHANGED")) {
                    if (!instant(event, "previousDeadline").equals(cutoff) || !at.isBefore(cutoff) || !at.isBefore(chosen)) fail("Incident deadline changed after cutoff or from an unproven value");
                } else if (kind.equals("CLOSED")) {
                    if (!instant(event, "previousDeadline").equals(cutoff) || !chosen.equals(cutoff) || at.isBefore(cutoff)
                            || !at.equals(instant(window, "closedAt"))) fail("Incident window closure contradicts its cutoff");
                    closed = true;
                } else fail("Incident window was reopened or has an invalid decision");
                cutoff = chosen; previous = at;
            }
            if (version != expected || !instant(window, "deadline").equals(cutoff) || !state.equals(closed ? "CLOSED" : "OPEN")
                    || !closed && window.hasNonNull("closedAt")) fail("Incident window state contradicts immutable decisions");
            if (window.hasNonNull("plannedStart")) {
                LocalDateTime local = local(window, "plannedStartLocal"); var offsets = ZoneId.of("Europe/Berlin").getRules().getValidOffsets(local);
                if (offsets.size() != 1 || !local.toInstant(offsets.getFirst()).equals(instant(window, "plannedStart"))) fail("Incident planned-start snapshot has an invalid Berlin offset");
            } else if (window.hasNonNull("plannedStartLocal")) {
                var offsets = ZoneId.of("Europe/Berlin").getRules().getValidOffsets(local(window, "plannedStartLocal"));
                if (offsets.size() == 1) fail("An unambiguous incident planned start lacks its confirmed instant");
            }
        }
        if (contexts.size() != index("incident_match_contexts").size()) fail("An incident context is missing its reporting window");
    }

    private void validateReportTime(JsonNode event, SortedMap<Long, JsonNode> history) {
        long version = positive(event, "windowVersion"); var opening = history == null ? null : history.get(version);
        if (opening == null || text(opening, "kind", 24).equals("CLOSED")) fail("Incident mutation references an unavailable reporting phase");
        Instant at = instant(event, "acceptedAt"), cutoff = instant(opening, "deadline");
        if (at.isBefore(instant(opening, "acceptedAt")) || !at.isBefore(cutoff) || !instant(event, "deadline").equals(cutoff)) fail("Incident mutation was accepted outside its confirmed reporting cutoff");
        var next = history.tailMap(version + 1);
        if (!next.isEmpty() && at.isAfter(instant(next.get(next.firstKey()), "acceptedAt"))) fail("Incident mutation uses a superseded reporting phase");
    }

    private void validateReportTarget(JsonNode report, JsonNode context) {
        var race = index("races").get(reference(report, "race", "races"));
        String day = reference(race, "matchday", "matchdays");
        if (!reference(report, "originalMatchday", "matchdays").equals(day) || !seasonForDay(day).equals(uuid(context, "season"))
                || !raceReference(race).equals(contextReference(context))) fail("Incident report belongs to a different pairing, day or season");
        var pair = race.hasNonNull("match") ? index("matches").get(reference(race, "match", "matches"))
                : index("playoff_matchups").get(reference(race, "playoffMatchup", "playoff_matchups"));
        boolean regular = race.hasNonNull("match");
        String home = race.hasNonNull("homeTeamOverride") ? reference(race, "homeTeamOverride", "teams") : reference(pair, regular ? "homeTeam" : "team1", "teams");
        String away = race.hasNonNull("awayTeamOverride") ? reference(race, "awayTeamOverride", "teams") : reference(pair, regular ? "awayTeam" : "team2", "teams");
        if (home.equals(away) || !reference(report, "originalHomeTeam", "teams").equals(home) || !reference(report, "originalAwayTeam", "teams").equals(away)
                || !Set.of(uuid(context, "homeTeam"), uuid(context, "awayTeam")).equals(Set.of(home, away))) fail("Incident report no longer identifies its original race participants");
        var party = report.get("party"); String type = text(party, "type", 16), id = reference(party, "id", "teams");
        if (!Set.of("TEAM", "ORGANIZATION").contains(type) || type.equals("TEAM") && !Set.of(home, away).contains(id)) fail("Incident reporting party does not match its frozen participants");
    }

    private String canonicalMatch(String matchId) {
        var associated = index("races").values().stream().filter(race -> Objects.equals(nullableUuid(race, "match"), matchId)).toList();
        var playoffs = associated.stream().map(race -> nullableUuid(race, "playoffMatchup")).filter(Objects::nonNull).distinct().toList();
        if (playoffs.isEmpty()) return "match:" + matchId;
        if (playoffs.size() != 1 || associated.stream().anyMatch(race -> !race.hasNonNull("playoffMatchup"))) fail("Incident legacy match has ambiguous canonical ownership");
        if (!index("playoff_matchups").containsKey(playoffs.getFirst())) fail("Incident canonical playoff reference is missing");
        return "playoff:" + playoffs.getFirst();
    }
    private String raceReference(JsonNode race) {
        if (race.hasNonNull("playoffMatchup")) return "playoff:" + reference(race, "playoffMatchup", "playoff_matchups");
        return "match:" + reference(race, "match", "matches");
    }
    private static String contextReference(JsonNode context) {
        if (context.hasNonNull("match") == context.hasNonNull("playoffMatchup")) fail("Incident context must have exactly one pairing target");
        return context.hasNonNull("match") ? "match:" + uuid(context, "match") : "playoff:" + uuid(context, "playoffMatchup");
    }
    private String seasonForDay(String id) {
        var day = index("matchdays").get(id); var phase = index("season_phases").get(reference(day, "phase", "season_phases"));
        return reference(phase, "season", "seasons");
    }
    private String reference(JsonNode row, String field, String table) {
        String id = uuid(row, field); if (!index(table).containsKey(id)) fail("Missing incident ownership reference " + field); return id;
    }
    private Map<String, JsonNode> index(String table) { return rows.computeIfAbsent(table, ignored -> new LinkedHashMap<>()); }
    private static String nullableUuid(JsonNode row, String field) { return row.hasNonNull(field) ? uuid(row, field) : null; }
    private static String uuid(JsonNode row, String field) {
        String value = text(row, field, 36);
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equalsIgnoreCase(value)) fail("Invalid incident reference " + field);
            return canonical;
        } catch (IllegalArgumentException invalid) { throw new ValidationException("Invalid incident reference " + field); }
    }
    private static String text(JsonNode row, String field, int maximum) {
        String value = optionalText(row, field, maximum); if (value == null || value.isBlank()) fail("Missing incident text " + field); return value;
    }
    private static String optionalText(JsonNode row, String field, int maximum) {
        var value = row == null ? null : row.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length() > maximum) fail("Invalid incident text " + field);
        return value.textValue();
    }
    private static String snowflake(JsonNode row, String field) {
        String value = text(row, field, 32); if (!value.matches("[0-9]{1,32}")) fail("Invalid incident Discord reference " + field); return value;
    }
    private static long nonnegative(JsonNode row, String field) {
        var value = row == null ? null : row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) fail("Invalid incident version " + field);
        return value.longValue();
    }
    private static long positive(JsonNode row, String field) { long value = nonnegative(row, field); if (value == 0) fail("Invalid incident pointer " + field); return value; }
    private static Instant instant(JsonNode row, String field) {
        try {
            var value = Instant.parse(text(row, field, 40)); int year = LocalDateTime.ofInstant(value, ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999 || value.getNano() % 1000 != 0) fail("Invalid incident timestamp " + field);
            return value;
        } catch (java.time.DateTimeException invalid) { throw new ValidationException("Invalid incident timestamp " + field); }
    }
    private static LocalDateTime local(JsonNode row, String field) {
        try {
            var value = LocalDateTime.parse(text(row, field, 40));
            if (value.getYear() < 1 || value.getYear() > 9999) fail("Invalid incident local timestamp " + field);
            return value;
        } catch (java.time.DateTimeException invalid) { throw new ValidationException("Invalid incident local timestamp " + field); }
    }
    private static void validatePayload(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.size() != 4) fail("Incomplete incident payload");
        for (String field : List.of("involvedDrivers", "replayTimestamp", "description", "evidenceLinks")) if (!payload.has(field)) fail("Incomplete incident payload");
        text(payload, "involvedDrivers", 2000); text(payload, "replayTimestamp", 200); text(payload, "description", 20000);
        if (optionalText(payload, "evidenceLinks", 8000) == null) fail("Missing incident evidence references");
    }
    private static void fail(String message) { throw new ValidationException(message); }
    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
}
