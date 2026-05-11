package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
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
import org.springframework.stereotype.Component;

/**
 * Phase 73 EXPORT-04 — the single Jackson {@code Module} that wires every per-entity MixIn
 * onto the {@code backupObjectMapper} bean. Picked up automatically by Phase 72's
 * {@code BackupObjectMapperConfig.backupObjectMapper(List<Module> backupMixInModules)}
 * via Spring DI — zero config-class changes.
 *
 * <p>The module is implemented as a single Spring component (instead of 24 separate
 * {@code Module} beans) because every MixIn is a sibling annotation-carrier with no
 * cross-MixIn state — keeping them in one file makes the entity-to-MixIn mapping
 * reviewable at a glance.
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
