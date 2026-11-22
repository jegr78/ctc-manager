package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMixInTest {

    @Test
    void givenTieBreakMatch_whenSerialize_thenWinnerIsIdRefAndDerivedFlagsAbsent() throws Exception {
        // given
        var mapper = new ObjectMapper().registerModule(new BackupSerializationModule()).registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var home = new Team("Test home", "TBH"); home.setId(UUID.randomUUID());
        var away = new Team("Test away", "TBA"); away.setId(UUID.randomUUID());
        var match = new Match(matchday, home, away);
        match.setId(UUID.randomUUID());
        match.setHomeScore(40); match.setAwayScore(40); match.setTieBreakWinner(away);
        // when
        var node = mapper.readTree(mapper.writeValueAsString(match));
        // then
        assertThat(node.get("tieBreakWinner").asText()).as("winner as id reference").isEqualTo(away.getId().toString());
        for (var derived : new String[]{"homeWinner", "awayWinner", "wonOnTieBreak", "scoredDraw"}) {
            assertThat(node.has(derived)).as(derived + " is derived and not exported").isFalse();
        }
    }
}
