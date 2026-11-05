package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RacePenaltyBackupValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ObjectNode> rows = new LinkedHashMap<>();

    private RacePenaltyBackupValidator fixture() {
        var season = row("seasons"); var home = row("teams"); var away = row("teams");
        var phase = row("season_phases").put("season", id(season));
        var day = row("matchdays").put("phase", id(phase));
        var match = row("matches").put("matchday", id(day)).put("homeTeam", id(home)).put("awayTeam", id(away));
        var race = row("races").put("matchday", id(day)).put("match", id(match));
        row("race_penalty_revisions").put("race", id(race)).put("season", id(season)).put("phase", id(phase))
                .put("matchday", id(day)).put("match", id(match)).putNull("playoffMatchup")
                .put("homeTeam", id(home)).put("awayTeam", id(away)).put("revisionNumber", 1)
                .put("homePenalty", 20).put("awayPenalty", 10).put("homeGross", 80).put("awayGross", 70)
                .put("reason", "Internal evidence").put("actorId", "trusted-manager").put("resultFingerprint", "a".repeat(64))
                .put("createdAt", "2026-11-05T12:00:00").put("updatedAt", "2026-11-05T12:00:00");
        return new RacePenaltyBackupValidator(13);
    }
    private ObjectNode row(String table) {
        var row = mapper.createObjectNode().put("id", UUID.randomUUID().toString());
        rows.put(table + ":" + rows.size(), row); return row;
    }
    private String id(ObjectNode row) { return row.get("id").asText(); }
    private ObjectNode penalty() { return rows.values().stream().filter(row -> row.has("revisionNumber")).findFirst().orElseThrow(); }
    private void feed(RacePenaltyBackupValidator validator) {
        rows.forEach((key, row) -> validator.accept(key.substring(0, key.indexOf(':')), row)); validator.validate();
    }

    @Test
    void givenValidHistory_whenValidating_thenAcceptExactIdentityAndZeroReset() {
        // given
        var validator = fixture(); var reset = penalty().deepCopy().put("id", UUID.randomUUID().toString())
                .put("revisionNumber", 2).put("homePenalty", 0).put("awayPenalty", 0);
        rows.put("race_penalty_revisions:reset", reset);
        // when / then
        assertThatCode(() -> feed(validator)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"homePenalty", "awayPenalty", "revisionNumber", "race", "season", "phase", "matchday", "match", "homeTeam", "awayTeam", "reason", "actorId", "resultFingerprint", "createdAt", "homeGross"})
    void givenMalformedHistory_whenValidating_thenRejectBeforeRestore(String field) {
        // given
        var validator = fixture(); var row = penalty();
        if (List.of("homePenalty", "awayPenalty").contains(field)) row.put(field, -1);
        else if (field.equals("revisionNumber")) row.put(field, 2);
        else if (field.equals("homeGross")) row.put(field, Long.MAX_VALUE);
        else if (List.of("reason", "actorId").contains(field)) row.put(field, " ");
        else if (field.equals("resultFingerprint")) row.put(field, "z".repeat(64));
        else if (field.equals("createdAt")) row.put(field, "not-a-time");
        else row.put(field, UUID.randomUUID().toString());
        // when / then
        assertThatThrownBy(() -> feed(validator)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("penalty");
    }

    @Test
    void givenReferenceBudgetExhausted_whenAcceptingMoreRows_thenRejectBeforeUnboundedRetention() {
        // given
        var validator = new RacePenaltyBackupValidator(13);
        for (int i = 0; i < 100_000; i++) validator.accept("teams", mapper.createObjectNode().put("id", new UUID(0, i).toString()));
        // when / then
        assertThatThrownBy(() -> validator.accept("teams", mapper.createObjectNode().put("id", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("budget");
    }

    @Test
    void givenOutOfRangeAuditDate_whenValidating_thenRejectBeforeRestore() {
        // given
        var validator = fixture(); penalty().put("createdAt", "0999-11-05T12:00:00");
        // when / then
        assertThatThrownBy(() -> feed(validator)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("penalty timestamp");
    }

    @Test
    void givenOldArchive_whenHistoryPresent_thenRejectRatherThanDiscardingIt() {
        // given
        fixture();
        // when / then
        assertThatThrownBy(() -> feed(new RacePenaltyBackupValidator(12)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("13");
    }

    @Test
    void givenDuplicateRevisionOrIdentityDrift_whenValidating_thenReject() {
        // given
        var validator = fixture(); rows.put("race_penalty_revisions:duplicate", penalty().deepCopy());
        // when / then
        assertThatThrownBy(() -> feed(validator)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("penalty");
    }
}
