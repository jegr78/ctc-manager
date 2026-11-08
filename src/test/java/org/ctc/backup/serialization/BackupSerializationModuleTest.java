package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupSerializationModuleTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new BackupSerializationModule());
    }

    @Test
    void givenFreshMapper_whenRegisterModule_thenAllTwentyFourMixInsAreWired() {
        // given / when / then — verify each entity-to-MixIn mapping
        assertThat(mapper.findMixInClassFor(Car.class)).isEqualTo(CarMixIn.class);
        assertThat(mapper.findMixInClassFor(Track.class)).isEqualTo(TrackMixIn.class);
        assertThat(mapper.findMixInClassFor(RaceScoring.class)).isEqualTo(RaceScoringMixIn.class);
        assertThat(mapper.findMixInClassFor(MatchScoring.class)).isEqualTo(MatchScoringMixIn.class);
        assertThat(mapper.findMixInClassFor(Driver.class)).isEqualTo(DriverMixIn.class);
        assertThat(mapper.findMixInClassFor(PsnAlias.class)).isEqualTo(PsnAliasMixIn.class);
        assertThat(mapper.findMixInClassFor(Team.class)).isEqualTo(TeamMixIn.class);
        assertThat(mapper.findMixInClassFor(Season.class)).isEqualTo(SeasonMixIn.class);
        assertThat(mapper.findMixInClassFor(SeasonPhase.class)).isEqualTo(SeasonPhaseMixIn.class);
        assertThat(mapper.findMixInClassFor(SeasonPhaseGroup.class)).isEqualTo(SeasonPhaseGroupMixIn.class);
        assertThat(mapper.findMixInClassFor(PhaseTeam.class)).isEqualTo(PhaseTeamMixIn.class);
        assertThat(mapper.findMixInClassFor(SeasonTeam.class)).isEqualTo(SeasonTeamMixIn.class);
        assertThat(mapper.findMixInClassFor(SeasonDriver.class)).isEqualTo(SeasonDriverMixIn.class);
        assertThat(mapper.findMixInClassFor(Playoff.class)).isEqualTo(PlayoffMixIn.class);
        assertThat(mapper.findMixInClassFor(PlayoffRound.class)).isEqualTo(PlayoffRoundMixIn.class);
        assertThat(mapper.findMixInClassFor(PlayoffMatchup.class)).isEqualTo(PlayoffMatchupMixIn.class);
        assertThat(mapper.findMixInClassFor(PlayoffSeed.class)).isEqualTo(PlayoffSeedMixIn.class);
        assertThat(mapper.findMixInClassFor(Matchday.class)).isEqualTo(MatchdayMixIn.class);
        assertThat(mapper.findMixInClassFor(Match.class)).isEqualTo(MatchMixIn.class);
        assertThat(mapper.findMixInClassFor(Race.class)).isEqualTo(RaceMixIn.class);
        assertThat(mapper.findMixInClassFor(RaceLineup.class)).isEqualTo(RaceLineupMixIn.class);
        assertThat(mapper.findMixInClassFor(RaceResult.class)).isEqualTo(RaceResultMixIn.class);
        assertThat(mapper.findMixInClassFor(RaceSettings.class)).isEqualTo(RaceSettingsMixIn.class);
        assertThat(mapper.findMixInClassFor(RaceAttachment.class)).isEqualTo(RaceAttachmentMixIn.class);
    }

    @Test
    void givenMatchWithForfeitingTeam_whenExporting_thenWriteOnlyItsUuid() {
        // given
        var home = new Team("Test walkover home", "T-WOH"); home.setId(java.util.UUID.randomUUID());
        var away = new Team("Test walkover away", "T-WOA"); away.setId(java.util.UUID.randomUUID());
        var day = new Matchday(); day.setId(java.util.UUID.randomUUID());
        var match = new Match(day, home, away); match.setId(java.util.UUID.randomUUID()); match.setWalkoverTeam(home);
        // when
        var row = mapper.valueToTree(match);
        // then
        assertThat(row.path("walkoverTeam").isTextual()).isTrue();
        assertThat(row.path("walkoverTeam").asText()).isEqualTo(home.getId().toString());
        assertThat(row.has("officialHomeScore")).isFalse();
        assertThat(row.has("officialAwayScore")).isFalse();
        assertThat(row.path("homeScore").isNull()).isTrue();
        assertThat(row.path("awayScore").isNull()).isTrue();
    }

    @Test void givenPlayoffWalkover_whenExporting_thenRetainRawEvidenceWithoutComputedDisplayProperties() {
        var home = new Team("Test playoff home", "T-WOH"); home.setId(java.util.UUID.randomUUID());
        var away = new Team("Test playoff away", "T-WOA"); away.setId(java.util.UUID.randomUUID());
        var round = new PlayoffRound(); round.setId(java.util.UUID.randomUUID());
        var matchup = new PlayoffMatchup(round, 1); matchup.setId(java.util.UUID.randomUUID());
        matchup.setTeam1(home); matchup.setTeam2(away); matchup.setWalkoverTeam(away); matchup.setWinner(home);
        var row = mapper.valueToTree(matchup);
        assertThat(row.has("officialHomeScore")).isFalse(); assertThat(row.has("officialAwayScore")).isFalse();
        assertThat(row.path("homeScore").isNull()).isTrue(); assertThat(row.path("awayScore").isNull()).isTrue();
        assertThat(row.path("walkoverTeam").asText()).isEqualTo(away.getId().toString());
    }

    @Test
    void givenFreshMapper_whenLookupNonRegisteredClass_thenReturnsNull() {
        // given — String is not a domain entity; not registered with a MixIn
        // when / then
        assertThat(mapper.findMixInClassFor(String.class)).isNull();
    }
}
