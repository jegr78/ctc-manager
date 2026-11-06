package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DisqualificationFingerprintTest {
    private UUID id(int value) { return new UUID(0, value); }
    private Race race() {
        var season = new Season(); season.setId(id(2)); var phase = new SeasonPhase(); phase.setId(id(3)); phase.setSeason(season);
        var day = new Matchday(); day.setId(id(4)); day.setPhase(phase); var home = new Team(); home.setId(id(7)); var away = new Team(); away.setId(id(8));
        var match = new Match(); match.setId(id(5)); match.setMatchday(day); match.setHomeTeam(home); match.setAwayTeam(away);
        var race = new Race(); race.setId(id(1)); race.setMatchday(day); race.setMatch(match); return race;
    }
    private ScoringService.RaceScoreSnapshot snapshot(boolean dsq, int racePoints, int total) {
        return new ScoringService.RaceScoreSnapshot(RacePenaltyTotals.calculate(total, 0, 0, 0), List.of(
                new ScoringService.ScoredDriver(id(9), id(10), 1, 2, true, racePoints, 2, 2, total, true, dsq)));
    }

    @Test
    void givenLegacyClassifiedSnapshot_whenFingerprinting_thenRetainReleasedCanonicalSha256() {
        // given / when / then
        assertThat(RacePenaltyService.fingerprint(race(), snapshot(false, 20, 24)))
                .isEqualTo("c5d85c55c2fe58886bcca6a6566ae36171d37f905f6312d491b5cdfd7e2476f7");
    }

    @Test
    void givenUnchangedZeroRacePointsAndBonuses_whenOnlyDsqFlagChanges_thenInvalidateOldResultFingerprint() {
        // given
        var race = race(); var classified = snapshot(false, 0, 4); var disqualified = snapshot(true, 0, 4);
        // when / then
        assertThat(RacePenaltyService.fingerprint(race, classified)).isNotEqualTo(RacePenaltyService.fingerprint(race, disqualified));
    }
}
