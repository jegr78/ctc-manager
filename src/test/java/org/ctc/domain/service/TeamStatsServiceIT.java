package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.TeamStats;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class TeamStatsServiceIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired TeamStatsService service;
    @Autowired StandingsService standings;
    @Autowired SeasonPhaseService phases;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired MatchRepository matches;
    @Autowired RaceRepository races;
    @Autowired RaceResultRepository results;
    @Autowired RaceLineupRepository lineups;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired PlayoffRepository playoffs;
    @Autowired PlayoffService playoffService;
    @Autowired PlayoffSeedingService seeding;
    final List<UUID> driverIds = new ArrayList<>();
    String suffix;
    TestHelper.SeasonFixture fixture;
    Team subTeam;
    UUID playoff;

    @BeforeEach
    void setUp() {
        // given
        suffix = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("T-STT" + suffix);
            subTeam = helper.createSubTeam("T-STT" + suffix + " Away B", "T-STT" + suffix + "_AWB", fixture.awayTeam());
            var regular = phases.findRegularPhase(fixture.season().getId());
            for (var team : List.of(fixture.homeTeam(), fixture.awayTeam(), subTeam)) phaseTeams.save(new PhaseTeam(regular, team));
            var leader = driver("TSTA" + suffix);
            var second = driver("TSTB" + suffix);
            var rival = driver("TSTC" + suffix);
            scored(fixture.match(), 60, 40);
            fielded(fixture.race(), leader, fixture.homeTeam(), 1, 25);
            fielded(fixture.race(), second, fixture.homeTeam(), 2, 10);
            fielded(fixture.race(), rival, fixture.awayTeam(), 3, 8);
            var day = helper.createMatchday(fixture.season(), "T-STT" + suffix + " MD2", 2);
            var subMatch = helper.createMatch(day, fixture.homeTeam(), subTeam);
            scored(subMatch, 30, 50);
            var subRace = helper.createRace(day, subMatch);
            fielded(subRace, rival, subTeam, 1, 25);
            fielded(subRace, leader, fixture.homeTeam(), 2, 18);
        });
        playoff = playoffService.createPlayoff(fixture.season().getId(), "Test stats final", 2).getId();
        UUID finalMatchup = tx.execute(status -> playoffs.findById(playoff).orElseThrow().getRounds().getLast().getMatchups().stream()
                .min(Comparator.comparingInt(PlayoffMatchup::getBracketPosition)).orElseThrow().getId());
        seeding.seedTeam(finalMatchup, fixture.homeTeam().getId(), 1);
        seeding.seedTeam(finalMatchup, subTeam.getId(), 2);
        playoffService.setWinnerManually(finalMatchup, fixture.homeTeam().getId(), "Test final decided");
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            lineups.deleteAll(lineups.findByRaceMatchdaySeasonId(season.getId()));
            lineups.flush();
            races.deleteAll(races.findByMatchdaySeasonId(season.getId()));
            races.flush();
            var bracket = playoffs.findById(playoff).orElseThrow();
            bracket.getRounds().forEach(round -> round.getMatchups().forEach(matchup -> matchup.setNextMatchup(null)));
            playoffs.flush();
            playoffs.delete(bracket);
            playoffs.flush();
            season.getPhases().forEach(phase -> phaseTeams.deleteAll(phaseTeams.findByPhaseId(phase.getId())));
            phaseTeams.flush();
            helper.deleteSeasonCascade(season);
            seasons.flush();
            drivers.deleteAllById(driverIds);
            teams.deleteById(subTeam.getId());
            teams.flush();
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenSeasonScope_whenReadingTeamStats_thenMatchTheWebsiteStandingsRow() {
        // when
        var stats = service.stats("t-stt" + suffix + "_hom", period());
        // then
        var table = tx.execute(status -> standings.calculateStandings(phases.findRegularPhase(fixture.season().getId()).getId(), null));
        var row = table.stream().filter(standing -> standing.getTeam().getId().equals(fixture.homeTeam().getId())).findFirst().orElseThrow();
        assertThat(stats.rank()).as("the rank is the team's position in the website standings").isEqualTo(table.indexOf(row) + 1).isEqualTo(2);
        assertThat(stats.ranked()).isEqualTo(table.size()).isEqualTo(3);
        assertThat(List.of(stats.played(), stats.wins(), stats.draws(), stats.losses(), stats.pointsFor(), stats.pointsAgainst(), stats.matchPoints()))
                .isEqualTo(List.of(row.getPlayed(), row.getWins(), row.getDraws(), row.getLosses(), row.getPointsFor(), row.getPointsAgainst(), row.getPoints()))
                .isEqualTo(List.of(2, 1, 0, 1, 90, 90, 3));
        assertThat(stats.races()).isEqualTo(2);
        assertThat(stats.playoffResult()).as("the winner of the highest round is champion").isEqualTo("Champion");
        assertThat(stats.topDrivers()).containsExactly(new TeamStats.TopDriver("TSTA" + suffix, 43), new TeamStats.TopDriver("TSTB" + suffix, 10));
    }

    @Test
    void givenSubteam_whenReadingSeasonStats_thenKeepItSeparateWithItsPlayoffRound() {
        // when
        var stats = service.stats(subTeam.getId().toString(), period());
        // then
        assertThat(stats.rank()).isEqualTo(1);
        assertThat(stats.playoffResult()).as("a beaten finalist shows the round label").isEqualTo("Final");
        assertThat(stats.topDrivers()).containsExactly(new TeamStats.TopDriver("TSTC" + suffix, 25));
    }

    @Test
    void givenParentWithSubteam_whenReadingAlltimeStats_thenRollUpLikeTheAlltimeStandings() {
        // when
        var stats = service.stats(subTeam.getShortName(), StatsPeriod.of(null, List.of(season()), false));
        // then
        var table = tx.execute(status -> standings.calculateAlltimeStandings(List.of(seasons.findById(fixture.season().getId()).orElseThrow())));
        var row = table.stream().filter(standing -> standing.getTeam().getId().equals(fixture.awayTeam().getId())).findFirst().orElseThrow();
        assertThat(stats.teamId()).as("alltime names the parent team").isEqualTo(fixture.awayTeam().getId());
        assertThat(stats.rank()).isEqualTo(table.indexOf(row) + 1);
        assertThat(List.of(stats.played(), stats.wins(), stats.losses(), stats.pointsFor(), stats.pointsAgainst(), stats.matchPoints()))
                .isEqualTo(List.of(row.getPlayed(), row.getWins(), row.getLosses(), row.getPointsFor(), row.getPointsAgainst(), row.getPoints()));
        assertThat(stats.seasons()).isEqualTo(1);
        assertThat(stats.bestResult()).isEqualTo("1st in " + season().getDisplayLabel());
        assertThat(stats.playoffAppearances()).isEqualTo(1);
        assertThat(stats.titles()).isZero();
        assertThat(stats.topDrivers()).containsExactly(new TeamStats.TopDriver("TSTC" + suffix, 33));
        assertThat(stats.profileSeasonLabel()).isEqualTo(season().getDisplayLabel());
    }

    @Test
    void givenChampion_whenReadingAlltimeStats_thenCountTheTitle() {
        // when
        var stats = service.stats(fixture.homeTeam().getName(), StatsPeriod.of(null, List.of(season()), false));
        // then
        assertThat(stats.titles()).isEqualTo(1);
        assertThat(stats.playoffAppearances()).isEqualTo(1);
        assertThat(stats.bestResult()).isEqualTo("2nd in " + season().getDisplayLabel());
    }

    @Test
    void givenUnknownTeam_whenReadingStats_thenSayNoTeamFound() {
        // when / then
        assertThatThrownBy(() -> service.stats("T-STT-none" + suffix, period())).isInstanceOf(StatsLookupException.class).hasMessage("No team found");
    }

    private Season season() { return tx.execute(status -> seasons.findById(fixture.season().getId()).orElseThrow()); }

    private StatsPeriod period() {
        var season = season();
        return StatsPeriod.of(season, List.of(season), false);
    }

    private Driver driver(String psn) {
        var driver = helper.createDriver(psn, psn);
        driverIds.add(driver.getId());
        return driver;
    }

    private void scored(Match match, int home, int away) {
        match.setHomeScore(home);
        match.setAwayScore(away);
        matches.save(match);
    }

    private void fielded(Race race, Driver driver, Team team, int position, int points) {
        lineups.save(new RaceLineup(race, driver, team));
        var result = new RaceResult(race, driver, position, position, false);
        result.setPointsTotal(points);
        results.save(result);
    }
}
