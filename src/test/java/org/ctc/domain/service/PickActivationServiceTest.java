package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class PickActivationServiceTest {
    final SeasonRepository seasons = mock(SeasonRepository.class);
    final RaceRepository races = mock(RaceRepository.class);
    final PickPublicationSnapshotRepository snapshots = mock(PickPublicationSnapshotRepository.class);
    final PickPublicationAttemptRepository attempts = mock(PickPublicationAttemptRepository.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final PickSessionEpoch epoch = new PickSessionEpoch();
    final EntityManager entities = mock(EntityManager.class);
    final PickActivationService service = new PickActivationService(seasons, races, snapshots, attempts, writes, epoch, entities);
    final Season season = new Season("Test activation", 2035, 99);
    final List<Race> rows = new ArrayList<>();
    Race configured;

    @BeforeEach
    void setUp() {
        season.setId(UUID.randomUUID()); configured = race(); rows.add(configured);
        configured.setCar(new Car("Test confidential", "Test choice")); configured.getCar().setId(UUID.randomUUID());
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        when(races.findByMatchdaySeasonId(season.getId())).thenReturn(rows);
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(rows);
        when(writes.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenExplicitWithheldClassification_whenEnabling_thenEnrollReviewedTargetsWithoutChangingTheirSettings(boolean scheduled) {
        // given
        if (!scheduled) configured.setDateTime(null);
        var original = PickSetupSnapshot.from(configured); var review = service.review(season.getId());
        assertThat(review.configured()).hasSize(1);
        // when
        assertDoesNotThrow(() -> service.enable(season.getId(), review.fingerprint(), Map.of(configured.getId(), LegacyPickClassification.WITHHELD), actor()));
        // then
        assertThat(season.isPicksCollectionEnabled()).isTrue(); assertThat(configured.isPicksManaged()).isTrue();
        assertThat(PickSetupSnapshot.from(configured)).isEqualTo(original);
        verify(snapshots, never()).save(any());
    }

    @Test
    void givenUnconfiguredScheduledRace_whenEnabling_thenStartWhollyWithheldWithoutAFalsePublicClassification() {
        // given
        configured.setCar(null); var review = service.review(season.getId());
        // when
        assertDoesNotThrow(() -> service.enable(season.getId(), review.fingerprint(), Map.of(), actor()));
        // then
        assertThat(season.isPicksCollectionEnabled()).isTrue(); assertThat(configured.isPicksManaged()).isTrue();
    }

    @Test
    void givenRestoreDuringTheReviewRead_whenPreparingActivation_thenBindTheFormToTheGenerationBeforeThoseReads() {
        // given
        String before = epoch.managementGeneration();
        when(races.findByMatchdaySeasonId(season.getId())).thenAnswer(call -> { epoch.afterRestore(null); return rows; });
        // when
        var review = service.review(season.getId());
        // then
        assertThat(review.generation()).isEqualTo(before).isNotEqualTo(epoch.managementGeneration());
        assertThatThrownBy(() -> service.enable(season.getId(), review.fingerprint(), Map.of(configured.getId(), LegacyPickClassification.WITHHELD),
                new ManagementPickActor("test-admin", review.generation()))).isInstanceOf(BusinessRuleException.class);
        assertThat(configured.isPicksManaged()).isFalse();
    }

    @Test
    void givenCompletedLegacyRace_whenEnabling_thenKeepItsExistingPublicRulesAndRequireNoClassificationForIt() {
        // given
        var completed = race(); completed.setCar(new Car("Test historical", "Test public car")); completed.getMatch().setHomeScore(3); rows.add(completed);
        var review = service.review(season.getId());
        // when
        assertDoesNotThrow(() -> service.enable(season.getId(), review.fingerprint(), Map.of(configured.getId(), LegacyPickClassification.WITHHELD), actor()));
        // then
        assertThat(configured.isPicksManaged()).isTrue(); assertThat(completed.isPicksManaged()).isFalse();
        assertThat(review.legacyHistoricalCount()).isEqualTo(1);
    }

    @Test
    void givenMatchingActualPublicationEvidence_whenClassifyingAlreadyPublic_thenPreserveThatEvidenceWithoutInventingApproval() {
        // given
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(configured.getId())).thenReturn(List.of(confirmed(configured)));
        var review = service.review(season.getId());
        // when
        assertDoesNotThrow(() -> service.enable(season.getId(), review.fingerprint(), Map.of(configured.getId(), LegacyPickClassification.CONFIRMED_PUBLIC), actor()));
        // then
        assertThat(configured.isPicksManaged()).isTrue(); assertThat(review.configured()).hasSize(1);
        assertThat(review.configured().getFirst().confirmedPublic()).isTrue(); assertThat(review.configured().getFirst().evidence()).hasSize(1);
        verify(snapshots, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-classification", "foreign-target", "stale-settings", "old-generation", "no-public-evidence", "changed-public-content", "uncertain-output", "hide-confirmed-public", "pending-output"})
    void givenIncompleteOrStaleMigrationReview_whenEnabling_thenRejectBeforeChangingAnyMarker(String problem) {
        // given
        var choices = new java.util.HashMap<UUID, LegacyPickClassification>(); choices.put(configured.getId(), LegacyPickClassification.WITHHELD);
        var proof = confirmed(configured);
        if (problem.equals("changed-public-content") || problem.equals("hide-confirmed-public")) {
            when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(configured.getId())).thenReturn(List.of(proof));
        }
        var review = service.review(season.getId()); var actor = actor();
        switch (problem) {
            case "missing-classification" -> choices.clear();
            case "foreign-target" -> choices.put(UUID.randomUUID(), LegacyPickClassification.WITHHELD);
            case "stale-settings" -> configured.getCar().setName("Test changed private choice");
            case "old-generation" -> actor = new ManagementPickActor("test-admin", UUID.randomUUID() + ":0");
            case "no-public-evidence" -> choices.put(configured.getId(), LegacyPickClassification.CONFIRMED_PUBLIC);
            case "changed-public-content" -> { configured.getCar().setName("Test newly private setup"); choices.put(configured.getId(), LegacyPickClassification.CONFIRMED_PUBLIC); review = service.review(season.getId()); }
            case "uncertain-output" -> {
                var attempt = mock(PickPublicationAttempt.class); var unconfirmed = mock(PickPublicationSnapshot.class);
                when(unconfirmed.getAttempt()).thenReturn(attempt); when(attempt.getState()).thenReturn(PickPublicationState.UNCERTAIN);
                when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(configured.getId())).thenReturn(List.of(unconfirmed));
                choices.put(configured.getId(), LegacyPickClassification.CONFIRMED_PUBLIC);
            }
            case "hide-confirmed-public" -> { }
            case "pending-output" -> {
                when(attempts.findByActiveRaceId(configured.getId())).thenReturn(Optional.of(mock(PickPublicationAttempt.class)));
                review = service.review(season.getId());
            }
            default -> throw new AssertionError(problem);
        }
        String fingerprint = review.fingerprint(); var submittedActor = actor;
        // when / then
        assertThatThrownBy(() -> service.enable(season.getId(), fingerprint, choices, submittedActor)).isInstanceOf(BusinessRuleException.class);
        assertThat(season.isPicksCollectionEnabled()).isFalse(); assertThat(configured.isPicksManaged()).isFalse();
    }

    @Test
    void givenEnrolledRace_whenPausingCollection_thenPreserveItsConfidentialMarkerAndSettings() {
        // given
        season.setPicksCollectionEnabled(true); configured.setPicksManaged(true); var content = PickSetupSnapshot.from(configured);
        // when
        assertDoesNotThrow(() -> service.pause(season.getId(), actor()));
        // then
        assertThat(season.isPicksCollectionEnabled()).isFalse(); assertThat(configured.isPicksManaged()).isTrue();
        assertThat(PickSetupSnapshot.from(configured)).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenEnabledSeasonAndLaterUnscheduledRace_whenReviewingAgain_thenEnrollItWithoutPausingOrChangingExistingHistory(boolean hasSettings) {
        // given
        season.setPicksCollectionEnabled(true); configured.setPicksManaged(true);
        var original = PickSetupSnapshot.from(configured);
        var later = race(); later.setDateTime(null);
        if (hasSettings) later.setCar(configured.getCar());
        rows.add(later);
        var review = service.review(season.getId());
        var choices = hasSettings ? Map.of(later.getId(), LegacyPickClassification.WITHHELD)
                : Map.<UUID, LegacyPickClassification>of();
        // when
        service.enable(season.getId(), review.fingerprint(), choices, actor());
        // then
        assertThat(review.enabled()).isTrue(); assertThat(review.newTargetCount()).isEqualTo(1);
        assertThat(season.isPicksCollectionEnabled()).isTrue(); assertThat(later.isPicksManaged()).isTrue();
        assertThat(later.getDateTime()).isNull(); assertThat(configured.isPicksManaged()).isTrue();
        assertThat(PickSetupSnapshot.from(configured)).isEqualTo(original);
        verify(snapshots, never()).save(any());
        assertThatThrownBy(() -> service.enable(season.getId(), review.fingerprint(), choices, actor()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private ManagementPickActor actor() { return new ManagementPickActor("test-admin", epoch.managementGeneration()); }
    private Race race() {
        var day = PhaseTestFixtures.matchdayInRegularPhase(season, "Test scheduled picks", 1); day.setId(UUID.randomUUID());
        var home = new Team("Test home", "T-AH"); home.setId(UUID.randomUUID()); var away = new Team("Test away", "T-AA"); away.setId(UUID.randomUUID());
        var pairing = new Match(day, home, away); pairing.setId(UUID.randomUUID());
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(pairing); race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
        return race;
    }
    private PickPublicationSnapshot confirmed(Race race) {
        Instant at = Instant.parse("2026-11-03T00:00:00Z");
        var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), race, 1, 0, PickPublicationOrigin.LEGACY,
                PickPublicationOutput.SETTINGS, PickPublicationContent.from(race), PickDisclosureField.all(), "test-admin", "123456789012345678",
                "234567890123456789", null, "a".repeat(64), "{}", epoch.managementGeneration(), at));
        attempt.confirm("test-already-public", at.plusSeconds(1));
        return new PickPublicationSnapshot(attempt);
    }
}
