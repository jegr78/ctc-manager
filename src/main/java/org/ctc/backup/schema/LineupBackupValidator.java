package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.ctc.domain.model.LineupActorKind;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.model.LineupWindowKind;

/** Bounded prewipe checks for lineup submissions, their immutable revisions and leg window decisions. */
public final class LineupBackupValidator {
    private static final Set<String> TABLES = Set.of("races", "teams", "lineup_submissions", "lineup_submission_revisions", "lineup_window_events");
    private static final int BUDGET = 100_000;
    private final int version;
    private final Set<UUID> races = new HashSet<>();
    private final Set<UUID> teams = new HashSet<>();
    private final Map<UUID, Submission> submissions = new HashMap<>();
    private final Set<String> targets = new HashSet<>();
    private final Map<UUID, TreeSet<Integer>> revisionNumbers = new HashMap<>();
    private final Set<UUID> requests = new HashSet<>();
    private final Map<UUID, TreeSet<Integer>> windowSequences = new HashMap<>();
    private int retained;

    private record Submission(UUID race, UUID team, int latest, Integer applied) { }

    public LineupBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        if (!TABLES.contains(table)) return;
        if (table.startsWith("lineup_") && version < LineupArchive.LINEUP_VERSION) {
            throw new IllegalArgumentException("Lineup submissions require backup schema " + LineupArchive.LINEUP_VERSION);
        }
        UUID id = uuid(row, "id");
        switch (table) {
            case "races" -> races.add(id);
            case "teams" -> teams.add(id);
            case "lineup_submissions" -> submission(id, row);
            case "lineup_submission_revisions" -> revision(row);
            case "lineup_window_events" -> window(row);
            default -> throw new IllegalStateException("Unexpected lineup table");
        }
        if (++retained > BUDGET) throw new IllegalArgumentException("Lineup validation exceeds the " + BUDGET + "-reference budget");
    }

    public void validate() {
        for (var entry : submissions.entrySet()) {
            var submission = entry.getValue();
            if (!races.contains(submission.race()) || !teams.contains(submission.team())) {
                throw new IllegalArgumentException("A lineup submission references a missing race or team");
            }
            var numbers = revisionNumbers.getOrDefault(entry.getKey(), new TreeSet<>());
            if (numbers.size() != submission.latest() || numbers.first() != 1 || numbers.last() != submission.latest()) {
                throw new IllegalArgumentException("Lineup revisions must number 1 to the latest revision without gaps");
            }
        }
        for (var submission : revisionNumbers.keySet()) {
            if (!submissions.containsKey(submission)) throw new IllegalArgumentException("A lineup revision references a missing submission");
        }
        for (var entry : windowSequences.entrySet()) {
            if (!races.contains(entry.getKey())) throw new IllegalArgumentException("A lineup window decision references a missing race");
            if (entry.getValue().first() != 1 || entry.getValue().last() != entry.getValue().size()) {
                throw new IllegalArgumentException("Lineup window decisions must number 1 to n without gaps");
            }
        }
    }

    private void submission(UUID id, JsonNode row) {
        UUID race = uuid(row, "race");
        UUID team = uuid(row, "team");
        int latest = positive(row, "latestRevisionNumber");
        Integer applied = row.hasNonNull("appliedRevisionNumber") ? positive(row, "appliedRevisionNumber") : null;
        boolean appliedAt = row.hasNonNull("appliedAt");
        boolean appliedBy = row.hasNonNull("appliedBy");
        if ((applied == null) != !appliedAt || (applied == null) != !appliedBy || applied != null && applied > latest) {
            throw new IllegalArgumentException("A lineup submission has an inconsistent applied revision");
        }
        if (appliedAt) instant(row, "appliedAt");
        if (appliedBy) text(row, "appliedBy", 128);
        if (!row.path("version").isIntegralNumber() || row.get("version").longValue() < latest) {
            throw new IllegalArgumentException("A lineup submission has an invalid version");
        }
        if (submissions.put(id, new Submission(race, team, latest, applied)) != null || !targets.add(race + ":" + team)) {
            throw new IllegalArgumentException("A lineup submission is duplicated");
        }
    }

    private void revision(JsonNode row) {
        UUID submission = uuid(row, "submission");
        int number = positive(row, "revisionNumber");
        if (!revisionNumbers.computeIfAbsent(submission, key -> new TreeSet<>()).add(number)) {
            throw new IllegalArgumentException("A lineup revision number is duplicated");
        }
        if (!requests.add(uuid(row, "requestId"))) throw new IllegalArgumentException("A lineup request identity is duplicated");
        LineupActorKind kind;
        try {
            kind = LineupActorKind.valueOf(row.path("actorKind").asText());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("A lineup revision has an invalid actor kind");
        }
        text(row, "actorId", 128);
        boolean reason = row.hasNonNull("reason");
        if (reason) text(row, "reason", 500);
        if (reason != (kind == LineupActorKind.MANAGEMENT)) throw new IllegalArgumentException("Only management corrections carry a reason");
        instant(row, "submittedAt");
        if (!row.path("aggregateVersion").isIntegralNumber()) throw new IllegalArgumentException("A lineup revision has an invalid version");
        var payload = row.get("payload");
        if (payload == null || !payload.isTextual() || payload.textValue().length() > 4000) throw new IllegalArgumentException("A lineup revision has an invalid payload");
        LineupPayload.fromJson(payload.textValue());
    }

    private void window(JsonNode row) {
        UUID race = uuid(row, "race");
        int sequence = positive(row, "sequenceNumber");
        if (!windowSequences.computeIfAbsent(race, key -> new TreeSet<>()).add(sequence)) {
            throw new IllegalArgumentException("A lineup window decision is duplicated");
        }
        try {
            LineupWindowKind.valueOf(row.path("kind").asText());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("A lineup window decision has an invalid kind");
        }
        text(row, "reason", 500);
        text(row, "actorId", 128);
        instant(row, "occurredAt");
    }

    private static UUID uuid(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Lineup backup field " + field + " is missing");
        try {
            return UUID.fromString(value.textValue());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Lineup backup field " + field + " is not a UUID");
        }
    }

    private static int positive(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isIntegralNumber() || value.intValue() < 1) throw new IllegalArgumentException("Lineup backup field " + field + " must be positive");
        return value.intValue();
    }

    private static void text(JsonNode row, String field, int max) {
        var value = row.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) {
            throw new IllegalArgumentException("Lineup backup field " + field + " is invalid");
        }
    }

    private static void instant(JsonNode row, String field) {
        try {
            Instant.parse(row.path(field).asText());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Lineup backup field " + field + " is not an instant");
        }
    }
}
