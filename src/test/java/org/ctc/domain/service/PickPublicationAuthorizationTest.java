package org.ctc.domain.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class PickPublicationAuthorizationTest {
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final MatchdayPickDecisionRepository decisions = mock(MatchdayPickDecisionRepository.class);
    final MatchdayPickRevisionRepository revisions = mock(MatchdayPickRevisionRepository.class);
    final PickPublicationAuthorization service = new PickPublicationAuthorization(picks, decisions, revisions);
    final MatchdayPick pick = mock(MatchdayPick.class);
    final Race race = new Race();
    final PickPayload payload = PickSubmissionServiceTest.payload();

    @BeforeEach
    void setUp() {
        race.setId(UUID.randomUUID()); race.setPicksManaged(true);
        var car = new Car("Test frozen", "Test car"); car.setId(payload.carId()); race.setCar(car);
        var track = new Track("Test frozen track"); track.setId(payload.trackId()); race.setTrack(track);
        var settings = new RaceSettings(race); RaceSettingsValues.from(payload).applyTo(settings); race.setSettings(settings);
        when(picks.findByRaceId(race.getId())).thenReturn(Optional.of(pick));
        when(pick.getId()).thenReturn(UUID.randomUUID()); when(pick.getState()).thenReturn(PickState.APPROVED);
        when(pick.getVersion()).thenReturn(2L); when(pick.getApprovedRevisionNumber()).thenReturn(1);
        when(pick.matchesTarget(race)).thenReturn(true);
        decision(PickDecisionKind.APPROVE, 2);
        when(revisions.findByPickIdAndRevisionNumber(pick.getId(), 1)).thenReturn(Optional.of(
                new MatchdayPickRevision(pick, 1, payload, Instant.now(), "12345", UUID.randomUUID(), 1)));
    }

    @Test
    void givenLegacyUnenrolledRace_whenAuthorizing_thenKeepItsExistingPublicationContract() {
        // given
        race.setPicksManaged(false);
        // when / then
        var source = assertDoesNotThrow(() -> service.authorize(race));
        assertThat(source.origin()).isEqualTo(PickPublicationOrigin.LEGACY); assertThat(source.version()).isZero();
        verifyNoInteractions(picks, decisions, revisions);
    }

    @Test
    void givenExactApprovedSettings_whenAuthorizing_thenCaptureTheApprovalVersion() {
        // when / then
        var source = assertDoesNotThrow(() -> service.authorize(race));
        assertThat(source.origin()).isEqualTo(PickPublicationOrigin.APPROVAL); assertThat(source.version()).isEqualTo(2);
    }

    @Test
    void givenRecordedCorrection_whenAuthorizing_thenCaptureItsOwnPayloadAndVersion() {
        // given
        when(pick.getVersion()).thenReturn(3L); decision(PickDecisionKind.CORRECTION, 3);
        // when / then
        var source = assertDoesNotThrow(() -> service.authorize(race));
        assertThat(source.origin()).isEqualTo(PickPublicationOrigin.CORRECTION); assertThat(source.version()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "pending", "foreign-target", "stale-decision", "missing-revision", "unrecorded-edit", "wrong-decision"})
    void givenUnapprovedOrUnrecordedChoices_whenAuthorizing_thenRejectBeforeAnyRenderingOrNetwork(String problem) {
        // given
        switch (problem) {
            case "missing" -> when(picks.findByRaceId(race.getId())).thenReturn(Optional.empty());
            case "pending" -> when(pick.getState()).thenReturn(PickState.SUBMITTED);
            case "foreign-target" -> when(pick.matchesTarget(race)).thenReturn(false);
            case "stale-decision" -> decision(PickDecisionKind.APPROVE, 1);
            case "missing-revision" -> when(revisions.findByPickIdAndRevisionNumber(pick.getId(), 1)).thenReturn(Optional.empty());
            case "unrecorded-edit" -> race.getSettings().setWeather("Test private unrecorded weather");
            case "wrong-decision" -> decision(PickDecisionKind.CLOSE, 2);
            default -> throw new AssertionError(problem);
        }
        // when / then
        assertThatThrownBy(() -> service.authorize(race)).isInstanceOf(BusinessRuleException.class);
    }

    private void decision(PickDecisionKind kind, long version) {
        when(decisions.findByPickIdOrderByAggregateVersionDesc(pick.getId())).thenReturn(List.of(
                new MatchdayPickDecision(pick, kind, version, "test-admin", "Test reviewed", Instant.now(),
                        kind == PickDecisionKind.CORRECTION ? payload : null)));
    }
}
