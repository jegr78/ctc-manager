package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.model.PlayoffSeed;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceAttachment;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link BackupSerializationModule} registers all 24 entity-to-MixIn mappings on a
 * freshly constructed {@link ObjectMapper}. Pure unit test — does NOT boot a Spring context.
 */
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
    void givenFreshMapper_whenLookupNonRegisteredClass_thenReturnsNull() {
        // given — String is not a domain entity; not registered with a MixIn
        // when / then
        assertThat(mapper.findMixInClassFor(String.class)).isNull();
    }
}
