package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class RacePenaltyAggregationIT {
    @Autowired TestHelper helper;
    @Autowired RaceService races;
    @Autowired ScoringService scoring;
    @Autowired RaceScoringRepository schemes;
    @Autowired RaceLineupRepository lineups;
    @Autowired RacePenaltyRevisionRepository penalties;
    @Autowired MatchRepository matches;
    @Autowired EntityManager em;

    @Test
    void givenSavedTeamPenaltyTotals_whenReaggregating_thenApplyOnceAndPreserveDriverPoints() {
        // given
        String prefix = "Penalty" + UUID.randomUUID().toString().substring(0, 6);
        var fixture = helper.createFullSeasonFixture(prefix);
        fixture.matchday().getPhase().setRaceScoring(schemes.saveAndFlush(new RaceScoring(prefix, "80,70", "", 0)));
        var home = helper.createDriver(prefix + "H", "Home driver");
        var away = helper.createDriver(prefix + "A", "Away driver");
        lineups.saveAllAndFlush(List.of(new RaceLineup(fixture.race(), home, fixture.homeTeam()),
                new RaceLineup(fixture.race(), away, fixture.awayTeam())));
        races.saveResults(fixture.race().getId(), List.of(
                new RaceService.RaceResultData(home.getId(), null, null, 1, 1, false),
                new RaceService.RaceResultData(away.getId(), null, null, 2, 2, false)));
        var revision = penalties.saveAndFlush(new RacePenaltyRevision(fixture.race(), 1, 20, 10, 80, 70,
                "Confirmed steward penalties", "test-manager", "a".repeat(64)));
        fixture.race().getPenaltyRevisions().add(revision);
        // when
        scoring.recomputeMatchScores(fixture.match());
        scoring.recomputeMatchScores(fixture.match());
        em.flush();
        em.clear();
        // then
        var stored = matches.findById(fixture.match().getId()).orElseThrow();
        assertThat(stored.getHomeScore()).isEqualTo(70);
        assertThat(stored.getAwayScore()).isEqualTo(70);
        assertThat(stored.getRaces().getFirst().getResults()).extracting(RaceResult::getPointsTotal).containsExactly(80, 70);
        assertThat(penalties.findByRaceIdOrderByRevisionNumberAsc(fixture.race().getId())).hasSize(1);
    }
}
