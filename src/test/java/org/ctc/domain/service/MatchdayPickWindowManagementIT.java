package org.ctc.domain.service;

import java.time.Instant;
import java.time.LocalDateTime;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class MatchdayPickWindowManagementIT extends PickPersistenceIT {
    @Autowired PickWindowService windows;
    @Autowired PickSessionEpoch epoch;
    @Autowired RaceService raceService;
    @Autowired MatchService matchService;
    @Autowired PlayoffService playoffService;
    @Autowired org.ctc.backup.lock.ImportLockService importLock;

    private ManagementPickActor manager() { return new ManagementPickActor("test-window-manager", epoch.managementGeneration()); }
    private void save(PickWindowService.DayWindow view, boolean open) {
        windows.saveMatchday(view.id(), open, view.deadline(), view.weekend(), view.version(), view.scope(), manager());
    }

    @Test
    void givenPreparedRound_whenOpeningAndClosing_thenApplyOneGlobalFlagAndAdvanceItsToken() {
        var view = windows.readMatchday(fixture.matchday().getId());
        save(view, false);
        var closed = windows.readMatchday(view.id());
        assertThat(closed.open()).isFalse();
        assertThat(closed.version()).isEqualTo(view.version() + 1);
        save(closed, true);
        assertThat(windows.readMatchday(view.id()).open()).isTrue();
    }

    @Test
    void givenStaleWindowVersion_whenSaving_thenRejectWithoutChangingItsCurrentFlag() {
        var view = windows.readMatchday(fixture.matchday().getId()); save(view, false);
        assertThatThrownBy(() -> save(view, true)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(windows.readMatchday(view.id()).open()).isFalse();
    }

    @Test
    void givenDifferentContextOrRestoreGeneration_whenSaving_thenRejectWithoutChangingTheWindow() {
        var view = windows.readMatchday(fixture.matchday().getId());
        assertThatThrownBy(() -> windows.saveMatchday(view.id(), false, view.deadline(), null, view.version(), "0".repeat(64), manager()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("context changed");
        assertThatThrownBy(() -> windows.saveMatchday(view.id(), false, view.deadline(), null, view.version(), view.scope(), new ManagementPickActor("test-window-manager", "old-instance:0")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Reload");
        assertThat(windows.readMatchday(view.id()).open()).isTrue();
    }

    @Test
    void givenLegacyClosedPick_whenCentrallyOpening_thenAppendAnImmutableWindowDecisionWithoutChangingItsPayload() {
        submissions.submit(fixture.race().getId(), 0, java.util.UUID.randomUUID(), actor, payload);
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow(); pick.close();
            decisions.save(new MatchdayPickDecision(pick, PickDecisionKind.CLOSE, pick.getVersion(), "test-legacy-manager", "Test legacy closure", Instant.now()));
        });
        save(windows.readMatchday(fixture.matchday().getId()), true);
        var pick = picks.findSnapshotByRaceId(fixture.race().getId()).orElseThrow();
        assertThat(pick.isManualClosed()).isFalse();
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        assertThat(pick.getLatestRevisionNumber()).isEqualTo(1);
        assertThat(pick.getVersion()).isEqualTo(3);
        tx.executeWithoutResult(status -> assertThat(decisions.findAll().stream().filter(row -> row.getPick().getId().equals(pick.getId())))
                .extracting(MatchdayPickDecision::getKind).containsExactlyInAnyOrder(PickDecisionKind.CLOSE, PickDecisionKind.WINDOW_OPEN));
    }

    @Test
    void givenMatchDeadlineOverride_whenSavingAndClearing_thenUseTheCurrentMatchdayDefaultAgain() {
        var view = windows.readPairing(fixture.race().getMatch().getId(), false);
        var cutoff = LocalDateTime.of(2036, 1, 1, 12, 0);
        windows.savePairing(view.id(), false, cutoff, view.version(), view.scope(), manager());
        var changed = windows.readPairing(view.id(), false);
        assertThat(changed.override()).isEqualTo(cutoff);
        assertThatThrownBy(() -> windows.savePairing(view.id(), false, null, view.version(), view.scope(), manager()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        windows.savePairing(view.id(), false, null, changed.version(), changed.scope(), manager());
        assertThat(windows.readPairing(view.id(), false).override()).isNull();
        assertThat(windows.readPairing(view.id(), false).defaultDeadline()).isEqualTo(fixture.matchday().getPickDeadline());
    }

    @Test
    void givenUnfinishedRound_whenCheckingCompletion_thenKeepItOpenUntilAllMatchesHaveScores() {
        tx.executeWithoutResult(status -> windows.closeCompletedForSeason(fixture.season().getId()));
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
        tx.executeWithoutResult(status -> {
            var match = entityManager.find(Match.class, fixture.race().getMatch().getId()); match.setHomeScore(10); match.setAwayScore(9);
            windows.closeCompletedForSeason(fixture.season().getId());
        });
        var closed = windows.readMatchday(fixture.matchday().getId());
        assertThat(closed.open()).isFalse();
        assertThatThrownBy(() -> save(closed, true)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("completed");
        tx.executeWithoutResult(status -> {
            var match = entityManager.find(Match.class, fixture.race().getMatch().getId()); match.setHomeScore(null); match.setAwayScore(null);
            windows.closeCompletedForSeason(fixture.season().getId());
        });
        assertThat(windows.readMatchday(closed.id()).open()).isFalse();
    }
    @Test
    void givenTwoUnfinishedMatches_whenQuickScoringOneAndWalkingOverTheOther_thenCloseOnlyAfterTheLastMatch() {
        var other = tx.execute(status -> {
            var first = entityManager.find(Match.class, fixture.match().getId());
            var second = new Match(first.getMatchday(), first.getAwayTeam(), first.getHomeTeam()); entityManager.persist(second); entityManager.flush();
            return second.getId();
        });
        raceService.quickScore(fixture.race().getId(), 20, 17);
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
        matchService.updateWalkover(other, fixture.homeTeam().getId());
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isFalse();
        matchService.updateWalkover(other, null);
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isFalse();
    }

    @Test
    void givenRestoreInProgress_whenSavingAnOpening_thenRejectBeforeMutatingTheWindow() {
        var view = windows.readMatchday(fixture.matchday().getId());
        assertThat(importLock.tryLock()).isTrue();
        try { assertThatThrownBy(() -> save(view, false)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore"); }
        finally { importLock.unlock(); }
        assertThat(windows.readMatchday(view.id()).open()).isTrue();
    }

    @Test
    void givenPlayoffBridge_whenChangingTheDeadlineFromEitherWorkspace_thenKeepOneCanonicalExceptionForBothLegs() {
        var pairing = playoff(true);
        var bridge = windows.readPairing(pairing.bridge(), false);
        var cutoff = LocalDateTime.of(2036, 1, 1, 20, 0);
        windows.savePairing(bridge.id(), false, cutoff, bridge.version(), bridge.scope(), manager());
        var canonical = windows.readPairing(pairing.playoff(), true);
        assertThat(canonical.override()).isEqualTo(cutoff); assertThat(canonical.version()).isEqualTo(bridge.version() + 1);
        tx.executeWithoutResult(status -> {
            var rows = entityManager.createQuery("select r from Race r where r.playoffMatchup.id = :id", Race.class).setParameter("id", pairing.playoff()).getResultList();
            assertThat(rows).hasSize(2).allSatisfy(race -> assertThat(MatchdayPickWindowRules.deadline(race)).isEqualTo(cutoff));
        });
        windows.savePairing(canonical.id(), true, null, canonical.version(), canonical.scope(), manager());
        assertThat(windows.readPairing(pairing.bridge(), false).override()).isNull();
    }

    @Test
    void givenAnOpenStandalonePlayoffRound_whenTheLastPairingIsDecided_thenCloseItsRoundOnly() {
        var pairing = playoff(false);
        playoffService.setWinnerManually(pairing.playoff(), fixture.homeTeam().getId(), "Test adjudication of final pairing");
        assertThat(windows.readMatchday(pairing.day()).open()).isFalse();
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
    }

    @Test
    void givenMixedOrdinaryAndPlayoffLegsOnOneBridge_whenChangingItsException_thenRejectAnAmbiguousScope() {
        var pairing = playoff(true);
        tx.executeWithoutResult(status -> {
            var race = new Race(); race.setMatchday(entityManager.find(Matchday.class, pairing.day()));
            race.setMatch(entityManager.find(Match.class, pairing.bridge())); entityManager.persist(race);
        });
        assertThatThrownBy(() -> windows.readPairing(pairing.bridge(), false)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("context");
    }

    private record PlayoffPairing(java.util.UUID day, java.util.UUID bridge, java.util.UUID playoff) { }
    private PlayoffPairing playoff(boolean withBridge) {
        return tx.execute(status -> {
            var season = entityManager.find(Season.class, fixture.season().getId());
            var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10);
            phase.setLabel("Test playoff picks"); entityManager.persist(phase);
            var playoff = new Playoff(phase, "Test playoff picks"); entityManager.persist(playoff);
            var round = new PlayoffRound(playoff, "Test final", 0); entityManager.persist(round);
            var pairing = new PlayoffMatchup(round, 0); pairing.setTeam1(entityManager.find(Team.class, fixture.homeTeam().getId()));
            pairing.setTeam2(entityManager.find(Team.class, fixture.awayTeam().getId())); entityManager.persist(pairing);
            var day = new Matchday(phase, "Test final picks", 1); day.setPicksOpen(true); entityManager.persist(day);
            Match bridge = withBridge ? new Match(day, pairing.getTeam1(), pairing.getTeam2()) : null;
            if (bridge != null) entityManager.persist(bridge);
            for (int leg = 0; leg < 2; leg++) {
                var race = new Race(); race.setMatchday(day); race.setMatch(bridge); race.setPlayoffMatchup(pairing);
                race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, leg)); race.setPicksManaged(true);
                race.setHomeTeamOverride(leg == 0 ? pairing.getTeam1() : pairing.getTeam2());
                race.setAwayTeamOverride(leg == 0 ? pairing.getTeam2() : pairing.getTeam1()); entityManager.persist(race);
            }
            entityManager.flush(); return new PlayoffPairing(day.getId(), bridge == null ? null : bridge.getId(), pairing.getId());
        });
    }

}
