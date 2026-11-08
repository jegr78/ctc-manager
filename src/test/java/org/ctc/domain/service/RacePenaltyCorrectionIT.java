package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.RacePenaltyRevision;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class RacePenaltyCorrectionIT extends RacePenaltyFixture {
    @org.springframework.beans.factory.annotation.Autowired MatchService matchService;
    @org.springframework.beans.factory.annotation.Autowired MatchdayService matchdayService;

    @org.springframework.beans.factory.annotation.Autowired org.ctc.domain.repository.RaceRepository raceRows;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ScoringService scoring;
    @org.springframework.beans.factory.annotation.Autowired StandingsService standings;
    @org.springframework.beans.factory.annotation.Autowired org.ctc.dataimport.CsvImportService csv;
    @org.springframework.beans.factory.annotation.Autowired PlayoffService playoffs;
    @org.springframework.beans.factory.annotation.Autowired org.ctc.domain.repository.PlayoffMatchupRepository matchupRows;

    @org.springframework.beans.factory.annotation.Autowired RacePenaltyLifecycleGuard penaltyGuard;
    @org.springframework.beans.factory.annotation.Autowired SeasonManagementService seasonManagement;

    @org.springframework.beans.factory.annotation.Autowired RaceLineupService lineupService;

    @Test void givenPlayoffLinkedRegularAnchor_whenDeclaringRegularWalkover_thenRejectWithoutChangingEitherIdentity() {
        UUID matchup = attachPlayoff();
        assertThatThrownBy(() -> matchService.updateWalkover(matchId, homeId))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("playoff");
        tx.executeWithoutResult(status -> {
            assertThat(matches.findById(matchId).orElseThrow().getWalkoverTeam()).isNull();
            assertThat(matchupRows.findById(matchup).orElseThrow().getWalkoverTeam()).isNull();
        });
        assertScores(80, 70);
    }

    @Test
    void givenViewOverlappingCorrection_whenReturningForm_thenKeepAmountsAndRevisionFromOneSnapshot() throws Exception {
        // given
        var original = penalties.view(raceId);
        // when
        var viewed = viewOverlapping(() -> penalties.save(raceId, command(original, 20, 10), "concurrent-manager"));
        // then
        assertThat(viewed.revision()).isZero();
        assertThat(viewed.history()).isEmpty();
        assertThat(viewed.totals().home().penalty()).isZero();
        assertThat(viewed.totals().away().penalty()).isZero();
        assertThatThrownBy(() -> penalties.save(raceId, command(viewed, 0, 0), "stale-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Penalty history changed");
        assertThat(penalties.view(raceId).history()).hasSize(1);
        assertScores(70, 70);
    }

    @Test
    void givenViewOverlappingGuestTeamCorrection_whenReturningForm_thenKeepFingerprintOfDisplayedPoints() throws Exception {
        // given
        var original = penalties.view(raceId);
        // when
        var viewed = viewOverlapping(() -> lineupService.saveLineup(raceId,
                java.util.Map.of(homeDriver, homeId), java.util.Map.of(awayDriver, homeId)));
        // then
        assertThat(viewed.totals().home().gross()).isEqualTo(80);
        assertThat(viewed.totals().away().gross()).isEqualTo(70);
        assertThat(viewed.fingerprint()).isEqualTo(original.fingerprint());
        assertThatThrownBy(() -> penalties.save(raceId, command(viewed, 20, 10), "stale-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Race results changed");
        assertThat(penalties.view(raceId).history()).isEmpty();
        assertScores(150, 0);
    }

    private RacePenaltyService.View viewOverlapping(Runnable writer) throws Exception {
        var calculated = new java.util.concurrent.CountDownLatch(1);
        var released = new java.util.concurrent.CountDownLatch(1);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> {
            var totals = call.callRealMethod();
            if (first.getAndSet(false)) {
                calculated.countDown();
                assertThat(released.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            return totals;
        }).when(scoring).calculateRaceScoreSnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(homeId));
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> penalties.view(raceId));
            try {
                assertThat(calculated.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                writer.run();
            } finally { released.countDown(); }
            return pending.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenInitializedEmptyHistory_whenAnotherManagerCommits_thenCurrentReadsProtectRaceAndPlayoffIdentity(boolean playoffIdentity) {
        // given
        var matchup = attachPlayoff(); var original = penalties.view(raceId);
        tx.executeWithoutResult(status -> {
            var cachedRace = raceRows.findById(raceId).orElseThrow(); var cachedMatchup = matchupRows.findById(matchup).orElseThrow();
            assertThat(cachedRace.getPenaltyRevisions()).isEmpty();
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                // when
                var pending = executor.submit(() -> penalties.save(raceId, command(original, 0, 0), "concurrent-manager"));
                try { assertThat(pending.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1); }
                catch (Exception failure) { throw new AssertionError("Concurrent correction must commit before the stale-context check", failure); }
            }
            // then
            assertThat(cachedRace.getPenaltyRevisions()).isEmpty();
            try {
                if (playoffIdentity) {
                    assertThatThrownBy(() -> penaltyGuard.requirePlayoffIdentity(cachedMatchup, teams.findById(awayId).orElseThrow(), teams.findById(homeId).orElseThrow()))
                            .isInstanceOfSatisfying(BusinessRuleException.class, failure -> assertThat(failure.getMessage()).satisfiesAnyOf(
                                    message -> assertThat(message).contains("penalty"), message -> assertThat(message).contains("changed", "Reload")));
                } else {
                    try {
                        assertThat(penaltyGuard.protects(cachedRace)).isTrue();
                        assertThatThrownBy(() -> penaltyGuard.requireNoHistory(List.of(cachedRace))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
                    } catch (BusinessRuleException failure) { assertThat(failure.getMessage()).contains("changed", "Reload"); }
                }
            } finally { status.setRollbackOnly(); }
        });
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(80, 70);
    }

    @Test
    void givenHistoricalPlayoffWalkoverWithoutWinner_whenCorrectingPenalties_thenRejectWithoutCreatingHistory() {
        // given
        UUID matchup = attachPlayoff();
        tx.executeWithoutResult(status -> matchupRows.findById(matchup).orElseThrow().setWalkoverTeam(teams.findById(homeId).orElseThrow()));
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("played race");
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenPenaltyOnAnotherLeg_whenEnteringQuickScore_thenCannotOverwriteOfficialMatchTotals() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        var other = tx.execute(status -> { var original = raceRows.findById(raceId).orElseThrow(); return helper.createRace(original.getMatchday(), original.getMatch()).getId(); });
        // when / then
        assertThatThrownBy(() -> races.quickScore(other, 99, 1)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertScores(70, 70); assertThat(penalties.view(raceId).history()).hasSize(1);
        // when
        races.saveResults(raceId, List.of());
        // then
        assertThatThrownBy(() -> races.quickScore(raceId, 99, 1)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertThat(penalties.view(raceId).history()).hasSize(1);
        assertThat(seasonManagement.getSwissRoundData(seasonId).pairings().values().stream().flatMap(List::stream).filter(pairing -> pairing.match().getId().equals(matchId)))
                .singleElement().satisfies(pairing -> { assertThat(pairing.quickScoreRaceId()).isNull(); assertThat(pairing.resultEntryRequired()).isTrue(); });
        tx.executeWithoutResult(status -> { var match = matches.findById(matchId).orElseThrow(); assertThat(match.getHomeScore()).isNull(); assertThat(match.getAwayScore()).isNull(); });
    }

    @Test
    void givenPenalizedLegClearedWhileAnotherLegHasResults_whenListingRaces_thenDoNotSubstituteWholeMatchScoreForEmptyLeg() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        var other = tx.execute(status -> {
            var original = raceRows.findById(raceId).orElseThrow(); var leg = helper.createRace(original.getMatchday(), original.getMatch());
            lineups.saveAllAndFlush(List.of(new org.ctc.domain.model.RaceLineup(leg, drivers.findById(homeDriver).orElseThrow(), teams.findById(homeId).orElseThrow()),
                    new org.ctc.domain.model.RaceLineup(leg, drivers.findById(awayDriver).orElseThrow(), teams.findById(awayId).orElseThrow())));
            races.saveResults(leg.getId(), results(false)); return leg.getId();
        });
        // when
        races.saveResults(raceId, List.of());
        // then
        assertScores(80, 70);
        tx.executeWithoutResult(status -> {
            var scores = races.getRaceListData(null, seasonId).raceScores();
            assertThat(scores).doesNotContainKey(raceId); assertThat(scores.get(other)).containsExactly(80, 70);
        });
        assertThat(penalties.view(raceId).history()).hasSize(1);
    }

    @Test
    void givenPermanentPenaltyHistory_whenSwappingOrMovingRace_thenRejectButAllowScheduleMetadata() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 0, 0), "test-manager");
        var originalDay = penalties.view(raceId).race().getMatchday().getId();
        var otherDay = tx.execute(status -> helper.createMatchday(seasons.findById(seasonId).orElseThrow(), "Test penalty later day", 2).getId());
        races.saveResults(raceId, List.of());
        // when / then
        for (var attempted : List.of(saveRace(originalDay, awayId, homeId), saveRace(otherDay, homeId, awayId))) {
            assertThat(attempted.success()).isFalse(); assertThat(attempted.message()).contains("penalty");
        }
        assertThat(saveRace(originalDay, homeId, awayId).success()).isTrue();
        assertThat(penalties.view(raceId).race().getMatchday().getId()).isEqualTo(originalDay);
        races.saveResults(raceId, results(false));
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(80, 70);
    }

    private RaceService.SaveResult saveRace(UUID day, UUID home, UUID away) {
        return races.saveRace(raceId, day, home, away, null, null, java.time.LocalDateTime.of(2036, 1, 1, 20, 0),
                12, null, null, null, null, null, org.ctc.domain.model.TimeProgressionMode.MULTIPLIER, 0, null, null, null, null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null-actor", "blank-actor", "long-actor", "blank-reason", "long-reason", "null-home", "null-away", "negative-away", "negative-revision"})
    void givenInvalidCorrectionMetadata_whenSaving_thenRejectWithoutAppendingOrChangingScores(String invalid) {
        // given
        var view = penalties.view(raceId);
        String actor = switch (invalid) { case "null-actor" -> null; case "blank-actor" -> " "; case "long-actor" -> "a".repeat(201); default -> "test-manager"; };
        var form = new RacePenaltyService.Correction(homeId, awayId, invalid.equals("null-home") ? null : 20,
                invalid.equals("null-away") ? null : invalid.equals("negative-away") ? -1 : 10,
                invalid.equals("negative-revision") ? -1 : view.revision(), view.fingerprint(), view.generation(),
                invalid.equals("blank-reason") ? " " : invalid.equals("long-reason") ? "r".repeat(2001) : "Test reason");
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, form, actor)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(invalid.contains("actor") ? "identity" : invalid.contains("reason") ? "reason" : invalid.contains("revision") ? "revision" : "nonnegative");
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenOppositelyOrientedLegsWithGuests_whenSavingBothPenalties_thenOffsetWithinEachLegBeforeAggregating() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        var otherId = tx.execute(status -> {
            var original = raceRows.findById(raceId).orElseThrow();
            var other = helper.createRace(original.getMatchday(), original.getMatch());
            other.setHomeTeamOverride(teams.findById(awayId).orElseThrow()); other.setAwayTeamOverride(teams.findById(homeId).orElseThrow());
            lineups.saveAllAndFlush(List.of(new org.ctc.domain.model.RaceLineup(other, drivers.findById(homeDriver).orElseThrow(), teams.findById(homeId).orElseThrow()),
                    new org.ctc.domain.model.RaceLineup(other, drivers.findById(awayDriver).orElseThrow(), teams.findById(awayId).orElseThrow(), true)));
            String message = races.saveResults(other.getId(), results(true));
            assertThat(message).contains(other.getHomeTeam().getShortName() + " 80 : 70 " + other.getAwayTeam().getShortName());
            return other.getId();
        });
        var view = penalties.view(otherId);
        // when
        penalties.save(otherId, new RacePenaltyService.Correction(awayId, homeId, 5, 20, view.revision(), view.fingerprint(), view.generation(), "Other leg"), "test-manager");
        tx.executeWithoutResult(status -> { scoring.recomputeMatchScores(matches.findById(matchId).orElseThrow()); scoring.recomputeMatchScores(matches.findById(matchId).orElseThrow()); });
        // then
        assertScores(125, 150);
        assertThat(penalties.view(otherId).totals().home().official()).isEqualTo(80);
        assertThat(penalties.view(otherId).totals().away().official()).isEqualTo(55);
        tx.executeWithoutResult(status -> assertThat(scoring.calculateTeamTotals(raceRows.findById(otherId).orElseThrow().getResults(), otherId, homeId)).containsExactly(55, 80));
    }

    @Test
    void givenNettedDraw_whenComputingSeasonAndAlltimeStandings_thenUseOfficialPointsAndDraw() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        // when / then
        tx.executeWithoutResult(status -> {
            var race = raceRows.findById(raceId).orElseThrow(); var season = seasons.findById(seasonId).orElseThrow();
            for (var table : List.of(standings.calculateStandings(race.getMatchday().getPhase().getId(), null), standings.calculateAlltimeStandings(List.of(season)))) {
                assertThat(table).hasSize(2);
                assertThat(table).allSatisfy(row -> { assertThat(row.getPointsFor()).isEqualTo(70); assertThat(row.getPointsAgainst()).isEqualTo(70); assertThat(row.getDraws()).isEqualTo(1); });
            }
        });
    }

    @Test
    void givenCorrectionAndReplacementCsv_whenImporting_thenReuseRaceIdentityAndRecalculateFromNewDriverPoints() throws Exception {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        var preview = tx.execute(status -> {
            var race = raceRows.findById(raceId).orElseThrow();
            var content = "Team,PSN ID,Position,Quali,FL\n%s,%s,1,1,false\n%s,%s,2,2,false\n".formatted(
                    race.getAwayTeam().getShortName(), drivers.findById(awayDriver).orElseThrow().getPsnId(), race.getHomeTeam().getShortName(), drivers.findById(homeDriver).orElseThrow().getPsnId());
            try { return csv.parseAndPreview(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    new org.ctc.dataimport.CsvImportService.ImportMetadata(seasonId, null, null, null, null, race.getMatchday().getId())); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        // when
        assertThat(csv.executeImport(preview, java.util.Map.of(), java.util.Set.of(), true).hasErrors()).isFalse();
        // then
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(60, 80);
        tx.executeWithoutResult(status -> assertThat(raceRows.findByMatchId(matchId)).extracting(org.ctc.domain.model.Race::getId).containsExactly(raceId));
    }

    @Test
    void givenTwoManagersWithSameRevision_whenSavingConcurrently_thenAcceptExactlyOneCorrection() throws Exception {
        // given
        var view = penalties.view(raceId); var command = command(view, 20, 10);
        var ready = new java.util.concurrent.CountDownLatch(2); var go = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> submit = () -> {
                ready.countDown(); assertThat(go.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                try { penalties.save(raceId, command, "concurrent-manager"); return "accepted"; }
                catch (BusinessRuleException failure) { return failure.getMessage(); }
            };
            var first = executor.submit(submit); var second = executor.submit(submit);
            // when
            try { assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); } finally { go.countDown(); }
            var outcomes = List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            // then
            assertThat(outcomes).filteredOn("accepted"::equals).hasSize(1);
            assertThat(outcomes).anyMatch(message -> message.contains("changed") || message.contains("reload"));
        }
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(70, 70);
    }

    private UUID attachPlayoff() {
        return tx.execute(status -> {
            var playoff = playoffs.createPlayoff(seasonId, "Test penalty final", 2);
            var matchup = matchupRows.findByRoundPlayoffId(playoff.getId()).getFirst();
            matchup.setTeam1(teams.findById(homeId).orElseThrow()); matchup.setTeam2(teams.findById(awayId).orElseThrow());
            raceRows.findById(raceId).orElseThrow().setPlayoffMatchup(matchup); matchupRows.flush();
            scoring.recomputeMatchScoresFromAllLegs(raceRows.findById(raceId).orElseThrow()); return matchup.getId();
        });
    }

    @Test
    void givenPenaltyHistoryThenClearedResults_whenDeclaringPlayoffWalkover_thenRejectAndKeepOpenHistory() {
        // given
        UUID matchup = attachPlayoff(); penalties.save(raceId, command(penalties.view(raceId), 0, 0), "test-manager");
        races.saveResults(raceId, List.of());
        // when / then
        assertThat(playoffs.getMatchupDetail(matchup).walkoverAllowed()).isFalse();
        assertThatThrownBy(() -> playoffs.declareWalkover(matchup, homeId, "Walkover"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        tx.executeWithoutResult(status -> { var stored = matchupRows.findById(matchup).orElseThrow(); assertThat(stored.getWinner()).isNull(); assertThat(stored.getWalkoverTeam()).isNull(); });
        assertThat(penalties.view(raceId).history()).hasSize(1);
    }

    @Test
    void givenDecidedPlayoffWithOfficialNettedWinner_whenCorrecting_thenRequireExplicitReopening() {
        // given
        UUID matchup = attachPlayoff(); penalties.save(raceId, command(penalties.view(raceId), 20, 0), "test-manager");
        playoffs.determineWinner(matchup);
        tx.executeWithoutResult(status -> assertThat(matchupRows.findById(matchup).orElseThrow().getWinner().getId()).isEqualTo(awayId));
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(penalties.view(raceId), 0, 0), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Reopen");
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(60, 70);
        // when
        playoffs.reopen(matchup, "Recheck penalty totals"); penalties.save(raceId, command(penalties.view(raceId), 0, 0), "test-manager");
        // then
        assertScores(80, 70); assertThat(penalties.view(raceId).history()).hasSize(2);
    }

    @Test
    void givenSavedPenalty_whenReadingRaceDetailAndList_thenUseOfficialLegScores() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        // when / then
        tx.executeWithoutResult(status -> {
            var detail = races.getRaceDetailData(raceId);
            assertThat(detail.homeTotal()).isEqualTo(70);
            assertThat(detail.awayTotal()).isEqualTo(70);
            assertThat(races.getRaceListData(null, seasonId).raceScores().get(raceId)).containsExactly(70, 70);
        });
    }

    @Test
    void givenPenaltyResetToZero_whenDeletingRaceOrPairingOrMatchday_thenKeepPermanentHistory() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 0, 0), "test-manager");
        var day = penalties.view(raceId).race().getMatchday().getId();
        // when / then
        assertThatThrownBy(() -> races.deleteRace(raceId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertThatThrownBy(() -> matchService.deleteMatch(matchId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertThatThrownBy(() -> matchdayService.deleteMatchday(day)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertThat(penalties.view(raceId).history()).hasSize(1);
        assertScores(80, 70);
    }

    @Test
    void givenSavedPenalty_whenSettingWalkover_thenRejectWithoutDiscardingScores() {
        // given
        penalties.save(raceId, command(penalties.view(raceId), 20, 10), "test-manager");
        // when / then
        assertThatThrownBy(() -> matchService.updateWalkover(matchId, homeId))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("penalty");
        assertScores(70, 70);
        assertThat(penalties.view(raceId).history()).hasSize(1);
    }

    @Test
    void givenFinalDriverResults_whenApplyingAndResettingPenalties_thenKeepImmutableHistoryAndOfficialTotals() {
        // given
        var original = penalties.view(raceId);
        // when
        assertThat(penalties.save(raceId, command(original, 20, 10), "test-manager")).isEqualTo(1);
        // then
        assertScores(70, 70);
        var corrected = penalties.view(raceId);
        assertThat(corrected.totals().home().gross()).isEqualTo(80);
        assertThat(corrected.totals().away().gross()).isEqualTo(70);
        assertThat(corrected.history()).hasSize(1);
        assertThat(corrected.history().getFirst().getActorId()).isEqualTo("test-manager");
        // when
        penalties.save(raceId, command(corrected, 0, 0), "other-manager");
        // then
        assertScores(80, 70);
        assertThat(penalties.view(raceId).history()).extracting(RacePenaltyRevision::getHomePenalty).containsExactly(20, 0);
    }

    @Test
    void givenStaleRevisionOrResultFingerprint_whenSaving_thenRejectWithoutAppendingOrChangingScores() {
        // given
        var original = penalties.view(raceId);
        penalties.save(raceId, command(original, 20, 10), "test-manager");
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(original, 100, 0), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertScores(70, 70);
        var beforeNewResults = penalties.view(raceId);
        races.saveResults(raceId, results(true));
        assertThatThrownBy(() -> penalties.save(raceId, command(beforeNewResults, 10, 0), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertScores(60, 80);
        assertThat(penalties.view(raceId).history()).hasSize(1);
        assertThat(penalties.view(raceId).history().getFirst().getHomeGross()).isEqualTo(80);
    }

    @Test
    void givenForgedIdentityNegativeAmountOrMissingRevision_whenSaving_thenRejectAndKeepNoHistory() {
        // given
        var view = penalties.view(raceId);
        var valid = command(view, 20, 10);
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(view, -1, 0), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("nonnegative");
        assertThatThrownBy(() -> penalties.save(raceId, new RacePenaltyService.Correction(UUID.randomUUID(), awayId,
                20, 10, valid.expectedRevision(), valid.fingerprint(), valid.generation(), valid.reason()), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> penalties.save(raceId, new RacePenaltyService.Correction(homeId, awayId,
                20, 10, null, valid.fingerprint(), valid.generation(), valid.reason()), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("revision");
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenPreviousManagementGeneration_whenSaving_thenRejectRestoredOrRestartedForm() {
        // given
        var original = penalties.view(raceId);
        epoch.afterRestore(null);
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(original, 20, 10), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current");
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenRestoreWriterFence_whenSaving_thenRejectBeforeTransactionOrMutation() {
        // given
        var original = penalties.view(raceId);
        assertThat(imports.tryLock()).isTrue();
        try {
            // when / then
            assertThatThrownBy(() -> penalties.save(raceId, command(original, 20, 10), "test-manager"))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
        } finally { imports.unlock(); }
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenUnplayedRace_whenSavingPenalty_thenDoNotCreateAnArtificialPlayedDraw() {
        // given
        races.saveResults(raceId, List.of());
        var empty = penalties.view(raceId);
        // when / then
        assertThatThrownBy(() -> penalties.save(raceId, command(empty, 20, 10), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("results");
        tx.executeWithoutResult(status -> assertThat(matches.findById(matchId).orElseThrow().getHomeScore()).isNull());
        assertThat(penalties.view(raceId).history()).isEmpty();
    }

    private RacePenaltyService.Correction command(RacePenaltyService.View view, int home, int away) {
        return new RacePenaltyService.Correction(homeId, awayId, home, away, view.revision(), view.fingerprint(),
                view.generation(), "Confirmed steward correction");
    }

}
