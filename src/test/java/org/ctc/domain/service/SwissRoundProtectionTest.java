package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.discord.repository.DiscordPostRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwissRoundProtectionTest {
    @Mock RaceLineupRepository lineups;
    @Mock MatchdayPickRepository picks;
    @Mock IncidentMatchContextRepository contexts;
    @Mock IncidentReportRepository reports;
    @Mock RacePenaltyRevisionRepository penalties;
    @Mock RaceDisqualificationRevisionRepository disqualifications;
    @Mock DiscordPostRepository posts;
    @InjectMocks SwissRoundProtection protection;

    @ParameterizedTest @ValueSource(strings={"score","walkover","channel","context","post","playoff","misplaced","results","lineup","picks","incident","penalty","dsq","attachment","racepost"})
    void givenOneHistoryBearingSource_whenCheckingPairing_thenKeepItsIdentityLocked(String source) {
        var phase=new SeasonPhase(); var day=new Matchday(phase,"Test round",1); day.setId(UUID.randomUUID());
        var home=new Team("Test home","TestH"); home.setId(UUID.randomUUID()); var away=new Team("Test away","TestA"); away.setId(UUID.randomUUID());
        var match=new Match(day,home,away); match.setId(UUID.randomUUID());
        var race=new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(match);
        switch(source) {
            case "score" -> match.setHomeScore(0);
            case "walkover" -> match.setWalkoverTeam(home);
            case "channel" -> match.setDiscordChannelId("12345");
            case "context" -> when(contexts.findByMatchId(match.getId())).thenReturn(Optional.of(mock(IncidentMatchContext.class)));
            case "post" -> when(posts.existsByMatchId(match.getId())).thenReturn(true);
            case "playoff" -> race.setPlayoffMatchup(new PlayoffMatchup());
            case "misplaced" -> { var other=new Matchday(phase,"Test other",2); other.setId(UUID.randomUUID()); race.setMatchday(other); }
            case "results" -> race.getResults().add(new RaceResult());
            case "lineup" -> when(lineups.existsByRaceId(race.getId())).thenReturn(true);
            case "picks" -> when(picks.findSnapshotByRaceId(race.getId())).thenReturn(Optional.of(mock(MatchdayPick.class)));
            case "incident" -> when(reports.existsByRaceId(race.getId())).thenReturn(true);
            case "penalty" -> when(penalties.existsByRaceId(race.getId())).thenReturn(true);
            case "dsq" -> when(disqualifications.existsByRaceId(race.getId())).thenReturn(true);
            case "attachment" -> race.getAttachments().add(new RaceAttachment(race,AttachmentType.LINK,"Test evidence","https://example.invalid/test"));
            case "racepost" -> when(posts.existsByRaceId(race.getId())).thenReturn(true);
            default -> throw new AssertionError(source);
        }
        assertThat(protection.reason(match,List.of(race))).as(source+" preserves pairing identity").isNotBlank();
        assertThat(race.getMatch()).isSameAs(match); assertThat(match.getHomeTeam()).isSameAs(home);
    }
}
