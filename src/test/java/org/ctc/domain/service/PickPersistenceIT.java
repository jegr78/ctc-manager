package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@CtcDevSpringBootContext
@Tag("integration")
class PickPersistenceIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired MatchdayPickRepository picks;
    @Autowired MatchdayPickRevisionRepository revisions;
    @Autowired MatchdayPickDecisionRepository decisions;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired PickReservationService reservations;
    @Autowired SeasonCarPoolService poolService;
    UUID addedPoolCar;
    @Autowired EntityManager entityManager;
    @MockitoBean PickCollectionStatus collection;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;
    AuthorizedPickActor actor;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPK" + UUID.randomUUID().toString().substring(0, 7));
            fixture.season().setPicksCollectionEnabled(true);
            fixture.race().setPicksManaged(true);
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            fixture.matchday().setPicksOpen(true);
            var car = cars.save(new Car("Test private", UUID.randomUUID().toString()));
            var track = new Track();
            track.setName("Test private " + UUID.randomUUID());
            track = tracks.save(track);
            fixture.season().getCars().add(car);
            fixture.season().getTracks().add(track);
            payload = new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                    TimeProgressionMode.EQUAL_CONDITIONS, null, "Slot 1: S01; Slot 2: R03", "Sunset",
                    Set.of(PickTyre.RS, PickTyre.RM), Set.of(PickTyre.RM));
            actor = new AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(), fixture.homeTeam().getId(), "12345");
            when(collection.isAvailable()).thenReturn(true);
            entityManager.flush();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteById(payload.carId());
            if (addedPoolCar != null) cars.deleteById(addedPoolCar);
            tracks.deleteById(payload.trackId());
            teams.deleteAllById(java.util.List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenSubmittedPicks_whenExpandingFrozenPool_thenPreserveRevisionAndAllowNewEntry() {
        // given
        var originalEntry = tx.execute(status -> {
            if (payload.carPoolEntryId() != null) return payload.carPoolEntryId();
            poolService.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            var id = poolService.saveOneMakeEntry(fixture.season().getId(), null, "Original One-Make", payload.carId());
            poolService.freeze(fixture.season().getId());
            return id;
        });
        payload = new PickPayload(payload.trackId(), payload.carId(), originalEntry, payload.laps(), payload.tyreWear(),
                payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(),
                payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(),
                payload.availableTyres(), payload.mandatoryTyres(), payload.lobbyRulesProfile(), payload.startType(), payload.slipstream());
        var originalSnapshot = poolService.preview(fixture.season().getId()).entries();
        var request = UUID.randomUUID();
        var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        addedPoolCar = tx.execute(status -> cars.saveAndFlush(new Car("Test extension", UUID.randomUUID().toString())).getId());
        // when
        var addedEntry = poolService.saveOneMakeEntry(fixture.season().getId(), null, "Additional One-Make", addedPoolCar);
        // then
        tx.executeWithoutResult(status -> {
            entityManager.clear();
            var revision = revisions.findByRequestId(request).orElseThrow();
            assertThat(revision.getPayload()).isEqualTo(payload);
            assertThat(revision.receipt()).isEqualTo(receipt);
            var pool = poolService.preview(fixture.season().getId());
            assertThat(pool.frozen()).isTrue();
            assertThat(pool.entries()).hasSize(originalSnapshot.size() + 1);
            assertThat(pool.entries().subList(0, originalSnapshot.size())).isEqualTo(originalSnapshot);
            assertThat(pool.entries()).filteredOn(entry -> entry.id().equals(originalEntry)).singleElement()
                    .satisfies(entry -> assertThat(entry.carIds()).contains(payload.carId()));
            assertThat(pool.entries().getLast().id()).isEqualTo(addedEntry);
            assertThat(pool.entries().getLast().carIds()).containsExactly(addedPoolCar);
            assertThat(reservations.pending(fixture.season().getId(), true)).singleElement()
                    .satisfies(reservation -> assertThat(reservation.entryId()).isEqualTo(originalEntry));
        });
    }

    @Test
    void givenCompleteConfirmation_whenReloadingAndReplaying_thenPreserveTypedPayloadAndExactOriginalReceipt() {
        // when
        UUID request = UUID.randomUUID();
        var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        var replay = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        // then
        assertThat(replay).isEqualTo(receipt);
        tx.executeWithoutResult(status -> {
            var persisted = revisions.findByRequestId(request).orElseThrow();
            assertThat(persisted.getPayload()).isEqualTo(payload);
            assertThat(persisted.getConfirmedAt()).isEqualTo(receipt.confirmedAt());
            assertThat(persisted.getReceiptVersion()).isEqualTo(1);
            assertThat(reservations.pending(fixture.season().getId(), true)).hasSize(1);
        });
    }

    @Test
    void givenSubmittedPick_whenReturningReplacingAndApproving_thenKeepImmutableHistoryAndTransferReservation() {
        // when
        UUID originalRequest = UUID.randomUUID();
        var original = submissions.submit(fixture.race().getId(), 0, originalRequest, actor, payload);
        var manager = new ManagementPickActor("test-admin");
        reviews.returnForCorrection(fixture.race().getId(), 1, manager, "Confirm the weather slot order");
        var replacement = submissions.submit(fixture.race().getId(), 2, UUID.randomUUID(), actor, payload);
        reviews.approve(fixture.race().getId(), 3, 2, manager, "Complete setup checked");
        // then
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
            assertThat(pick.getVersion()).isEqualTo(4);
            assertThat(pick.getLatestRevisionNumber()).isEqualTo(2);
            assertThat(pick.getApprovedRevisionNumber()).isEqualTo(2);
            assertThat(pick.isManualOpen()).isFalse();
            assertThat(pick.getRace().getSettings().isComplete()).isTrue();
            assertThat(pick.getRace().getSettings().getMandatoryTyres()).isEqualTo("RM");
            assertThat(pick.getRace().getSettings().getTimeProgressionMode()).isEqualTo(TimeProgressionMode.EQUAL_CONDITIONS);
            assertThat(pick.getRace().getSettings().getTimeProgressionMultiplier()).isNull();
            assertThat(replacement.revisionNumber()).isEqualTo(2);
            assertThat(revisions.findByRequestId(originalRequest).orElseThrow().receipt()).isEqualTo(original);
            assertThat(reservations.pending(fixture.season().getId(), true)).isEmpty();
            var history = decisions.findAll().stream().filter(decision -> decision.getPick().getId().equals(pick.getId())).toList();
            assertThat(history).hasSize(2).allSatisfy(decision -> {
                assertThat(decision.getActorId()).isEqualTo("test-admin");
                assertThat(decision.getReason()).isNotBlank();
                assertThat(decision.getDecidedAt()).isNotNull();
                assertThat(decision.getCorrectionPayload()).isNull();
            });
        });
    }
}
