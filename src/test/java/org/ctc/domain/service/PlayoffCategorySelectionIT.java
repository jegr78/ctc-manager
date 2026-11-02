package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CtcDevSpringBootContext
@Tag("integration")
class PlayoffCategorySelectionIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired RaceRepository races;
    @Autowired PlayoffRepository playoffs;
    @Autowired PlayoffMatchupRepository matchups;
    @Autowired PlayoffSeedRepository seeds;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService raceService;
    @Autowired PlayoffService playoffService;
    @Autowired PlayoffSeedingService seeding;
    @Autowired MatchService matchService;
    @Autowired MatchRepository matches;
    @Autowired ScoringService scoring;
    TestHelper.SeasonFixture graph;
    List<UUID> teamIds = new ArrayList<>();
    List<UUID> carIds = new ArrayList<>();
    UUID playoffId;
    UUID entry;
    UUID first;
    UUID second;
    UUID finale;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            graph = helper.createFullSeasonFixture("TPC" + suffix);
            teamIds.add(graph.homeTeam().getId());
            teamIds.add(graph.awayTeam().getId());
            for (String name : List.of("C", "D")) {
                var team = helper.createTeam("Test playoff " + suffix + name, "TPC" + suffix + name);
                graph.season().addTeam(team);
                teamIds.add(team.getId());
            }
            for (String name : List.of("MR", "RR")) {
                var car = new Car("Test playoff " + suffix, name);
                car.setCategory(CarCategory.GR_3);
                car.setDrivetrain(Drivetrain.valueOf(name));
                car.setModelYear(9898);
                car.setModelYearOverride(true);
                car.setModelYearSource("Test fixture");
                car.setModelYearEvidence("Test accepted local year");
                carIds.add(cars.saveAndFlush(car).getId());
            }
            pools.changeMode(graph.season().getId(), CarPoolMode.CATEGORY_POOL);
            entry = pools.saveEntry(graph.season().getId(), null, "Test playoff MR/RR", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9898)));
            pools.freeze(graph.season().getId());
            var playoff = playoffService.createPlayoff(graph.season().getId(), "Test playoff " + suffix, 4);
            playoffId = playoff.getId();
            var firstRound = playoff.getRounds().stream().filter(round -> round.getRoundIndex() == 0).findFirst().orElseThrow();
            var opening = firstRound.getMatchups().stream().sorted(Comparator.comparingInt(PlayoffMatchup::getBracketPosition)).toList();
            first = opening.get(0).getId();
            second = opening.get(1).getId();
            finale = opening.get(0).getNextMatchup().getId();
        });
        seeding.seedTeam(first, teamIds.get(0), 1);
        seeding.seedTeam(first, teamIds.get(1), 2);
        seeding.seedTeam(second, teamIds.get(2), 1);
        seeding.seedTeam(second, teamIds.get(3), 2);
    }

    @AfterEach
    void cleanUp() {
        if (graph == null) return;
        tx.executeWithoutResult(status -> {
            races.deleteAll(races.findByMatchdaySeasonId(graph.season().getId()));
            var playoff = playoffs.findById(playoffId).orElseThrow();
            playoff.getRounds().forEach(round -> round.getMatchups().forEach(matchup -> matchup.setNextMatchup(null)));
            playoffs.flush();
            playoffs.delete(playoff);
            playoffs.flush();
            var season = seasons.findById(graph.season().getId()).orElseThrow();
            for (var phase : season.getPhases()) phaseTeams.deleteAll(phaseTeams.findByPhaseId(phase.getId()));
            phaseTeams.flush();
            helper.deleteSeasonCascade(season);
            seasons.flush();
            cars.deleteAllById(carIds);
            teams.deleteAllById(teamIds);
        });
    }

    @Test
    void givenConfiguredLegAndConsumedNewHome_whenManuallyReseeding_thenRejectWithoutChangingParticipantsOrSelection() {
        // given
        UUID selected = configurePlayoff(first, 0);
        configureRegular(2);
        // when / then
        assertThatThrownBy(() -> seeding.seedTeam(first, teamIds.get(2), 1))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
        assertHome(first, teamIds.get(0));
        assertSelection(selected, teamIds.get(0));
    }

    @Test
    void givenTwoConfiguredHomes_whenSavingJointSeedSwap_thenCheckTheWholeFinalAssignment() {
        // given
        UUID selectedFirst = configurePlayoff(first, 0);
        UUID selectedSecond = configurePlayoff(second, 1);
        // when
        seeding.saveSeed(playoffId, List.of(
                new PlayoffSeedingService.SeedEntry(first, 1, teamIds.get(2), 1),
                new PlayoffSeedingService.SeedEntry(second, 1, teamIds.get(0), 2)));
        // then
        assertHome(first, teamIds.get(2));
        assertHome(second, teamIds.get(0));
        assertSelection(selectedFirst, teamIds.get(2));
        assertSelection(selectedSecond, teamIds.get(0));
    }

    @Test
    void givenConfiguredLegAndAutomaticNewHomeAlreadyConsumed_whenAutoSeeding_thenKeepSeedsAndParticipants() {
        // given
        UUID selected = configurePlayoff(first, 0);
        configureRegular(2);
        seeding.saveSeedNumbers(playoffId, Map.of(teamIds.get(2), 1, teamIds.get(3), 2, teamIds.get(0), 3, teamIds.get(1), 4));
        var before = tx.execute(status -> seeds.findByPlayoffId(playoffId).stream().map(PlayoffSeed::getId).toList());
        // when / then
        assertThatThrownBy(() -> seeding.autoSeedBracket(playoffId))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
        assertHome(first, teamIds.get(0));
        assertSelection(selected, teamIds.get(0));
        tx.executeWithoutResult(status -> assertThat(seeds.findByPlayoffId(playoffId)).extracting(PlayoffSeed::getId)
                .containsExactlyInAnyOrderElementsOf(before));
    }

    @Test
    void givenConfiguredFinalAndWinnerAlreadyConsumed_whenAdvancing_thenRejectBeforeDecisionHistoryChanges() {
        // given
        prepareFinal();
        UUID selected = configurePlayoff(finale, 0);
        configureRegular(1);
        // when / then
        assertThatThrownBy(() -> playoffService.setWinnerManually(first, teamIds.get(1), "Test adjudication"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
        tx.executeWithoutResult(status -> {
            var saved = matchups.findById(first).orElseThrow();
            assertThat(saved.getWinner()).isNull();
            assertThat(saved.getDecisionHistory()).isNull();
        });
        assertHome(finale, teamIds.get(0));
        assertSelection(selected, teamIds.get(0));
    }

    @Test
    void givenConfiguredFinalAndAdvancedHome_whenReopening_thenRejectBeforeClearingWinnerHistoryOrHome() {
        // given
        prepareFinal();
        UUID selected = configurePlayoff(finale, 0);
        playoffService.setWinnerManually(first, teamIds.get(0), "Test adjudication");
        String history = tx.execute(status -> matchups.findById(first).orElseThrow().getDecisionHistory());
        // when / then
        assertThatThrownBy(() -> playoffService.reopen(first, "Test bracket correction"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("home team");
        tx.executeWithoutResult(status -> {
            var saved = matchups.findById(first).orElseThrow();
            assertThat(saved.getWinner().getId()).isEqualTo(teamIds.get(0));
            assertThat(saved.getDecisionHistory()).isEqualTo(history);
        });
        assertHome(finale, teamIds.get(0));
        assertSelection(selected, teamIds.get(0));
    }

    @Test
    void givenCachedPairing_whenMetadataSavesAfterReorientation_thenKeepTheCommittedHomeAndSelection() {
        // given
        configure(graph.race().getId(), carIds.get(0));
        var writer = new TransactionTemplate(tx.getTransactionManager());
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            var cached = matches.findById(graph.match().getId()).orElseThrow();
            assertThat(cached.getHomeTeam().getId()).isEqualTo(teamIds.get(0));
            writer.executeWithoutResult(inner -> assertThat(raceService.saveRace(graph.race().getId(), graph.matchday().getId(),
                    teamIds.get(1), teamIds.get(0), null, carIds.get(0), null, 5, null, null, null, null, null,
                    TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, entry).success()).isTrue());
            // when
            matchService.markChannelArchived(graph.match().getId());
        });
        // then
        tx.executeWithoutResult(status -> {
            var saved = matches.findById(graph.match().getId()).orElseThrow();
            assertThat(saved.getHomeTeam().getId()).isEqualTo(teamIds.get(1));
            assertThat(saved.getDiscordChannelArchivedAt()).isNotNull();
        });
        assertSelection(graph.race().getId(), teamIds.get(1));
    }

    @Test
    void givenCachedPlayoffMatchup_whenScoresSaveAfterReseeding_thenKeepTheCommittedParticipant() {
        // given
        configurePlayoff(first, 0);
        tx.executeWithoutResult(status -> {
            var matchup = matchups.findById(first).orElseThrow();
            matchup.setHomeScore(3);
            matchup.setAwayScore(1);
        });
        var writer = new TransactionTemplate(tx.getTransactionManager());
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            var cached = matchups.findById(first).orElseThrow();
            assertThat(cached.getTeam1().getId()).isEqualTo(teamIds.get(0));
            writer.executeWithoutResult(inner -> seeding.seedTeam(first, teamIds.get(2), 1));
            // when
            scoring.recomputePlayoffMatchupScores(cached);
        });
        // then
        assertHome(first, teamIds.get(2));
        tx.executeWithoutResult(status -> {
            var saved = matchups.findById(first).orElseThrow();
            assertThat(saved.getHomeScore()).isNull();
            assertThat(saved.getAwayScore()).isNull();
        });
    }

    @Test
    void givenStandingsDerivedHomeAlreadyConsumed_whenAutoSeeding_thenKeepSeedsRosterAndParticipantUnchanged() {
        // given
        configurePlayoff(first, 0);
        configureRegular(2);
        tx.executeWithoutResult(status -> {
            var regular = races.findById(graph.race().getId()).orElseThrow().getMatchday().getPhase();
            for (var teamId : teamIds) phaseTeams.save(new PhaseTeam(regular, teams.findById(teamId).orElseThrow()));
            var selected = races.findByMatchdaySeasonId(graph.season().getId()).stream()
                    .filter(race -> race.getPlayoffMatchup() == null && race.getCarPoolEntry() != null).findFirst().orElseThrow();
            raceService.quickScore(selected.getId(), 3, 0);
            raceService.quickScore(graph.race().getId(), 0, 0);
        });
        var roster = tx.execute(status -> phaseTeams.findByPhaseId(playoffs.findById(playoffId).orElseThrow().getPhase().getId())
                .stream().map(PhaseTeam::getId).toList());
        assertThat(seeds.findByPlayoffId(playoffId)).isEmpty();
        // when / then
        assertThatThrownBy(() -> seeding.autoSeedBracket(playoffId))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
        assertHome(first, teamIds.get(0));
        tx.executeWithoutResult(status -> {
            assertThat(seeds.findByPlayoffId(playoffId)).isEmpty();
            assertThat(phaseTeams.findByPhaseId(playoffs.findById(playoffId).orElseThrow().getPhase().getId()))
                    .extracting(PhaseTeam::getId).containsExactlyInAnyOrderElementsOf(roster);
        });
    }

    private void prepareFinal() {
        tx.executeWithoutResult(status -> {
            var matchup = matchups.findById(finale).orElseThrow();
            matchup.setTeam1(teams.findById(teamIds.get(0)).orElseThrow());
            matchup.setTeam2(teams.findById(teamIds.get(2)).orElseThrow());
        });
    }

    private UUID configurePlayoff(UUID matchup, int carIndex) {
        UUID raceId = playoffService.addRaceToMatchup(matchup, null, null, null).getId();
        configure(raceId, carIds.get(carIndex));
        return raceId;
    }

    private void configureRegular(int homeIndex) {
        UUID raceId = tx.execute(status -> {
            var md = races.findById(graph.race().getId()).orElseThrow().getMatchday();
            var match = matchService.createMatchWithLegs(md, teams.findById(teamIds.get(homeIndex)).orElseThrow(),
                    teams.findById(teamIds.get(3)).orElseThrow(), false);
            return races.findByMatchId(match.getId()).getFirst().getId();
        });
        configure(raceId, carIds.get(1));
    }

    private void configure(UUID raceId, UUID car) {
        var context = tx.execute(status -> {
            var race = races.findById(raceId).orElseThrow();
            return List.of(race.getMatchday().getId(), race.getHomeTeam().getId(), race.getAwayTeam().getId());
        });
        assertThat(raceService.saveRace(raceId, context.get(0), context.get(1), context.get(2), null, car, null,
                5, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, entry).success()).isTrue();
    }

    private void assertHome(UUID matchup, UUID home) {
        tx.executeWithoutResult(status -> assertThat(matchups.findById(matchup).orElseThrow().getTeam1().getId()).isEqualTo(home));
    }

    private void assertSelection(UUID raceId, UUID home) {
        tx.executeWithoutResult(status -> {
            var saved = races.findById(raceId).orElseThrow();
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(saved.getCar()).isNotNull();
            assertThat(saved.getHomeTeam().getId()).isEqualTo(home);
        });
    }
}
