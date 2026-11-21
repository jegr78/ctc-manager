package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieBreakArchiveTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {1, 12, 27})
    void givenOlderSchema_whenNormalizing_thenDefaultsWithoutTieBreak(int version) {
        // when
        var phase = TieBreakArchive.normalize(version, "season_phases", mapper.createObjectNode().put("legs", 1));
        var match = TieBreakArchive.normalize(version, "matches", mapper.createObjectNode().put("bye", false));
        var matchup = TieBreakArchive.normalize(version, "playoff_matchups", mapper.createObjectNode().put("bye", false));
        // then
        assertThat(phase.path("tieBreakRaceWinner").booleanValue()).isFalse();
        assertThat(match.path("tieBreakWinner").isNull()).isTrue();
        assertThat(matchup.path("decidedByTieBreak").booleanValue()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 27})
    void givenOlderSchemaWithTieBreakData_whenNormalizing_thenRejected(int version) {
        // when / then
        assertThatThrownBy(() -> TieBreakArchive.normalize(version, "season_phases", mapper.createObjectNode().put("tieBreakRaceWinner", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tie-break fields require backup schema 28");
        assertThatThrownBy(() -> TieBreakArchive.normalize(version, "matches", mapper.createObjectNode().put("tieBreakWinner", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TieBreakArchive.normalize(version, "playoff_matchups", mapper.createObjectNode().put("decidedByTieBreak", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenCurrentSchema_whenNormalizing_thenRequiresTypedFields() {
        // given
        var winner = UUID.randomUUID().toString();
        // when
        var match = TieBreakArchive.normalize(28, "matches", mapper.createObjectNode().put("tieBreakWinner", winner));
        // then
        assertThat(match.path("tieBreakWinner").textValue()).isEqualTo(winner);
        assertThatThrownBy(() -> TieBreakArchive.normalize(28, "season_phases", mapper.createObjectNode()))
                .as("missing flag").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TieBreakArchive.normalize(28, "playoff_matchups", mapper.createObjectNode().put("decidedByTieBreak", "yes")))
                .as("text flag").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TieBreakArchive.normalize(28, "matches", mapper.createObjectNode().put("tieBreakWinner", "not-a-uuid")))
                .as("invalid id").isInstanceOf(IllegalArgumentException.class);
        assertThat(TieBreakArchive.normalize(28, "cars", mapper.createObjectNode())).isNotNull();
    }
}
