package org.ctc.testsupport;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

public abstract class RacePenaltyFixture {
    @Autowired protected TestHelper helper;
    @Autowired protected TransactionTemplate tx;
    @Autowired protected RaceService races;
    @Autowired protected RacePenaltyService penalties;
    @Autowired protected RacePenaltyRevisionRepository revisions;
    @Autowired protected RaceLineupRepository lineups;
    @Autowired protected PhaseTeamRepository phaseTeams;
    @Autowired protected SeasonRepository seasons;
    @Autowired protected MatchRepository matches;
    @Autowired protected TeamRepository teams;
    @Autowired protected DriverRepository drivers;
    @Autowired protected PickSessionEpoch epoch;
    @Autowired protected ImportLockService imports;
    protected UUID seasonId;
    protected UUID raceId;
    protected UUID matchId;
    protected UUID homeId;
    protected UUID awayId;
    protected UUID homeDriver;
    protected UUID awayDriver;

    @BeforeEach
    protected void setUp() {
        tx.executeWithoutResult(status -> {
            String prefix = "PenaltyWrite" + UUID.randomUUID().toString().substring(0, 6);
            var graph = helper.createFullSeasonFixture(prefix);
            phaseTeams.saveAllAndFlush(List.of(new PhaseTeam(graph.matchday().getPhase(), graph.homeTeam()), new PhaseTeam(graph.matchday().getPhase(), graph.awayTeam())));
            var scheme = helper.getRaceScoring(graph.season());
            scheme.setRacePoints("80,70"); scheme.setQualiPoints(""); scheme.setFastestLapPoints(0);
            var home = helper.createDriver(prefix + "H", "Home");
            var away = helper.createDriver(prefix + "A", "Away");
            lineups.saveAllAndFlush(List.of(new RaceLineup(graph.race(), home, graph.homeTeam()),
                    new RaceLineup(graph.race(), away, graph.awayTeam(), true)));
            seasonId = graph.season().getId(); raceId = graph.race().getId(); matchId = graph.match().getId();
            homeId = graph.homeTeam().getId(); awayId = graph.awayTeam().getId();
            homeDriver = home.getId(); awayDriver = away.getId();
            races.saveResults(raceId, results(false));
        });
    }

    @AfterEach
    protected void cleanUp() {
        if (seasonId == null) return;
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(seasonId).orElseThrow();
            for (var phase : season.getPhases()) phaseTeams.deleteAll(phaseTeams.findByPhaseId(phase.getId()));
            phaseTeams.flush();
            helper.deleteSeasonCascade(season);
            seasons.flush(); drivers.deleteAllById(List.of(homeDriver, awayDriver));
            teams.deleteAllById(List.of(homeId, awayId));
        });
    }

    protected List<RaceService.RaceResultData> results(boolean reverse) {
        return List.of(new RaceService.RaceResultData(homeDriver, null, null, reverse ? 2 : 1, 1, false),
                new RaceService.RaceResultData(awayDriver, null, null, reverse ? 1 : 2, 2, false));
    }

    protected void assertScores(int home, int away) {
        tx.executeWithoutResult(status -> {
            var match = matches.findById(matchId).orElseThrow();
            assertThat(match.getHomeScore()).isEqualTo(home); assertThat(match.getAwayScore()).isEqualTo(away);
        });
    }
}
