package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieBreakBackupValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String phase = UUID.randomUUID().toString();
    private final String matchday = UUID.randomUUID().toString();
    private final String playoff = UUID.randomUUID().toString();
    private final String round = UUID.randomUUID().toString();
    private final String home = UUID.randomUUID().toString();
    private final String away = UUID.randomUUID().toString();

    @Test
    void givenConsistentTieBreakRows_whenValidating_thenAccepted() {
        // given
        var validator = graph(true);
        validator.accept("matches", match(home, 40, 40));
        validator.accept("playoff_matchups", matchup(true, home, 30, 30));
        // when / then
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenWinnerInPhaseWithoutRule_whenValidating_thenRejected() {
        // given
        var validator = graph(false);
        validator.accept("matches", match(home, 40, 40));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid match tie-break winner");
    }

    @Test
    void givenWinnerOfUntiedMatch_whenValidating_thenRejected() {
        // given
        var validator = graph(true);
        validator.accept("matches", match(home, 41, 40));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenNonParticipantWinner_whenValidating_thenRejected() {
        // given
        var validator = graph(true);
        validator.accept("matches", match(UUID.randomUUID().toString(), 40, 40));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTieBreakMatchupWithoutWinnerOrTie_whenValidating_thenRejected() {
        // given
        var validator = graph(true);
        validator.accept("playoff_matchups", matchup(true, null, 30, 30));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid playoff tie-break decision");
    }

    private TieBreakBackupValidator graph(boolean rule) {
        var validator = new TieBreakBackupValidator();
        validator.accept("season_phases", mapper.createObjectNode().put("id", phase).put("tieBreakRaceWinner", rule));
        validator.accept("matchdays", mapper.createObjectNode().put("id", matchday).put("phase", phase));
        validator.accept("playoffs", mapper.createObjectNode().put("id", playoff).put("phase", phase));
        validator.accept("playoff_rounds", mapper.createObjectNode().put("id", round).put("playoff", playoff));
        return validator;
    }

    private ObjectNode match(String winner, int homeScore, int awayScore) {
        return mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("matchday", matchday)
                .put("homeTeam", home).put("awayTeam", away).put("homeScore", homeScore).put("awayScore", awayScore)
                .put("bye", false).putNull("walkoverTeam").put("tieBreakWinner", winner);
    }

    private ObjectNode matchup(boolean decided, String winner, int homeScore, int awayScore) {
        return mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("round", round)
                .put("team1", home).put("team2", away).put("winner", winner).put("homeScore", homeScore)
                .put("awayScore", awayScore).put("bye", false).putNull("walkoverTeam").put("decidedByTieBreak", decided);
    }
}
