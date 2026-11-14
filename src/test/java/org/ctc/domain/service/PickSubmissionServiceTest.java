package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.SubmissionReceipt;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickSubmissionServiceTest {
    static final Instant NOW = Instant.parse("2026-11-02T18:00:00Z");
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final MatchdayPickRevisionRepository revisions = mock(MatchdayPickRevisionRepository.class);
    final PickTargetService targets = mock(PickTargetService.class);
    final PickEligibilityService eligibility = mock(PickEligibilityService.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final TrackEnvironmentService environments = mock(TrackEnvironmentService.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final Race race = new Race();
    final Season season = new Season("Test_PickSubmission");
    final Team team = new Team();
    final UUID request = UUID.randomUUID();
    MatchdayPick pick;
    AuthorizedPickActor actor;
    PickSubmissionService service;

    @BeforeEach
    void setUp() {
        race.setId(UUID.randomUUID());
        season.setId(UUID.randomUUID());
        team.setId(UUID.randomUUID());
        race.setHomeTeamOverride(team);
        var phase = new SeasonPhase();
        phase.setSeason(season);
        var day = new Matchday(phase, "Test pick round", 1);
        day.setPickDeadline(LocalDateTime.of(2026, 11, 2, 20, 0));
        day.setPicksOpen(true);
        race.setMatchday(day);
        pick = new MatchdayPick(race, season, team);
        actor = new AuthorizedPickActor(race.getId(), season.getId(), team.getId(), "12345");
        when(targets.lockForTeam(race.getId(), actor)).thenReturn(new PickTargetService.LockedTarget(season, race, pick));
        when(targets.isEligible(race)).thenReturn(true);
        when(revisions.findByRequestId(request)).thenReturn(Optional.empty());
        when(picks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(writes.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        service = new PickSubmissionService(picks, revisions, targets, eligibility, new PickDeadlinePolicy(clock), writes, clock, environments);
    }

    @Test
    void givenCompleteConfirmationBeforeDeadline_whenSubmitting_thenPersistImmutableRevisionAndDurableReceipt() {
        // given / when / then
        var payload = payload();
        var receipt = service.submit(race.getId(), 0, request, actor, payload);
        assertThat(receipt).isEqualTo(new SubmissionReceipt(race.getId(), request, 1, 1, NOW, actor.userId()));
        var saved = ArgumentCaptor.forClass(MatchdayPickRevision.class);
        verify(revisions).save(saved.capture());
        assertThat(saved.getValue().getPayload()).isEqualTo(payload);
        assertThat(saved.getValue().receipt()).isEqualTo(receipt);
        assertThat(saved.getValue().getPick()).isSameAs(pick);
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        verify(eligibility).validate(race, payload);
        verify(environments).requireTimeOfDay(payload.trackId(), payload.timeOfDay());
    }

    @Test
    void givenGatewayConfirmation_whenSubmitting_thenValidateCanonicalWeatherAgainstLockedCurrentLayout() {
        // given
        gatewayActor(); var payload = withWeather("Custom S01; S01; ?; S02; C01; C06");
        // when
        service.submit(race.getId(), 0, request, actor, payload);
        // then
        verify(environments).requireWeather(payload.trackId(), PickWeatherSelection.parseCanonical(payload.weather()));
        verify(revisions).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"S01", "Preset ?", "Custom S01; S02", "Preset S01; S02", "rain", "Custom S01; S01; S01; S01; S01; S01; S01; S01; S01"})
    void givenGatewayConfirmationWithMalformedWeather_whenSubmitting_thenRejectBeforeSaving(String weather) {
        // given
        gatewayActor(); var payload = withWeather(weather);
        // when / then
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload)).isInstanceOf(BusinessRuleException.class);
        verify(revisions, never()).save(any()); assertThat(pick.getVersion()).isZero();
    }

    @Test
    void givenLayoutRainChangedAfterRendering_whenConfirming_thenRejectBeforeSaving() {
        // given
        gatewayActor(); var payload = withWeather("Preset R03");
        doThrow(new BusinessRuleException("Weather is unavailable for this layout"))
                .when(environments).requireWeather(payload.trackId(), PickWeatherSelection.parseCanonical(payload.weather()));
        // when / then
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload)).isInstanceOf(BusinessRuleException.class);
        verify(revisions, never()).save(any()); assertThat(pick.getVersion()).isZero();
    }

    @Test
    void givenHistoricGatewayReceiptAndChangedCatalog_whenReplaying_thenReturnOriginalRawWeatherWithoutRevalidation() {
        // given
        gatewayActor(); pick.submit();
        var original = new MatchdayPickRevision(pick, 1, payload(), NOW, actor.userId(), request, 1);
        when(revisions.findByRequestId(request)).thenReturn(Optional.of(original));
        // when / then
        assertThat(service.submit(race.getId(), 0, request, actor, payload())).isEqualTo(original.receipt());
        verifyNoInteractions(environments); verify(revisions, never()).save(any());
    }

    private void gatewayActor() {
        actor = new AuthorizedPickActor(race.getId(), season.getId(), team.getId(), "12345",
                new AuthorizedPickActor.ChannelContext("test-guild", "test-channel", 0, team.getId()));
        when(targets.lockForTeam(race.getId(), actor)).thenReturn(new PickTargetService.LockedTarget(season, race, pick));
    }
    private PickPayload withWeather(String weather) {
        var p = payload();
        return new PickPayload(p.trackId(), p.carId(), p.carPoolEntryId(), p.laps(), p.tyreWear(), p.fuelConsumption(),
                p.refuelingSpeed(), p.initialFuel(), p.requiredPitStops(), p.timeProgressionMode(), p.timeProgressionMultiplier(),
                weather, p.timeOfDay(), p.availableTyres(), p.mandatoryTyres());
    }

    @Test
    void givenChangedLayoutTimeOptions_whenConfirmingNewSubmission_thenRejectBeforeSavingARevision() {
        doThrow(new BusinessRuleException("Choose a time of day from current options"))
                .when(environments).requireTimeOfDay(any(), any());
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current options");
        verify(picks, never()).save(any()); verify(revisions, never()).save(any());
        assertThat(pick.getVersion()).isZero();
    }

    @Test
    void givenLostReplyAndChangedVersion_whenReplayingSameRequest_thenReturnOriginalReceiptWithoutAnotherWrite() {
        // given / when / then
        pick.submit();
        var original = new MatchdayPickRevision(pick, 1, payload(), NOW.minusSeconds(60), actor.userId(), request, 1);
        when(revisions.findByRequestId(request)).thenReturn(Optional.of(original));
        pick.returnForCorrection();
        pick.submit();
        var receipt = service.submit(race.getId(), 0, request, actor, payload());
        assertThat(receipt).isEqualTo(original.receipt());
        assertThat(pick.getVersion()).isEqualTo(3);
        verify(revisions, never()).save(any());
        verifyNoInteractions(eligibility, environments);
    }

    @Test
    void givenDistinctStaleEdit_whenConfirming_thenDoNotAddRevisionOrChangeAggregate() {
        // given / when / then
        pick.submit();
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(pick.getVersion()).isEqualTo(1);
        verify(revisions, never()).save(any());
    }

    @Test
    void givenAnotherSubmittersRequest_whenReplaying_thenDenyReceipt() {
        // given / when / then
        var original = new MatchdayPickRevision(pick, 1, payload(), NOW, "someone-else", request, 1);
        when(revisions.findByRequestId(request)).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("request");
        assertThat(pick.getVersion()).isZero();
        verify(revisions, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"at-deadline", "after-deadline", "closed-matchday", "closed", "invalid-target"})
    void givenClosedCollection_whenConfirming_thenLeaveHistoryAndAggregateUntouched(String cause) {
        // given / when / then
        switch (cause) {
            case "at-deadline" -> race.getMatchday().setPickDeadline(LocalDateTime.of(2026, 11, 2, 19, 0));
            case "after-deadline" -> race.getMatchday().setPickDeadline(LocalDateTime.of(2026, 11, 2, 18, 59));
            case "closed-matchday" -> race.getMatchday().setPicksOpen(false);
            case "closed" -> pick.close();
            case "invalid-target" -> when(targets.isEligible(race)).thenReturn(false);
            default -> throw new AssertionError(cause);
        }
        long version = pick.getVersion();
        assertThatThrownBy(() -> service.submit(race.getId(), version, request, actor, payload()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(pick.getVersion()).isEqualTo(version);
        verify(revisions, never()).save(any());
    }

    @Test
    void givenReturnedPickInOpenMatchdayWithoutCutoff_whenReplacing_thenKeepImmutableOldRevision() {
        // given / when / then
        pick.submit();
        var old = new MatchdayPickRevision(pick, 1, payload(), NOW.minusSeconds(120), actor.userId(), UUID.randomUUID(), 1);
        pick.returnForCorrection();
        race.getMatchday().setPickDeadline(null);
        var receipt = service.submit(race.getId(), 2, request, actor, payload());
        assertThat(receipt).isNotNull();
        assertThat(receipt.revisionNumber()).isEqualTo(2);
        assertThat(pick.isManualOpen()).isTrue();
        assertThat(old.getRevisionNumber()).isEqualTo(1);
        assertThat(old.receipt().aggregateVersion()).isEqualTo(1);
        assertThat(old.getConfirmedAt()).isEqualTo(NOW.minusSeconds(120));
    }

    @Test
    void givenEligibilityRejection_whenConfirming_thenDoNotChangeVersionOrCreateReceipt() {
        // given / when / then
        doThrow(new BusinessRuleException("Track reserved")).when(eligibility).validate(any(), any());
        assertThatThrownBy(() -> service.submit(race.getId(), 0, request, actor, payload()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reserved");
        assertThat(pick.getVersion()).isZero();
        verify(revisions, never()).save(any());
    }

    @Test
    void givenClockCrossingCutoffDuringConfirmation_whenSubmitting_thenUseAcceptedInstantForReceipt() {
        // given
        var cutoff = Instant.parse("2026-11-02T19:00:00Z");
        var accepted = cutoff.minusNanos(1);
        var advancing = mock(Clock.class);
        when(advancing.instant()).thenReturn(accepted, cutoff);
        var boundaryService = new PickSubmissionService(picks, revisions, targets, eligibility,
                new PickDeadlinePolicy(advancing), writes, advancing, environments);
        // when
        var receipt = boundaryService.submit(race.getId(), 0, request, actor, payload());
        // then
        assertThat(receipt.confirmedAt()).isBefore(cutoff);
        assertThat(receipt.confirmedAt()).isEqualTo(accepted.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void givenDurableRequestWithoutRetainedWizardPayload_whenReplaying_thenRecoverOriginalReceipt() {
        // given
        pick.submit();
        var original = new MatchdayPickRevision(pick, 1, payload(), NOW, actor.userId(), request, 1);
        when(revisions.findByRequestId(request)).thenReturn(Optional.of(original));
        var recovered = new java.util.concurrent.atomic.AtomicReference<SubmissionReceipt>();
        // when / then
        assertThatCode(() -> recovered.set(service.submit(race.getId(), 0, request, actor, null))).doesNotThrowAnyException();
        assertThat(recovered.get()).isEqualTo(original.receipt());
        verify(revisions, never()).save(any());
        verifyNoInteractions(eligibility, environments);
    }

    static PickPayload payload() {
        return new PickPayload(UUID.randomUUID(), UUID.randomUUID(), null, 10, 3, 2, 4,
                PickInitialFuel.DEFAULT, 0, TimeProgressionMode.MULTIPLIER, 0, "S01", "Afternoon",
                Set.of(PickTyre.RS, PickTyre.RM), Set.of());
    }
}
