package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DisqualificationArchiveTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, ObjectNode> rows = new LinkedHashMap<>();
    private ObjectNode result;
    private ObjectNode history;
    private ObjectNode race;
    private ObjectNode match;

    private ObjectNode row(String table) {
        var node = json.createObjectNode().put("id", UUID.randomUUID().toString()); rows.put(table + ":" + rows.size(), node); return node;
    }
    private String id(ObjectNode row) { return row.path("id").textValue(); }
    private void fixture() {
        var season = row("seasons"); var home = row("teams"); var away = row("teams"); var driver = row("drivers");
        var phase = row("season_phases").put("season", id(season)); var day = row("matchdays").put("phase", id(phase));
        match = row("matches").put("matchday", id(day)).put("homeTeam", id(home)).put("awayTeam", id(away));
        race = row("races").put("matchday", id(day)).put("match", id(match));
        result = row("race_results").put("race", id(race)).put("driver", id(driver)).put("position", 1)
                .put("disqualified", true).put("pointsRace", 0).put("pointsQuali", 3).put("pointsFl", 2).put("pointsTotal", 5);
        history = row("race_disqualification_revisions").put("result", id(result)).put("driver", id(driver))
                .put("race", id(race)).put("season", id(season)).put("phase", id(phase)).put("matchday", id(day))
                .put("match", id(match)).putNull("playoffMatchup").put("homeTeam", id(home)).put("awayTeam", id(away))
                .put("revisionNumber", 1).put("previousDisqualified", false).put("disqualified", true).put("recordedPosition", 1)
                .put("reason", "Private DSQ evidence").put("actorId", "trusted-manager").put("resultFingerprint", "a".repeat(64))
                .put("createdAt", "2200-11-05T12:34:56.123456").put("updatedAt", "2200-11-05T12:34:56.123456");
    }
    private void feed(int version) {
        var validator = new DisqualificationBackupValidator(version);
        rows.forEach((key, row) -> validator.accept(key.substring(0, key.indexOf(':')), DisqualificationArchive.normalize(version, key.substring(0, key.indexOf(':')), row)));
        validator.validate();
    }

    @ParameterizedTest
    @ValueSource(ints = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void givenLegacySchema_whenNormalizingMissingFlag_thenDefaultFalseWithoutMutatingOriginal(int version) {
        // given
        var row = json.createObjectNode().put("position", 3).put("pointsTotal", 15);
        // when
        var normalized = DisqualificationArchive.normalize(version, "race_results", row);
        // then
        assertThat(normalized.path("disqualified").isBoolean()).isTrue(); assertThat(normalized.path("disqualified").booleanValue()).isFalse();
        assertThat(normalized.path("position").intValue()).isEqualTo(3); assertThat(normalized.path("pointsTotal").intValue()).isEqualTo(15);
        assertThat(row.has("disqualified")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "text", "number", "array", "object"})
    void givenCurrentSchemaMalformedFlag_whenNormalizing_thenRejectTypedState(String kind) {
        // given
        var row = json.createObjectNode();
        switch (kind) {
            case "null" -> row.putNull("disqualified"); case "text" -> row.put("disqualified", "true");
            case "number" -> row.put("disqualified", 1); case "array" -> row.putArray("disqualified"); case "object" -> row.putObject("disqualified"); default -> { }
        }
        // when / then
        assertThatThrownBy(() -> DisqualificationArchive.normalize(16, "race_results", row)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DSQ");
    }

    @Test
    void givenValidDsqAndClearingHistory_whenValidating_thenAcceptExactStateChainAndCapturedOriginalPositions() {
        // given
        fixture(); var cleared = history.deepCopy().put("id", UUID.randomUUID().toString()).put("revisionNumber", 2)
                .put("previousDisqualified", true).put("disqualified", false);
        result.put("disqualified", false).put("position", 2).put("pointsRace", 70).put("pointsTotal", 75);
        rows.put("race_disqualification_revisions:clear", cleared);
        // when / then
        assertThatCode(() -> feed(16)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"result", "driver", "race", "season", "phase", "matchday", "match", "homeTeam", "awayTeam", "revisionNumber", "previousDisqualified", "disqualified", "recordedPosition", "reason", "actorId", "resultFingerprint", "createdAt", "unexpected"})
    void givenMalformedHistory_whenValidating_thenRejectBeforeAnyRestore(String field) {
        // given
        fixture();
        switch (field) {
            case "revisionNumber" -> history.put(field, 2); case "previousDisqualified" -> history.put(field, true);
            case "disqualified" -> history.put(field, false); case "recordedPosition" -> history.put(field, 0);
            case "reason", "actorId" -> history.put(field, " "); case "resultFingerprint" -> history.put(field, "z".repeat(64));
            case "createdAt" -> history.put(field, "0999-11-05T12:00:00"); case "unexpected" -> history.put("untrusted", "value");
            default -> history.put(field, UUID.randomUUID().toString());
        }
        // when / then
        assertThatThrownBy(() -> feed(16)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DSQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-history", "points", "foreign-driver", "bye", "legacy", "state", "duplicate", "bad-result-flag", "nanoseconds", "duplicate-driver", "bonus-total", "bonus-overflow"})
    void givenInvalidResultContext_whenValidating_thenRejectWithoutInventingHistory(String kind) {
        // given
        fixture();
        switch (kind) {
            case "missing-history" -> rows.entrySet().removeIf(entry -> entry.getKey().startsWith("race_disqualification_revisions:"));
            case "points" -> result.put("pointsRace", 80); case "foreign-driver" -> result.put("driver", UUID.randomUUID().toString());
            case "bye" -> match.put("bye", true); case "state" -> result.put("disqualified", false);
            case "duplicate" -> rows.put("race_disqualification_revisions:duplicate", history.deepCopy());
            case "bad-result-flag" -> result.put("disqualified", "true"); default -> { }
            case "nanoseconds" -> history.put("createdAt", "2200-11-05T12:34:56.123456789");
            case "duplicate-driver" -> rows.put("race_results:duplicate-driver", result.deepCopy().put("id", UUID.randomUUID().toString())
                    .put("position", 2).put("disqualified", false).put("pointsRace", 70).put("pointsTotal", 75));
            case "bonus-total" -> result.put("pointsTotal", 0);
            case "bonus-overflow" -> result.put("pointsQuali", Integer.MAX_VALUE).put("pointsFl", 1).put("pointsTotal", Integer.MIN_VALUE);
        }
        // when / then
        assertThatThrownBy(() -> feed(kind.equals("legacy") ? 15 : 16)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DSQ");
    }

    @Test
    void givenLegacyVersionWithHistoryRow_whenReadingItsShape_thenRejectTheVersionBeforeRetainingHistory() {
        // given
        fixture(); var validator = new DisqualificationBackupValidator(15);
        // when / then
        assertThatThrownBy(() -> validator.accept("race_disqualification_revisions", history))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires schema version 16");
    }

    @Test
    void givenMissingNullableHistoryField_whenReadingCurrentShape_thenRequireExplicitNullReference() {
        // given
        fixture(); history.remove("playoffMatchup"); var validator = new DisqualificationBackupValidator(16);
        // when / then
        assertThatThrownBy(() -> validator.accept("race_disqualification_revisions", history))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing DSQ history field playoffMatchup");
    }

    @Test
    void givenDriverReferenceBudgetReached_whenReadingAnotherReference_thenRejectBeforeUnboundedRetention() {
        // given
        var validator = new DisqualificationBackupValidator(16);
        for (int i = 0; i < 100_000; i++) validator.accept("drivers", json.createObjectNode().put("id", new UUID(0, i).toString()));
        // when / then
        assertThatThrownBy(() -> validator.accept("drivers", json.createObjectNode().put("id", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DSQ validation", "budget");
    }
}
