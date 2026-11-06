package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class DisqualificationCorrectionIT extends RacePenaltyFixture {
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired RaceRepository raceRows;
    @Autowired MatchService matchService;
    @Autowired MatchdayService matchdayService;
    @Autowired RaceDisqualificationLifecycleGuard dsqGuard;
    @Autowired org.ctc.dataimport.CsvImportService csv;
    @Autowired RaceLineupService lineupService;
    @Autowired PlayoffService playoffs;
    @Autowired org.ctc.domain.repository.PlayoffMatchupRepository matchups;
    @Autowired ScoringService scoring;
    @Autowired DriverRankingService rankings;
    @Autowired StandingsService standings;
    @Autowired SeasonManagementService management;
    @Autowired DriverMergeService merges;

    UUID resultId() {
        return tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
                .filter(r -> r.getDriver().getId().equals(homeDriver)).findFirst().orElseThrow().getId());
    }

    RaceDisqualificationService.Correction command(RaceDisqualificationService.View view, boolean disqualified) {
        return new RaceDisqualificationService.Correction(disqualified, view.revision(), view.fingerprint(), view.generation(), "Test private DSQ reason <script>");
    }

    @Test
    void givenPlayedRace_whenSettingAndClearingDsq_thenShiftRacePointsKeepBonusesAndManualPenaltyTotals() {
        // given
        tx.executeWithoutResult(status -> {
            var scheme = helper.getRaceScoring(seasons.findById(seasonId).orElseThrow());
            scheme.setQualiPoints("3,2,1"); scheme.setFastestLapPoints(2);
        });
        races.saveResults(raceId, List.of(new RaceService.RaceResultData(homeDriver, null, null, 1, 1, true),
                new RaceService.RaceResultData(awayDriver, null, null, 2, 2, false)));
        var penalty = penalties.view(raceId);
        penalties.save(raceId, new RacePenaltyService.Correction(homeId, awayId, 20, 10, penalty.revision(),
                penalty.fingerprint(), penalty.generation(), "Test manual team totals"), "test-manager");
        var id = resultId(); var original = disqualifications.view(raceId, id);
        // when
        assertThat(disqualifications.save(raceId, id, command(original, true), "test-manager")).isEqualTo(1);
        // then
        var dsq = disqualifications.view(raceId, id);
        assertThat(dsq.disqualified()).isTrue(); assertThat(dsq.history()).hasSize(1);
        assertThat(dsq.result().getPosition()).isEqualTo(1); assertThat(dsq.result().getClassificationLabel()).isEqualTo("DSQ");
        assertThat(dsq.result().getPointsRace()).isZero(); assertThat(dsq.result().getPointsQuali()).isEqualTo(3);
        assertThat(dsq.result().getPointsFl()).isEqualTo(2); assertThat(dsq.result().getPointsTotal()).isEqualTo(5);
        assertThat(dsq.history().getFirst().getActorId()).isEqualTo("test-manager");
        assertThat(penalties.view(raceId).totals().home().penalty()).isEqualTo(20);
        assertThat(penalties.view(raceId).totals().away().penalty()).isEqualTo(10);
        assertScores(0, 82);
        // when
        disqualifications.save(raceId, id, command(dsq, false), "test-manager");
        // then
        var cleared = disqualifications.view(raceId, id);
        assertThat(cleared.disqualified()).isFalse(); assertThat(cleared.history()).hasSize(2);
        assertThat(cleared.result().getPosition()).isEqualTo(1); assertThat(cleared.result().getPointsTotal()).isEqualTo(85);
        assertThat(cleared.history().getLast().isPreviousDisqualified()).isTrue();
        assertScores(75, 72);
    }

    @Test
    void givenDsqHistory_whenEditingOriginalResults_thenKeepResultAndHistoryAndRecomputeCompleteClassification() {
        // given
        var id = resultId(); disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        // when
        races.saveResults(raceId, results(true));
        // then
        var current = disqualifications.view(raceId, id);
        assertThat(current.disqualified()).isTrue(); assertThat(current.history()).hasSize(1);
        assertThat(current.result().getPosition()).isEqualTo(2); assertThat(current.result().getPointsRace()).isZero();
        assertThat(current.history().getFirst().getRecordedPosition()).isEqualTo(1);
        assertScores(0, 80);
        assertThatThrownBy(() -> races.saveResults(raceId, List.of())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("permanent DSQ history");
        assertThat(disqualifications.view(raceId, id).history()).hasSize(1); assertScores(0, 80);
    }

    @Test
    void givenStaleRevisionResultsOrRestoreGeneration_whenSaving_thenRejectBeforeMutation() {
        // given
        var id = resultId(); var original = disqualifications.view(raceId, id);
        disqualifications.save(raceId, id, command(original, true), "test-manager");
        // when / then
        assertThatThrownBy(() -> disqualifications.save(raceId, id, command(original, false), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("history changed");
        var current = disqualifications.view(raceId, id);
        races.saveResults(raceId, results(true));
        assertThatThrownBy(() -> disqualifications.save(raceId, id, command(current, false), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("results changed");
        var reloaded = disqualifications.view(raceId, id); var stale = command(reloaded, false); epoch.afterRestore(null);
        assertThatThrownBy(() -> disqualifications.save(raceId, id, stale, "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no longer current");
        assertThat(disqualifications.view(raceId, id).history()).hasSize(1); assertScores(0, 80);
    }

    @Test
    void givenInvalidInputOrForeignResult_whenSaving_thenLeaveResultsAndHistoryUnchanged() {
        // given
        var id = resultId(); var view = disqualifications.view(raceId, id);
        // when / then
        assertThatThrownBy(() -> disqualifications.save(raceId, UUID.randomUUID(), command(view, true), "test-manager"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> disqualifications.save(raceId, id, command(view, true), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("trusted manager");
        assertThatThrownBy(() -> disqualifications.save(raceId, id, new RaceDisqualificationService.Correction(true,
                view.revision(), view.fingerprint(), view.generation(), " "), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> disqualifications.save(raceId, id, command(view, false), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no DSQ to clear");
        assertThat(disqualifications.view(raceId, id).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenClearedDsqHistory_whenDeletingForfeitingOrQuickScoring_thenProtectPermanentEvidence() {
        // given
        var id = resultId(); disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), false), "test-manager");
        var day = disqualifications.view(raceId, id).race().getMatchday().getId();
        // when / then
        assertThatThrownBy(() -> races.deleteRace(raceId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatThrownBy(() -> matchService.deleteMatch(matchId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatThrownBy(() -> matchdayService.deleteMatchday(day)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatThrownBy(() -> races.quickScore(raceId, 99, 1)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThat(disqualifications.view(raceId, id).history()).hasSize(2); assertScores(80, 70);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenCurrentOrClearedDsqOnOneLeg_whenReplacingCsvOrSheetResults_thenRejectWholeImportBeforeChangingAnotherLeg(boolean cleared) {
        // given
        var other = tx.execute(status -> {
            var original = raceRows.findById(raceId).orElseThrow(); var leg = helper.createRace(original.getMatchday(), original.getMatch());
            lineups.saveAllAndFlush(List.of(new org.ctc.domain.model.RaceLineup(leg, drivers.findById(homeDriver).orElseThrow(), teams.findById(homeId).orElseThrow()),
                    new org.ctc.domain.model.RaceLineup(leg, drivers.findById(awayDriver).orElseThrow(), teams.findById(awayId).orElseThrow())));
            races.saveResults(leg.getId(), results(false)); return leg.getId();
        });
        var id = resultId(); disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        if (cleared) disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), false), "test-manager");
        var originalIds = tx.execute(status -> raceRows.findById(other).orElseThrow().getResults().stream().map(r -> r.getId()).sorted().toList());
        var preview = tx.execute(status -> {
            var race = raceRows.findById(raceId).orElseThrow();
            String source = "Team,PSN ID,Position,Quali,FL\n%s,%s,1,1,false\n%s,%s,2,2,false\n".formatted(
                    race.getAwayTeam().getShortName(), drivers.findById(awayDriver).orElseThrow().getPsnId(),
                    race.getHomeTeam().getShortName(), drivers.findById(homeDriver).orElseThrow().getPsnId());
            try { return csv.parseAndPreview(new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    new org.ctc.dataimport.CsvImportService.ImportMetadata(seasonId, null, null, null, null, race.getMatchday().getId())); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        // when / then
        assertThatThrownBy(() -> csv.executeMultiRaceImport(List.of(preview), java.util.Map.of(), java.util.Set.of(), true))
                .isInstanceOf(org.ctc.dataimport.exception.ImportRejectedException.class).hasMessageContaining("permanent DSQ history");
        tx.executeWithoutResult(status -> assertThat(raceRows.findById(other).orElseThrow().getResults().stream().map(r -> r.getId()).sorted().toList()).isEqualTo(originalIds));
        assertThat(disqualifications.view(raceId, id).history()).hasSize(cleared ? 2 : 1); assertScores(cleared ? 160 : 80, cleared ? 140 : 150);
    }

    @Test
    void givenStandalonePlayoff_whenDisqualifyingWinner_thenReaggregateItsScoresAndRequireReopeningBeforeClearing() {
        // given
        var legId = tx.execute(status -> {
            var playoff = playoffs.createPlayoff(seasonId, "Test DSQ final", 2);
            var matchup = matchups.findByRoundPlayoffId(playoff.getId()).getFirst();
            matchup.setTeam1(teams.findById(homeId).orElseThrow()); matchup.setTeam2(teams.findById(awayId).orElseThrow());
            var race = playoffs.addRaceToMatchup(matchup.getId(), null, null, null);
            lineups.saveAllAndFlush(List.of(new org.ctc.domain.model.RaceLineup(race, drivers.findById(homeDriver).orElseThrow(), teams.findById(homeId).orElseThrow()),
                    new org.ctc.domain.model.RaceLineup(race, drivers.findById(awayDriver).orElseThrow(), teams.findById(awayId).orElseThrow())));
            races.saveResults(race.getId(), results(false)); return race.getId();
        });
        var id = tx.execute(status -> raceRows.findById(legId).orElseThrow().getResults().stream().filter(r -> r.getDriver().getId().equals(homeDriver)).findFirst().orElseThrow().getId());
        // when
        disqualifications.save(legId, id, command(disqualifications.view(legId, id), true), "test-manager");
        var matchup = tx.execute(status -> {
            var race = raceRows.findById(legId).orElseThrow(); assertThat(race.getMatch()).isNull();
            assertThat(race.getPlayoffMatchup().getHomeScore()).isZero(); assertThat(race.getPlayoffMatchup().getAwayScore()).isEqualTo(80);
            return race.getPlayoffMatchup().getId();
        });
        playoffs.determineWinner(matchup);
        // then
        var decided = disqualifications.view(legId, id); assertThat(decided.correctable()).isFalse();
        assertThatThrownBy(() -> disqualifications.save(legId, id, command(decided, false), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("decided");
        assertThat(disqualifications.view(legId, id).history()).hasSize(1);
        tx.executeWithoutResult(status -> assertThat(matchups.findById(matchup).orElseThrow().getWinner().getId()).isEqualTo(awayId));
        assertScores(80, 70);
    }

    @Test
    void givenRestoreWriterAdmissionHeld_whenSavingDsq_thenRejectBeforeOpeningItsTransaction() {
        // given
        var id = resultId(); var view = disqualifications.view(raceId, id); assertThat(imports.tryLock()).isTrue();
        // when / then
        try { assertThatThrownBy(() -> disqualifications.save(raceId, id, command(view, true), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore"); }
        finally { imports.unlock(); }
        assertThat(disqualifications.view(raceId, id).history()).isEmpty(); assertScores(80, 70);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenInitializedResultBeforeConcurrentDsq_whenSavingNativeResultsOrLineups_thenRetainCurrentStateOrRejectRetry(boolean lineup) {
        // given
        var id = resultId(); var view = disqualifications.view(raceId, id);
        tx.executeWithoutResult(status -> {
            var cached = raceRows.findById(raceId).orElseThrow();
            assertThat(cached.getResults()).allSatisfy(result -> { assertThat(result.isDisqualified()).isFalse(); assertThat(result.getPointsRace()).isPositive(); });
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                var pending = executor.submit(() -> disqualifications.save(raceId, id, command(view, true), "concurrent-manager"));
                try { assertThat(pending.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1); }
                catch (Exception failure) { throw new AssertionError("Concurrent DSQ must commit before testing the cached writer", failure); }
            }
            // when / then
            try {
                if (lineup) lineupService.saveLineup(raceId, java.util.Map.of(homeDriver, homeId), java.util.Map.of(awayDriver, awayId));
                else races.saveResults(raceId, results(false));
                var current = raceRows.findById(raceId).orElseThrow().getResults().stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
                assertThat(current.isDisqualified()).isTrue(); assertThat(current.getPointsRace()).isZero();
                var match = matches.findById(matchId).orElseThrow(); assertThat(match.getHomeScore()).isZero(); assertThat(match.getAwayScore()).isEqualTo(80);
            } catch (BusinessRuleException retry) { assertThat(retry.getMessage().toLowerCase()).containsAnyOf("changed", "reload"); }
            finally { status.setRollbackOnly(); }
        });
        assertThat(disqualifications.view(raceId, id).disqualified()).isTrue(); assertThat(disqualifications.view(raceId, id).history()).hasSize(1); assertScores(0, 80);
    }

    @Test
    void givenDsqWinner_whenReadingStandingsRankingsAndSwissEntry_thenUseCorrectedScoresAndRequireDriverResults() {
        // given
        var id = resultId(); disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        var phase = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getMatchday().getPhase().getId());
        // when / then
        assertThat(standings.calculateStandings(phase, null)).filteredOn(r -> r.getTeam().getId().equals(awayId))
                .singleElement().satisfies(r -> { assertThat(r.getWins()).isEqualTo(1); assertThat(r.getPointsFor()).isEqualTo(80); });
        for (var ranking : List.of(rankings.calculateRankingForPhase(phase), rankings.calculateAlltimeRanking(List.of(seasonId)))) {
            assertThat(ranking).filteredOn(r -> r.getDriver().getId().equals(homeDriver)).singleElement().satisfies(r -> {
                assertThat(r.getTotalPoints()).isZero(); assertThat(r.getBestPosition()).isZero(); assertThat(r.getBestPositionLabel()).isEqualTo("-");
            });
            assertThat(ranking).filteredOn(r -> r.getDriver().getId().equals(awayDriver)).singleElement().satisfies(r -> {
                assertThat(r.getTotalPoints()).isEqualTo(80); assertThat(r.getBestPosition()).isEqualTo(1);
            });
        }
        assertThat(management.getSwissRoundData(seasonId).pairings().values().stream().flatMap(List::stream)
                .filter(pair -> pair.match().getId().equals(matchId))).singleElement().satisfies(pair -> {
                    assertThat(pair.resultEntryRequired()).isTrue(); assertThat(pair.quickScoreRaceId()).isNull();
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"state", "revision", "negative-revision", "long-actor", "long-reason", "generation", "fingerprint"})
    void givenInvalidRequiredManagementFields_whenSavingDsq_thenRejectWithoutHistoryOrPointMutation(String kind) {
        // given
        var id = resultId(); var view = disqualifications.view(raceId, id);
        var correction = new RaceDisqualificationService.Correction(kind.equals("state") ? null : true,
                kind.equals("revision") ? null : kind.equals("negative-revision") ? -1 : view.revision(),
                kind.equals("fingerprint") ? null : view.fingerprint(), kind.equals("generation") ? "stale" : view.generation(),
                kind.equals("long-reason") ? "R".repeat(2001) : "Test input decision");
        String expected = switch (kind) {
            case "long-actor" -> "trusted manager"; case "long-reason" -> "reason"; case "generation" -> "no longer current";
            case "fingerprint" -> "results changed"; default -> "current revision";
        };
        // when / then
        assertThatThrownBy(() -> disqualifications.save(raceId, id, correction, kind.equals("long-actor") ? "A".repeat(201) : "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(expected);
        assertThat(disqualifications.view(raceId, id).history()).isEmpty(); assertScores(80, 70);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenCurrentOrClearedSourceDsq_whenMergingDrivers_thenPreserveResultDriverOwnership(boolean cleared) {
        // given
        var id = resultId(); disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        if (cleared) disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), false), "test-manager");
        // when / then
        assertThatThrownBy(() -> merges.merge(homeDriver, awayDriver)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThat(drivers.existsById(homeDriver)).isTrue();
        assertThat(disqualifications.view(raceId, id).history()).hasSize(cleared ? 2 : 1);
        assertThat(disqualifications.view(raceId, id).result().getDriver().getId()).isEqualTo(homeDriver);
        assertScores(cleared ? 80 : 0, cleared ? 70 : 80);
    }

    @Test
    void givenTwoManagersWithSameRevision_whenCorrectingConcurrently_thenCommitExactlyOneHistoryRevision() throws Exception {
        // given
        var id = resultId(); var command = command(disqualifications.view(raceId, id), true);
        var ready = new java.util.concurrent.CountDownLatch(2); var go = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> submit = () -> {
                ready.countDown(); assertThat(go.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                try { disqualifications.save(raceId, id, command, "concurrent-manager"); return "accepted"; }
                catch (BusinessRuleException rejected) { return rejected.getMessage(); }
            };
            var first = executor.submit(submit); var second = executor.submit(submit);
            // when
            try { assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); } finally { go.countDown(); }
            var outcomes = List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            // then
            assertThat(outcomes).filteredOn("accepted"::equals).hasSize(1);
            assertThat(outcomes).anyMatch(message -> message.contains("changed") || message.contains("reload"));
        }
        assertThat(disqualifications.view(raceId, id).history()).hasSize(1); assertScores(0, 80);
    }
}
