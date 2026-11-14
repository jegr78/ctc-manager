package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickPublicationPersistenceIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager entityManager;
    @Autowired RaceRepository races;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired TrackRepository tracks;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired PickPublicationSnapshotRepository snapshots;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickPublicationService publications;
    @Autowired PickSessionEpoch epoch;
    @Autowired org.ctc.backup.lock.ImportLockService importLock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    TestHelper.SeasonFixture fixture;
    UUID trackId;
    final Instant captured = Instant.parse("2026-11-03T01:00:00.123456Z");

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPB" + UUID.randomUUID().toString().substring(0, 7));
            var track = new Track(); track.setName("Test private original " + UUID.randomUUID());
            track = tracks.save(track); trackId = track.getId(); fixture.race().setTrack(track);
            entityManager.flush();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush(); tracks.deleteById(trackId);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenConfirmedSnapshot_whenReloadingAfterPrivateEdit_thenKeepExactFrozenContentAndPrecision() {
        // given
        UUID attemptId = tx.execute(status -> {
            var attempt = prepare(1, Set.of(PickDisclosureField.TRACK));
            attempt.confirm("test-original-message", captured.plusSeconds(1));
            attempts.flush(); snapshots.saveAndFlush(new PickPublicationSnapshot(attempt));
            return attempt.getId();
        });
        String original = disclosure.forPublic(fixture.race().getId()).trackName();
        // when
        tx.executeWithoutResult(status -> tracks.findById(trackId).orElseThrow().setName("Test later private correction"));
        // then
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isEqualTo(original).doesNotContain("later");
        tx.executeWithoutResult(status -> {
            var attempt = attempts.findById(attemptId).orElseThrow();
            assertThat(attempt.getCapturedAt()).isEqualTo(captured);
            assertThat(attempt.getCompletedAt()).isEqualTo(captured.plusSeconds(1));
            assertThat(attempt.getRequestHash()).isEqualTo(PickPublicationAttempt.manifestHash("{}"));
            assertThat(attempt.getContentHash()).isEqualTo(attempt.getContent().fingerprint());
        });
    }

    @Test
    void givenUncertainDelivery_whenReadingAndRetrying_thenWithholdAndBlockSecondActiveAttempt() {
        // given
        tx.executeWithoutResult(status -> prepare(1, PickDisclosureField.all()).uncertain("LOST_RESPONSE", captured.plusSeconds(1)));
        // when / then
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> prepare(2, PickDisclosureField.all())))
                .isInstanceOf(DataIntegrityViolationException.class);
        tx.executeWithoutResult(status -> assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isPresent());
    }

    @Test
    void givenNewPartialVersion_whenReloading_thenNeverBorrowTrackFromOlderCompleteVersion() {
        // given
        tx.executeWithoutResult(status -> {
            var first = prepare(1, PickDisclosureField.all()); first.confirm("test-first", captured.plusSeconds(1));
            attempts.flush(); snapshots.saveAndFlush(new PickPublicationSnapshot(first));
            var second = prepare(2, Set.of(PickDisclosureField.LAPS)); second.confirm("test-second", captured.plusSeconds(2));
            attempts.flush(); snapshots.saveAndFlush(new PickPublicationSnapshot(second));
        });
        // when
        var projection = disclosure.forPublic(fixture.race().getId());
        // then
        assertThat(projection.trackName()).isNull(); assertThat(projection.carName()).isNull();
        assertThat(projection.settings()).isNotNull(); assertThat(projection.settings().weather()).isNull();
    }

    @Test
    void givenConfirmedPriorVersionAndFailedNewAttempt_whenReading_thenPreserveEarlierPublication() {
        // given
        tx.executeWithoutResult(status -> {
            var first = prepare(1, Set.of(PickDisclosureField.TRACK)); first.confirm("test-first", captured.plusSeconds(1));
            attempts.flush(); snapshots.saveAndFlush(new PickPublicationSnapshot(first));
            prepare(2, PickDisclosureField.all()).fail("REJECTED", captured.plusSeconds(2));
        });
        // when / then
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test private original");
    }

    @Test
    void givenExplicitLegacyPublication_whenSending_thenCommitPreparationBeforeNetworkAndConfirmFrozenContentAfterSuccess() {
        // given
        var sends = new AtomicInteger();
        // when
        String result = assertDoesNotThrow(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                new ManagementPickActor("test-admin"), rows -> bundle(() -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    tx.executeWithoutResult(status -> {
                        assertThat(attempts.findByActiveRaceId(fixture.race().getId()).orElseThrow().getState())
                                .isEqualTo(PickPublicationState.PREPARED);
                        tracks.findById(trackId).orElseThrow().setName("Test private during network");
                    });
                    sends.incrementAndGet(); return "test-confirmed-message";
                }, message -> "test-confirmed")));
        // then
        assertThat(result).isEqualTo("test-confirmed"); assertThat(sends).hasValue(1);
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test private original").doesNotContain("during network");
    }

    @Test
    void givenLostResponse_whenPublishing_thenRetainDurableUncertaintyWithoutDisclosingChoices() {
        // given
        // when / then
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                new ManagementPickActor("test-admin"), rows -> bundle(() -> { throw new IOException("Test lost response"); }, message -> "unexpected")))
                .isInstanceOf(IOException.class);
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId()).orElseThrow().getState()).isEqualTo(PickPublicationState.UNCERTAIN);
            assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).isEmpty();
        });
    }

    @Test
    void givenKnownRejectedOutput_whenPublishing_thenKeepARecordedFailureAndPermitAnExplicitRetry() {
        // given
        // when / then
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                new ManagementPickActor("test-admin"), rows -> bundle(() -> { throw new DiscordAuthException("Test rejected", null); }, message -> "unexpected")))
                .isInstanceOf(DiscordAuthException.class);
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isEmpty();
            assertThat(attempts.findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(fixture.race().getId()).orElseThrow().getState())
                    .isEqualTo(PickPublicationState.FAILED);
        });
    }

    @Test
    void givenRemoteSuccessAndLocalFailure_whenConfirming_thenPreserveUncertaintyInAnIndependentTransaction() {
        // given
        var sends = new AtomicInteger();
        // when / then
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                new ManagementPickActor("test-admin"), rows -> bundle(() -> { sends.incrementAndGet(); return "test-remote-success"; },
                        message -> { throw new IllegalStateException("Test database failure after HTTP"); })))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reconciliation");
        assertThat(sends).hasValue(1);
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId()).orElseThrow().getState()).isEqualTo(PickPublicationState.UNCERTAIN);
            assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).isEmpty();
        });
    }

    @Test
    void givenManagedChoicesWithoutApproval_whenPublishing_thenRejectBeforePreparingOrSending() {
        // given
        var renders = new AtomicInteger();
        // when / then
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS,
                new ManagementPickActor("test-admin"), rows -> { renders.incrementAndGet(); return bundle(() -> "unexpected", message -> "unexpected"); }))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved");
        assertThat(renders).hasValue(0);
    }

    @Test
    void givenRenderingThatChangesCapturedChoices_whenPublishing_thenRollBackBeforeAnyNetworkOrPreparedAttempt() {
        // given
        var sends = new AtomicInteger();
        // when / then
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, new ManagementPickActor("test-manager"), rows -> {
            rows.getFirst().getTrack().setName("Test changed inside rendering");
            return bundle(() -> { sends.incrementAndGet(); return "unexpected"; }, message -> message);
        })).isInstanceOf(BusinessRuleException.class).hasMessageContaining("render changed");
        assertThat(sends).hasValue(0);
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isEmpty();
            assertThat(tracks.findById(trackId).orElseThrow().getName()).contains("Test private original");
        });
    }

    @Test
    void givenFrozenOriginalRoundAfterALegacyRaceMoves_whenDeletingItsOriginalRound_thenRetainTheReferencedPublicationIdentity() {
        // given
        tx.executeWithoutResult(status -> {
            var first = prepare(1, Set.of(PickDisclosureField.TRACK)); first.confirm("test-original-round", captured.plusSeconds(1));
            attempts.flush(); snapshots.saveAndFlush(new PickPublicationSnapshot(first));
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var day = helper.createMatchday(season, "Test new publication round", 7);
            var pairing = helper.createMatch(day, teams.findById(fixture.homeTeam().getId()).orElseThrow(), teams.findById(fixture.awayTeam().getId()).orElseThrow());
            var race = races.findById(fixture.race().getId()).orElseThrow(); race.setMatchday(day); race.setMatch(pairing);
        });
        // when / then
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM matches WHERE id = ?", fixture.match().getId());
            jdbc.update("DELETE FROM matchdays WHERE id = ?", fixture.matchday().getId());
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PreparedPickPublication<String> bundle(PreparedPickPublication.Delivery delivery, java.util.function.Function<String, String> persistence) {
        return new PreparedPickPublication<>("123456789012345678", "234567890123456789", null, "a".repeat(64), "{}",
                Map.of(fixture.race().getId(), Set.of(PickDisclosureField.TRACK)), delivery, persistence);
    }

    @Test
    void givenUncertainPhysicalOutput_whenVerifying_thenCheckOutsideSqlAndAtomicallyRecordItsFrozenSnapshot() throws Exception {
        UUID batch = pending(); var verified = new AtomicInteger();
        String result = publications.reconcile(batch, publications.reconciliationFingerprint(batch), "Test checked exact remote bytes", currentActor(), rows ->
                new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(publications.isInFlight(batch)).isTrue();
                    assertThat(importLock.tryLock()).isTrue();
                    try { assertThat(importLock.isLocked()).isTrue(); } finally { importLock.unlock(); }
                    verified.incrementAndGet(); return "test-reconciled-message";
                }, message -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return "test-confirmed"; }));
        assertThat(result).isEqualTo("test-confirmed"); assertThat(verified).hasValue(1);
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).contains("Test private original");
        tx.executeWithoutResult(status -> {
            var attempt = attempts.findByBatchId(batch).getFirst();
            assertThat(attempt.getState()).isEqualTo(PickPublicationState.SUCCEEDED);
            assertThat(attempt.getReconciledBy()).isEqualTo("test-reviewer");
            assertThat(attempt.getReconciliationReason()).isEqualTo("Test checked exact remote bytes");
            assertThat(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(fixture.race().getId())).hasSize(1);
        });
        assertThat(publications.isInFlight(batch)).isFalse();
    }

    @Test
    void givenVerificationFailure_whenReconciling_thenRetainPendingAndWithholdTheFrozenChoices() {
        UUID batch = pending();
        assertThatThrownBy(() -> publications.reconcile(batch, publications.reconciliationFingerprint(batch), "Test failed proof", currentActor(), rows ->
                new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> { throw new IOException("Test lost verification"); }, message -> "unexpected")))
                .isInstanceOf(IOException.class);
        assertPending(batch); assertThat(publications.isInFlight(batch)).isFalse();
    }

    @Test
    void givenLocalFailureAfterVerifiedMessage_whenReconciling_thenRollBackTheMappingAndPublicConfirmation() {
        UUID batch = pending();
        assertThatThrownBy(() -> publications.reconcile(batch, publications.reconciliationFingerprint(batch), "Test database rejected mapping", currentActor(), rows ->
                new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> "test-confirmed-remote", message -> { throw new BusinessRuleException("Test SQL failure"); })))
                .isInstanceOf(BusinessRuleException.class);
        assertPending(batch);
    }

    @Test
    void givenStaleFormOrRestoreContext_whenReconciling_thenDenyBeforeVerification() {
        UUID batch = pending(); var calls = new AtomicInteger();
        for (String problem : List.of("fingerprint", "generation", "reason", "restore", "transaction")) {
            boolean restore = problem.equals("restore"); if (restore) assertThat(importLock.tryLock()).isTrue();
            try {
                org.assertj.core.api.ThrowableAssert.ThrowingCallable action = () -> publications.reconcile(batch,
                        problem.equals("fingerprint") ? "0".repeat(64) : publications.reconciliationFingerprint(batch),
                        problem.equals("reason") ? " " : "Test reviewed evidence",
                        problem.equals("generation") ? new ManagementPickActor("test-reviewer", "stale") : currentActor(), rows -> {
                            calls.incrementAndGet(); return new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> "unexpected", message -> message);
                        });
                if (problem.equals("transaction")) assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    try { action.call(); } catch (IOException error) { throw new IllegalStateException(error); } catch (Throwable error) { if (error instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException(error); }
                })).isInstanceOf(BusinessRuleException.class);
                else assertThatThrownBy(action).isInstanceOf(BusinessRuleException.class);
            } finally { if (restore) importLock.unlock(); }
        }
        assertThat(calls).hasValue(0); assertPending(batch);
    }

    @Test
    void givenUncertainOutput_whenAbandoning_thenRequireRemoteHandlingAndRetainTheDecisionWithoutReleasingFields() {
        UUID batch = pending(); String fingerprint = publications.reconciliationFingerprint(batch); var quarantined = new AtomicInteger();
        assertThatThrownBy(() -> publications.abandon(batch, fingerprint, "Test retired output", false, currentActor(), rows -> quarantined.incrementAndGet()))
                .isInstanceOf(BusinessRuleException.class);
        assertPending(batch);
        publications.abandon(batch, fingerprint, "Test retired output", true, currentActor(), rows -> quarantined.incrementAndGet());
        assertThat(quarantined).hasValue(1);
        tx.executeWithoutResult(status -> {
            assertThat(attempts.findByActiveRaceId(fixture.race().getId())).isEmpty();
            assertThat(attempts.findByBatchId(batch).getFirst().getState()).isEqualTo(PickPublicationState.ABANDONED);
        });
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
        assertThatThrownBy(() -> publications.abandon(batch, fingerprint, "Test repeated retirement", true, currentActor(), rows -> quarantined.incrementAndGet()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private UUID pending() {
        return tx.execute(status -> {
            var attempt = prepare(1, Set.of(PickDisclosureField.TRACK));
            attempt.uncertain("TEST_LOST_RESPONSE", captured.plusSeconds(1));
            return attempt.getBatchId();
        });
    }

    @Test
    void givenVerificationInProgress_whenTryingToResolveTheSameOutputAgain_thenDenyBothConfirmationAndRetirement() throws Exception {
        // given
        UUID batch = pending(); String fingerprint = publications.reconciliationFingerprint(batch);
        // when
        publications.reconcile(batch, fingerprint, "Test first verification", currentActor(), rows -> new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> {
            assertThatThrownBy(() -> publications.abandon(batch, fingerprint, "Test competing retirement", true, currentActor(), ignored -> { }))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("still being");
            assertThatThrownBy(() -> publications.reconcile(batch, fingerprint, "Test competing confirmation", currentActor(), ignored -> {
                throw new AssertionError("A competing verification must not start");
            })).isInstanceOf(BusinessRuleException.class).hasMessageContaining("still being");
            return "test-original-verified-message";
        }, message -> message));
        // then
        tx.executeWithoutResult(status -> assertThat(attempts.findByBatchId(batch).getFirst().getState()).isEqualTo(PickPublicationState.SUCCEEDED));
    }

    @Test
    void givenTwoRacesInOneUncertainPhysicalBundle_whenReconciling_thenConfirmBothCapturedRacesTogether() throws Exception {
        // given
        UUID batch = pending();
        UUID secondId = tx.execute(status -> {
            var first = attempts.findByBatchId(batch).getFirst();
            var second = helper.createRace(first.getRace().getMatchday(), first.getRace().getMatch());
             second.setTrack(tracks.findById(trackId).orElseThrow());
            var attempt = attempts.saveAndFlush(new PickPublicationAttempt(new PickPublicationAttempt.Capture(batch, second, 1, 0, PickPublicationOrigin.LEGACY,
                    first.getOutput(), PickPublicationContent.from(second), Set.of(PickDisclosureField.TRACK), first.getActorId(), first.getChannelId(), first.getWebhookId(),
                    null, first.getTemplateHash(), first.getRequestManifest(), first.getRuntimeGeneration(), captured)));
            attempt.uncertain("TEST_LOST_RESPONSE", captured.plusSeconds(1)); return second.getId();
        });
        var verified = new AtomicInteger();
        // when
        publications.reconcile(batch, publications.reconciliationFingerprint(batch), "Test checked the complete physical output", currentActor(), rows -> {
            assertThat(rows).hasSize(2);
            return new org.ctc.domain.picks.PreparedPickReconciliation<>(() -> { verified.incrementAndGet(); return "test-bundle-message"; }, message -> message);
        });
        // then
        assertThat(verified).hasValue(1);
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isEqualTo(disclosure.forPublic(secondId).trackName());
        tx.executeWithoutResult(status -> assertThat(attempts.findByBatchId(batch)).hasSize(2)
                .allSatisfy(row -> { assertThat(row.getState()).isEqualTo(PickPublicationState.SUCCEEDED); assertThat(row.getMessageId()).isEqualTo("test-bundle-message"); }));
    }
    private ManagementPickActor currentActor() { return new ManagementPickActor("test-reviewer", epoch.managementGeneration()); }
    private void assertPending(UUID batch) {
        tx.executeWithoutResult(status -> assertThat(attempts.findByBatchId(batch).getFirst().getState()).isEqualTo(PickPublicationState.UNCERTAIN));
        assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
    }

    private PickPublicationAttempt prepare(long version, Set<PickDisclosureField> fields) {
        var race = races.findById(fixture.race().getId()).orElseThrow();
        return attempts.saveAndFlush(new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), race,
                version, 0, PickPublicationOrigin.LEGACY, PickPublicationOutput.SETTINGS, PickPublicationContent.from(race), fields,
                "test-admin", "123456789012345678", "234567890123456789", null, "a".repeat(64), "{}",
                "12345678-1234-1234-1234-123456789abc:0", captured)));
    }
}
