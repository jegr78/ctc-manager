package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
class PickConcurrencyIT extends PickPersistenceIT {
    @Autowired RaceRepository races;
    @Autowired JdbcTemplate jdbc;
    @Autowired ImportLockService imports;
    @MockitoSpyBean RaceSettingsApplicationService settings;
    UUID otherRaceId;
    PickPayload otherPayload;
    AuthorizedPickActor otherActor;

    @BeforeEach
    void createSecondTarget() {
        // given
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var original = races.findById(fixture.race().getId()).orElseThrow();
            var other = helper.createRace(original.getMatchday(), original.getMatch());
            other.setDateTime(original.getDateTime().plusDays(1));
            
            otherRaceId = other.getId();
            var car = cars.save(new Car("Test private concurrency", UUID.randomUUID().toString()));
            var track = new Track();
            track.setName("Test private concurrency " + UUID.randomUUID());
            tracks.save(track);
            season.getCars().add(car);
            season.getTracks().add(track);
            otherPayload = new PickPayload(track.getId(), car.getId(), null, 21, 3, 4, 5,
                    PickInitialFuel.FUEL_40, 2, TimeProgressionMode.MULTIPLIER, 0,
                    "R03 then S01", "Morning", payload.availableTyres(), payload.mandatoryTyres());
            otherActor = new AuthorizedPickActor(otherRaceId, season.getId(), fixture.homeTeam().getId(), "23456");
        });
    }

    @Override
    @AfterEach
    void cleanUp() {
        super.cleanUp();
        tx.executeWithoutResult(status -> {
            cars.deleteById(otherPayload.carId());
            tracks.deleteById(otherPayload.trackId());
        });
    }

    @Test
    void givenTwoConcurrentTargetsClaimingSameChoice_whenConfirming_thenCommitExactlyOneReservation() throws Exception {
        // when
        var outcomes = concurrently(
                () -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload),
                () -> submissions.submit(otherRaceId, 0, UUID.randomUUID(), otherActor, payload));
        // then
        assertOneWinner(outcomes);
        tx.executeWithoutResult(status -> {
            var pending = reservations.pending(fixture.season().getId(), true);
            assertThat(pending).hasSize(1);
            assertThat(revisions.findAll().stream().filter(revision ->
                    revision.getPick().getSeason().getId().equals(fixture.season().getId()))).hasSize(1);
        });
    }

    @Test
    void givenTwoConcurrentDisjointChoices_whenConfirming_thenCommitBothWithoutLostReservations() throws Exception {
        // when
        var outcomes = concurrently(
                () -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload),
                () -> submissions.submit(otherRaceId, 0, UUID.randomUUID(), otherActor, otherPayload));
        // then
        assertThat(outcomes).containsOnly((Throwable) null);
        tx.executeWithoutResult(status -> assertThat(reservations.pending(fixture.season().getId(), true)).hasSize(2));
    }

    @Test
    void givenConcurrentReplacementAndApproval_whenUsingSameVersion_thenCommitOneCompleteTransition() throws Exception {
        // given
        UUID request = UUID.randomUUID();
        var original = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        // when
        var outcomes = concurrently(
                () -> submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor, otherPayload),
                () -> { reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Reviewed original"); return null; });
        // then
        assertOneWinner(outcomes);
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(2);
            assertThat(revisions.findByRequestId(request).orElseThrow().receipt()).isEqualTo(original);
            if (pick.getState() == PickState.APPROVED) {
                assertThat(pick.getLatestRevisionNumber()).isEqualTo(1);
                assertThat(pick.getApprovedRevisionNumber()).isEqualTo(1);
                assertThat(pick.getRace().getCar().getId()).isEqualTo(payload.carId());
                assertThat(RaceSettingsValues.from(pick.getRace().getSettings())).isEqualTo(RaceSettingsValues.from(payload));
                assertThat(reservations.pending(fixture.season().getId(), true)).isEmpty();
            } else {
                assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
                assertThat(pick.getLatestRevisionNumber()).isEqualTo(2);
                assertThat(pick.getApprovedRevisionNumber()).isNull();
                assertThat(pick.getRace().getCar()).isNull();
                assertThat(reservations.pending(fixture.season().getId(), true)).hasSize(1);
            }
        });
    }

    @Test
    void givenConcurrentReplacementAndReturn_whenUsingSameVersion_thenKeepOnlyWinningTransition() throws Exception {
        // given
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
        // when
        var outcomes = concurrently(
                () -> submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor, otherPayload),
                () -> { reviews.returnForCorrection(fixture.race().getId(), 1, new ManagementPickActor("test-admin"), "Check weather"); return null; });
        // then
        assertOneWinner(outcomes);
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(2);
            assertThat(pick.getState()).isIn(PickState.SUBMITTED, PickState.RETURNED);
            assertThat(pick.isManualOpen()).isEqualTo(pick.getState() == PickState.RETURNED);
            assertThat(pick.getLatestRevisionNumber()).isEqualTo(pick.getState() == PickState.RETURNED ? 1 : 2);
            assertThat(pick.getRace().getCar()).isNull();
        });
    }

    @Test
    void givenReplacedRevision_whenApprovingOldRevision_thenPreserveAllRows() {
        // given
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
        submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor, otherPayload);
        // when / then
        assertThatThrownBy(() -> reviews.approve(fixture.race().getId(), 2, 1,
                new ManagementPickActor("test-admin"), "Stale approval")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exact latest");
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(2);
            assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
            assertThat(pick.getRace().getCar()).isNull();
            assertThat(decisions.findAll().stream().filter(decision -> decision.getPick().getId().equals(pick.getId()))).isEmpty();
        });
    }

    @Test
    void givenDatabaseFailureAfterSettingsFlush_whenApproving_thenRollBackSettingsStateAndHistoryTogether() {
        // given
        tx.executeWithoutResult(status -> settings.apply(races.findById(fixture.race().getId()).orElseThrow(), otherPayload));
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
        var reviewedFingerprint = tx.execute(status -> PickSetupSnapshot.from(races.findById(fixture.race().getId()).orElseThrow()).fingerprint());
        var flushed = new AtomicBoolean();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT weather FROM race_settings WHERE race_id = ?", String.class,
                    fixture.race().getId().toString())).isEqualTo(payload.weather());
            flushed.set(true);
            return null;
        }).when(AopTestUtils.<RaceSettingsApplicationService>getUltimateTargetObject(settings))
                .apply(any(Race.class), any(PickPayload.class));
        jdbc.execute("ALTER TABLE matchday_pick_decisions ADD CONSTRAINT test_pick_approval_failure CHECK (reason <> 'test rollback')");
        try {
            // when / then
            assertThatThrownBy(() -> reviews.approve(fixture.race().getId(), 1, 1,
                    new ManagementPickActor("test-admin"), "test rollback", true, reviewedFingerprint)).isInstanceOf(RuntimeException.class);
            assertThat(flushed).as("settings SQL really completed before the decision insert failed").isTrue();
            tx.executeWithoutResult(status -> {
                var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
                assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
                assertThat(pick.getVersion()).isEqualTo(1);
                assertThat(pick.getApprovedRevisionNumber()).isNull();
                assertThat(pick.getRace().getCar().getId()).isEqualTo(otherPayload.carId());
                assertThat(pick.getRace().getTrack().getId()).isEqualTo(otherPayload.trackId());
                assertThat(RaceSettingsValues.from(pick.getRace().getSettings())).isEqualTo(RaceSettingsValues.from(otherPayload));
                assertThat(pick.getRace().getMatch().getId()).isEqualTo(fixture.match().getId());
                assertThat(pick.getRace().getDateTime()).isEqualTo(fixture.race().getDateTime());
                assertThat(pick.getRace().getResults()).isEmpty();
                assertThat(decisions.findAll().stream().filter(decision -> decision.getPick().getId().equals(pick.getId()))).isEmpty();
            });
        } finally {
            jdbc.execute("ALTER TABLE matchday_pick_decisions DROP CONSTRAINT test_pick_approval_failure");
        }
    }

    @Test
    void givenRestoreHoldingAdmission_whenSubmitting_thenRejectBeforeAnyPersistentWrite() {
        // given
        assertThat(imports.tryLock()).isTrue();
        try {
            // when / then
            assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
            tx.executeWithoutResult(status -> assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty());
        } finally {
            imports.unlock();
        }
    }

    @Test
    void givenAdmittedApprovalPausedBeforeCommit_whenRestoreDrainsWriters_thenWaitForCommittedSettings() throws Exception {
        // given
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
        var applied = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            applied.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(AopTestUtils.<RaceSettingsApplicationService>getUltimateTargetObject(settings))
                .apply(any(Race.class), any(PickPayload.class));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var approval = workers.submit(() -> reviews.approve(fixture.race().getId(), 1, 1,
                    new ManagementPickActor("test-admin"), "Checked before restore"));
            try {
                assertThat(applied.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(imports.tryLock()).isTrue();
                var drainStarted = new CountDownLatch(1);
                // when
                var drained = workers.submit(() -> {
                    drainStarted.countDown();
                    imports.awaitWritersDrained();
                    return null;
                });
                assertThat(drainStarted.await(10, TimeUnit.SECONDS)).isTrue();
                // then
                assertThatThrownBy(() -> drained.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> submissions.submit(otherRaceId, 0, UUID.randomUUID(), otherActor, otherPayload))
                        .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
                release.countDown();
                approval.get(10, TimeUnit.SECONDS);
                drained.get(10, TimeUnit.SECONDS);
                tx.executeWithoutResult(status -> {
                    var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
                    assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
                    assertThat(RaceSettingsValues.from(pick.getRace().getSettings())).isEqualTo(RaceSettingsValues.from(payload));
                });
            } finally {
                release.countDown();
                imports.unlock();
            }
        }
    }

    private static List<Throwable> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        var start = new CyclicBarrier(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = workers.submit(() -> attempt(start, first));
            var two = workers.submit(() -> attempt(start, second));
            return java.util.Arrays.asList(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }

    private static Throwable attempt(CyclicBarrier start, Callable<?> action) {
        try {
            start.await(10, TimeUnit.SECONDS);
            action.call();
            return null;
        } catch (Exception failure) {
            return failure;
        }
    }

    private static void assertOneWinner(List<Throwable> outcomes) {
        assertThat(outcomes.stream().filter(java.util.Objects::isNull)).hasSize(1);
        assertThat(outcomes.stream().filter(java.util.Objects::nonNull)).singleElement()
                .isInstanceOf(BusinessRuleException.class);
    }
}
