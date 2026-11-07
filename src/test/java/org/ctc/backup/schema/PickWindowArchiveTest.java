package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickWindowArchiveTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode row() { return mapper.createObjectNode().put("id", UUID.randomUUID().toString()); }
    @ParameterizedTest
    @ValueSource(ints = {1, 5, 7, 12, 16})
    void givenLegacyArchive_whenNormalizing_thenDefaultOnlyNewFieldsWithoutMutatingTheSource(int schema) {
        var original = row();
        var day = PickWindowArchive.normalize(schema, "matchdays", original);
        assertThat(day.path("picksOpen").booleanValue()).isFalse();
        assertThat(day.path("pickWindowVersion").longValue()).isZero();
        assertThat(original.has("picksOpen")).isFalse();
        for (String table : new String[]{"matches", "playoff_matchups"}) {
            var pair = PickWindowArchive.normalize(schema, table, original);
            assertThat(pair.has("pickDeadlineOverride")).isTrue(); assertThat(pair.path("pickDeadlineOverride").isNull()).isTrue();
        }
    }
    @Test
    void givenCurrentWindowSettings_whenNormalizing_thenPreserveTheirExactValues() {
        var day = row().put("picksOpen", true).put("pickWindowVersion", 7);
        assertThat(PickWindowArchive.normalize(17, "matchdays", day)).isEqualTo(day);
        var pair = row().put("pickWindowVersion", 9).put("pickDeadlineOverride", "2035-01-01T20:00:00");
        assertThat(PickWindowArchive.normalize(17, "matches", pair)).isEqualTo(pair);
    }
    @Test
    void givenMissingOrCoercedCurrentFields_whenNormalizing_thenReject() {
        for (var bad : new ObjectNode[]{row(), row().put("picksOpen", "true").put("pickWindowVersion", 0), row().put("picksOpen", true).put("pickWindowVersion", -1), row().put("picksOpen", true).put("pickWindowVersion", "1")})
            assertThatThrownBy(() -> PickWindowArchive.normalize(17, "matchdays", bad)).isInstanceOf(IllegalArgumentException.class);
        for (String cutoff : new String[]{"not-a-date", "2027-03-28T02:30", "2027-10-31T02:30", "0999-01-01T12:00", "2035-01-01T12:00:00.123"})
            assertThatThrownBy(() -> PickWindowArchive.normalize(17, "matches", row().put("pickWindowVersion", 0).put("pickDeadlineOverride", cutoff))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void givenNewSettingsMasqueradingAsAnOldArchive_whenNormalizing_thenReject() {
        assertThatThrownBy(() -> PickWindowArchive.normalize(16, "matchdays", row().put("picksOpen", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PickWindowArchive.normalize(16, "matches", row().put("pickDeadlineOverride", "2035-01-01T20:00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PickWindowArchive.normalize(16, "matches", row().put("pickWindowVersion", new java.math.BigInteger("18446744073709551616"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void givenInconsistentPlayoffBridge_whenValidating_thenRejectWithoutRepairingItsOverride() {
        var match = row().put("pickDeadlineOverride", "2035-01-01T20:00");
        var playoff = row().putNull("pickDeadlineOverride");
        var race = row().put("match", match.path("id").textValue()).put("playoffMatchup", playoff.path("id").textValue());
        var validator = new PickWindowBackupValidator();
        validator.accept("matches", match); validator.accept("playoff_matchups", playoff); validator.accept("races", race);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical");
        assertThat(match.path("pickDeadlineOverride").textValue()).isEqualTo("2035-01-01T20:00");
    }
}
