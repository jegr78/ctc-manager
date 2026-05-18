package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.ctc.domain.model.*;
import org.springframework.stereotype.Component;

/**
 * Jackson {@link com.fasterxml.jackson.databind.Module} that registers all 24 per-entity MixIns
 * on the {@code backupObjectMapper} bean via Spring DI. Keeping all mappings in one file makes
 * the entity-to-MixIn mapping reviewable at a glance.
 */
@Component
public class BackupSerializationModule extends SimpleModule {

    public BackupSerializationModule() {
        super("BackupSerializationModule");
        setMixInAnnotation(Car.class, CarMixIn.class);
        setMixInAnnotation(Track.class, TrackMixIn.class);
        setMixInAnnotation(RaceScoring.class, RaceScoringMixIn.class);
        setMixInAnnotation(MatchScoring.class, MatchScoringMixIn.class);
        setMixInAnnotation(Driver.class, DriverMixIn.class);
        setMixInAnnotation(PsnAlias.class, PsnAliasMixIn.class);
        setMixInAnnotation(Team.class, TeamMixIn.class);
        setMixInAnnotation(Season.class, SeasonMixIn.class);
        setMixInAnnotation(SeasonPhase.class, SeasonPhaseMixIn.class);
        setMixInAnnotation(SeasonPhaseGroup.class, SeasonPhaseGroupMixIn.class);
        setMixInAnnotation(PhaseTeam.class, PhaseTeamMixIn.class);
        setMixInAnnotation(SeasonTeam.class, SeasonTeamMixIn.class);
        setMixInAnnotation(SeasonDriver.class, SeasonDriverMixIn.class);
        setMixInAnnotation(Playoff.class, PlayoffMixIn.class);
        setMixInAnnotation(PlayoffRound.class, PlayoffRoundMixIn.class);
        setMixInAnnotation(PlayoffMatchup.class, PlayoffMatchupMixIn.class);
        setMixInAnnotation(PlayoffSeed.class, PlayoffSeedMixIn.class);
        setMixInAnnotation(Matchday.class, MatchdayMixIn.class);
        setMixInAnnotation(Match.class, MatchMixIn.class);
        setMixInAnnotation(Race.class, RaceMixIn.class);
        setMixInAnnotation(RaceLineup.class, RaceLineupMixIn.class);
        setMixInAnnotation(RaceResult.class, RaceResultMixIn.class);
        setMixInAnnotation(RaceSettings.class, RaceSettingsMixIn.class);
        setMixInAnnotation(RaceAttachment.class, RaceAttachmentMixIn.class);
    }
}
