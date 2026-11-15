package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class DriverStatsServiceIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired DriverStatsService service;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired MatchRepository matches;
    @Autowired RaceRepository races;
    @Autowired RaceResultRepository results;
    @Autowired RaceLineupRepository lineups;
    @Autowired SeasonDriverRepository seasonDrivers;
    @Autowired PlayoffRepository playoffs;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired PlayoffService playoffService;
    @Autowired PlayoffSeedingService seeding;
    final List<UUID> driverIds = new ArrayList<>();
    String suffix;
    TestHelper.SeasonFixture fixture;
    UUID playoff;

    @BeforeEach
    void setUp() {
        // given
        suffix = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TSTS" + suffix);
            var target = driver("TSD" + suffix, "Test stats target " + suffix);
            var rival = driver("TSX" + suffix, "Test stats rival " + suffix);
            driver("TSZ" + suffix, "Test stats idle " + suffix);
            driver("TST1" + suffix, "Test stats twin " + suffix);
            driver("TST2" + suffix, "Test stats twin " + suffix);
            helper.createSeasonDriver(fixture.season(), target, fixture.homeTeam());
            helper.createSeasonDriver(fixture.season(), rival, fixture.awayTeam());
            var first = fixture.race();
            lineups.save(new RaceLineup(first, target, fixture.homeTeam()));
            result(first, rival, 1, 1, false, 0, true);
            result(first, target, 2, 2, true, 20, false);
            result(helper.createRace(fixture.matchday(), fixture.match()), target, 3, 3, false, 14, false);
            var walkoverDay = helper.createMatchday(fixture.season(), "TSTS" + suffix + " MD2", 2);
            var walkover = helper.createMatch(walkoverDay, fixture.homeTeam(), fixture.awayTeam());
            walkover.setWalkoverTeam(fixture.awayTeam());
            matches.save(walkover);
            var forfeited = helper.createRace(walkoverDay, walkover);
            result(forfeited, target, 1, 1, true, 25, false);
            result(forfeited, rival, 2, 2, false, 17, false);
            var guestDay = helper.createMatchday(fixture.season(), "TSTS" + suffix + " MD3", 3);
            var guestRace = helper.createRace(guestDay, helper.createMatch(guestDay, fixture.awayTeam(), fixture.homeTeam()));
            lineups.save(new RaceLineup(guestRace, target, fixture.awayTeam(), true));
            result(guestRace, target, 2, 1, false, 17, false);
        });
        playoff = playoffService.createPlayoff(fixture.season().getId(), "Test stats playoff", 4).getId();
        UUID opening = tx.execute(status -> playoffs.findById(playoff).orElseThrow().getRounds().stream()
                .filter(round -> round.getRoundIndex() == 0).findFirst().orElseThrow().getMatchups().stream()
                .min(Comparator.comparingInt(PlayoffMatchup::getBracketPosition)).orElseThrow().getId());
        seeding.seedTeam(opening, fixture.homeTeam().getId(), 1);
        seeding.seedTeam(opening, fixture.awayTeam().getId(), 2);
        UUID playoffRace = playoffService.addRaceToMatchup(opening, null, null, LocalDateTime.of(2035, 1, 1, 20, 0)).getId();
        tx.executeWithoutResult(status -> result(races.findById(playoffRace).orElseThrow(), drivers.findById(driverIds.getFirst()).orElseThrow(), 1, 2, false, 20, false));
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            seasonDrivers.deleteAll(seasonDrivers.findBySeasonId(season.getId()));
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
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenDsqAheadWalkoverGuestAndPlayoffRaces_whenReadingSeasonStats_thenCountOnlyCountedRaces() {
        // when
        var stats = service.stats("tsd" + suffix, period());
        // then
        assertThat(stats.races()).as("the walkover race is excluded, the playoff race counts").isEqualTo(4);
        assertThat(stats.wins()).as("a DSQ ahead promotes the second place to a win").isEqualTo(2);
        assertThat(stats.podiums()).isEqualTo(4);
        assertThat(stats.poles()).isEqualTo(1);
        assertThat(stats.fastestLaps()).as("the walkover fastest lap is excluded").isEqualTo(1);
        assertThat(stats.bestPosition()).isEqualTo(1);
        assertThat(stats.averagePosition()).isEqualTo(1.75);
        assertThat(stats.points()).isEqualTo(71);
        assertThat(stats.guestRaces()).isEqualTo(1);
        assertThat(stats.disqualifications()).isZero();
        assertThat(stats.rank()).isEqualTo(1);
        assertThat(stats.ranked()).isEqualTo(2);
        assertThat(stats.teams()).as("the latest regular lineup names the season team").containsExactly(fixture.homeTeam().getName());
    }

    @Test
    void givenPoleWithDsq_whenReadingSeasonStats_thenCountRaceAndPoleButNoPosition() {
        // when
        var stats = service.stats("Test stats rival " + suffix, period());
        // then
        assertThat(stats.races()).as("a DSQ counts as a race, the walkover does not").isEqualTo(1);
        assertThat(stats.poles()).isEqualTo(1);
        assertThat(stats.wins()).isZero();
        assertThat(stats.podiums()).isZero();
        assertThat(stats.disqualifications()).isEqualTo(1);
        assertThat(stats.bestPosition()).isZero();
        assertThat(stats.averagePosition()).as("the average position ignores DSQ results").isNull();
        assertThat(stats.rank()).isEqualTo(2);
    }

    @Test
    void givenUnknownAmbiguousOrIdleDriver_whenReadingStats_thenExplainWithoutFigures() {
        // when / then
        assertThatThrownBy(() -> service.stats("TSNONE" + suffix, period())).isInstanceOf(StatsLookupException.class).hasMessage("No driver found");
        assertThatThrownBy(() -> service.stats("test stats twin " + suffix, period())).isInstanceOf(StatsLookupException.class)
                .hasMessage("Several drivers match, pick one from the list");
        assertThatThrownBy(() -> service.stats("TSZ" + suffix, period())).isInstanceOf(StatsLookupException.class)
                .hasMessage("No results for TSZ" + suffix + " in " + fixture.season().getDisplayLabel());
    }

    @Test
    void givenDriverId_whenReadingStats_thenResolveTheIdBeforeNames() {
        // when / then
        assertThat(service.stats(driverIds.getFirst().toString(), period()).psnId()).isEqualTo("TSD" + suffix);
    }

    @Test
    void whenListingDriversWithResults_thenIncludeOnlyDriversWithResultsInTheSeasons() {
        // when
        var found = tx.execute(status -> drivers.findWithResultsInSeasons(List.of(fixture.season().getId())).stream().map(Driver::getPsnId).toList());
        // then
        assertThat(found).containsExactlyInAnyOrder("TSD" + suffix, "TSX" + suffix);
    }

    private StatsPeriod period() {
        var season = seasons.findById(fixture.season().getId()).orElseThrow();
        return new StatsPeriod(season, List.of(season), false);
    }

    private Driver driver(String psn, String nickname) {
        var driver = helper.createDriver(psn, nickname);
        driverIds.add(driver.getId());
        return driver;
    }

    private void result(Race race, Driver driver, int position, int quali, boolean fastestLap, int points, boolean disqualified) {
        var result = new RaceResult(race, driver, position, quali, fastestLap);
        result.setPointsTotal(points);
        result.setDisqualified(disqualified);
        results.save(result);
    }
}
