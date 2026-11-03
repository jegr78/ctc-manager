package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IncidentBackupValidatorTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SEASON = id(), PHASE = id(), DAY = id(), HOME = id(), AWAY = id(), MATCH = id(), RACE = id(),
            CONTEXT = id(), WINDOW = id(), REPORT = id(), FIRST = id(), SECOND = id();
    final Map<String, List<ObjectNode>> data = new LinkedHashMap<>();

    @BeforeEach
    void fixture() {
        row("seasons", "id", SEASON); row("teams", "id", HOME); row("teams", "id", AWAY);
        row("season_phases", "id", PHASE, "season", SEASON); row("matchdays", "id", DAY, "phase", PHASE);
        row("matches", "id", MATCH, "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "200");
        row("races", "id", RACE, "matchday", DAY, "match", MATCH, "playoffMatchup", null, "homeTeamOverride", null, "awayTeamOverride", null);
        row("incident_match_contexts", "id", CONTEXT, "match", MATCH, "playoffMatchup", null, "season", SEASON,
                "homeTeam", HOME, "awayTeam", AWAY, "guildId", "100", "channelId", "200", "version", 1,
                "boundBy", "test-manager", "boundAt", "2026-11-03T11:00:00Z");
        row("incident_report_windows", "id", WINDOW, "context", CONTEXT, "version", 1, "state", "OPEN",
                "plannedStartLocal", "2026-11-02T20:00:00", "plannedStart", "2026-11-02T19:00:00Z",
                "deadline", "2026-11-03T13:00:00Z", "openedAt", "2026-11-03T11:01:00Z", "openedBy", "test-manager", "closedAt", null);
        row("incident_reports", "id", REPORT, "window", WINDOW, "race", RACE, "originalMatchday", DAY,
                "originalHomeTeam", HOME, "originalAwayTeam", AWAY, "caseReference", "INC-TEST", "authorId", "400",
                "party", JSON.createObjectNode().put("type", "TEAM").put("id", HOME).put("label", "Test home").put("roleId", "300"),
                "state", "ACTIVE", "version", 2, "latestRevisionNumber", 2, "frozenRevisionNumber", null);
        revision(1, 1, FIRST, "2026-11-03T12:00:00Z"); revision(2, 2, SECOND, "2026-11-03T12:01:00Z");
        event(null, "OPENED", 1, null, null, null, "2026-11-03T11:01:00Z", null);
        event(REPORT, "SUBMITTED", 1, 1L, 1, FIRST, "2026-11-03T12:00:00Z", null);
        event(REPORT, "EDITED", 1, 2L, 2, SECOND, "2026-11-03T12:01:00Z", null);
    }

    @Test
    void givenCompleteHistoryAndCurrentRoleChanges_whenValidating_thenKeepExactOriginalCaseAttribution() {
        data.get("teams").getFirst().put("discordRoleId", "999");
        assertThatCode(() -> validate(8)).doesNotThrowAnyException();
        assertThat(data.get("incident_reports").getFirst().path("party").path("roleId").asText()).isEqualTo("300");
    }

    @Test
    void givenWithdrawnCase_whenValidating_thenRetainItsLastRevisionAndWithdrawalWithoutExpiry() {
        var report = first("incident_reports"); report.put("state", "WITHDRAWN"); report.put("version", 3);
        event(REPORT, "WITHDRAWN", 1, 3L, 2, id(), "2026-11-03T12:02:00Z", null);
        assertThatCode(() -> validate(8)).doesNotThrowAnyException();
    }

    @Test
    void givenClosedWindowAndFrozenLatestRevision_whenValidating_thenPreserveClosureWithoutNewDeadline() {
        close(); assertThatCode(() -> validate(8)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"two-targets", "season", "participants", "shared-legacy-channel", "latest-pointer", "revision-gap",
            "duplicate-request", "foreign-race", "unrelated-party", "blank-payload", "late-revision", "author", "window-event",
            "frozen-pointer", "unknown-field", "missing-field"})
    void givenMalformedIncidentContract_whenValidating_thenRejectWithoutIncludingPrivatePayload(String corruption) {
        switch (corruption) {
            case "two-targets" -> first("incident_match_contexts").put("playoffMatchup", id());
            case "season" -> { String other = id(); row("seasons", "id", other); first("incident_match_contexts").put("season", other); }
            case "participants" -> first("incident_match_contexts").put("homeTeam", AWAY);
            case "shared-legacy-channel" -> row("matches", "id", id(), "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "200");
            case "latest-pointer" -> first("incident_reports").put("latestRevisionNumber", 99);
            case "revision-gap" -> data.get("incident_report_revisions").removeFirst();
            case "duplicate-request" -> data.get("incident_report_revisions").getLast().put("requestId", FIRST);
            case "foreign-race" -> {
                String match = id(), race = id(); row("matches", "id", match, "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "201");
                row("races", "id", race, "matchday", DAY, "match", match, "playoffMatchup", null, "homeTeamOverride", null, "awayTeamOverride", null);
                first("incident_reports").put("race", race);
            }
            case "unrelated-party" -> { String team = id(); row("teams", "id", team); ((ObjectNode) first("incident_reports").get("party")).put("id", team); }
            case "blank-payload" -> ((ObjectNode) first("incident_report_revisions").get("payload")).put("description", " ");
            case "late-revision" -> {
                data.get("incident_report_revisions").getLast().put("acceptedAt", "2026-11-03T13:00:00Z");
                data.get("incident_report_events").getLast().put("acceptedAt", "2026-11-03T13:00:00Z");
            }
            case "author" -> first("incident_report_revisions").put("actorId", "401");
            case "window-event" -> first("incident_report_events").put("deadline", "2026-11-03T14:00:00Z");
            case "frozen-pointer" -> { close(); first("incident_reports").put("frozenRevisionNumber", 1); }
            case "unknown-field" -> first("incident_reports").put("untrustedExtraField", "private payload must stay hidden");
            case "missing-field" -> first("incident_report_windows").remove("closedAt");
            default -> throw new IllegalArgumentException(corruption);
        }
        assertThatThrownBy(() -> validate(8)).isInstanceOf(IncidentBackupValidator.ValidationException.class)
                .hasMessageNotContaining("Test private incident facts").hasMessageNotContaining("private payload must stay hidden");
    }

    @Test
    void givenIncidentRowsDisguisedAsOlderSchema_whenValidating_thenRejectInsteadOfGrantingLegacyDefaults() {
        assertThatThrownBy(() -> validate(7)).isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageContaining("schema");
    }

    @Test
    void givenSupportedOldArchiveWithoutIncidentRows_whenValidating_thenKeepIncidentStateAbsent() {
        data.keySet().removeIf(table -> table.startsWith("incident_"));
        assertThatCode(() -> validate(7)).doesNotThrowAnyException();
    }

    @Test
    void givenReferenceBudgetExceeded_whenReading_thenRejectBeforeRetainingUnboundedArchiveMetadata() {
        var validator = new IncidentBackupValidator(8);
        assertThatThrownBy(() -> {
            for (int i = 0; i <= 100000; i++) validator.accept("teams", JSON.createObjectNode().put("id", id()));
        }).isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageContaining("100000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-races", "bye", "foreign-season", "participants"})
    void givenContextWithoutReports_whenItsReportableRaceIdentityIsInvalid_thenRejectBeforeRestore(String corruption) {
        data.get("incident_reports").clear(); data.get("incident_report_revisions").clear();
        data.get("incident_report_events").removeIf(event -> event.hasNonNull("report"));
        switch (corruption) {
            case "no-races" -> data.get("races").clear();
            case "bye" -> first("matches").put("bye", true);
            case "foreign-season" -> {
                String season = id(), phase = id(), day = id(); row("seasons", "id", season);
                row("season_phases", "id", phase, "season", season); row("matchdays", "id", day, "phase", phase);
                first("races").put("matchday", day);
            }
            case "participants" -> first("races").put("awayTeamOverride", HOME);
            default -> throw new IllegalArgumentException(corruption);
        }
        assertThatThrownBy(() -> validate(8)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }

    private void validate(int version) {
        var validator = new IncidentBackupValidator(version);
        data.forEach((table, rows) -> rows.forEach(row -> validator.accept(table, row)));
        validator.validate();
    }
    private void close() {
        var window = first("incident_report_windows"); window.put("state", "CLOSED"); window.put("version", 2); window.put("closedAt", "2026-11-03T13:00:00Z");
        first("incident_reports").put("frozenRevisionNumber", 2);
        event(null, "CLOSED", 2, null, null, null, "2026-11-03T13:00:00Z", "2026-11-03T13:00:00Z");
    }
    private void revision(int number, long version, String request, String at) {
        row("incident_report_revisions", "id", id(), "report", REPORT, "revisionNumber", number, "reportVersion", version,
                "payload", JSON.createObjectNode().put("involvedDrivers", "Test driver and guest").put("replayTimestamp", "00:00")
                        .put("description", "Test private incident facts").put("evidenceLinks", "https://example.invalid/footage"),
                "actorId", "400", "acceptedAt", at, "requestId", request);
    }
    private void event(String report, String kind, long windowVersion, Long reportVersion, Integer revision, String request, String at, String previous) {
        row("incident_report_events", "id", id(), "window", WINDOW, "report", report,
                "eventKey", report == null ? "window:" + WINDOW + ":" + windowVersion : "report:" + report + ":" + reportVersion,
                "kind", kind, "windowVersion", windowVersion, "reportVersion", reportVersion, "revisionNumber", revision,
                "actorKind", report != null ? "REPORTER" : kind.equals("CLOSED") ? "SYSTEM" : "MANAGEMENT",
                "actorId", report != null ? "400" : "test-manager", "reason", report == null ? "Test explicit window decision" : "",
                "acceptedAt", at, "previousDeadline", previous, "deadline", "2026-11-03T13:00:00Z", "requestId", request);
    }
    private ObjectNode first(String table) { return data.get(table).getFirst(); }
    private ObjectNode row(String table, Object... values) {
        var node = JSON.createObjectNode();
        for (int i = 0; i < values.length; i += 2) node.set((String) values[i], JSON.valueToTree(values[i + 1]));
        if (table.startsWith("incident_")) { node.putNull("createdAt"); node.putNull("updatedAt"); }
        data.computeIfAbsent(table, ignored -> new ArrayList<>()).add(node); return node;
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
