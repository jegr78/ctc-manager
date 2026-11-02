package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.MatchdayPickDecisionRepository;
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

class PickReviewServiceTest {
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final MatchdayPickRevisionRepository revisions = mock(MatchdayPickRevisionRepository.class);
    final MatchdayPickDecisionRepository decisions = mock(MatchdayPickDecisionRepository.class);
    final PickTargetService targets = mock(PickTargetService.class);
    final PickEligibilityService eligibility = mock(PickEligibilityService.class);
    final RaceSettingsApplicationService settings = mock(RaceSettingsApplicationService.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final Clock clock = Clock.fixed(PickSubmissionServiceTest.NOW, ZoneOffset.UTC);
    final ManagementPickActor actor = new ManagementPickActor("test-admin");
    final Race race = new Race();
    final Season season = new Season("Test_PickReview");
    final Team team = new Team();
    MatchdayPick pick;
    PickReviewService service;

    @BeforeEach
    void setUp() {
        race.setId(UUID.randomUUID());
        season.setId(UUID.randomUUID());
        team.setId(UUID.randomUUID());
        pick = new MatchdayPick(race, season, team);
        when(targets.lockForManagement(race.getId(), actor)).thenReturn(new PickTargetService.LockedTarget(season, race, pick));
        when(targets.isEligible(race)).thenReturn(true);
        when(picks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(writes.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        service = new PickReviewService(picks, revisions, decisions, targets, eligibility, settings, writes, clock);
    }

    @Test
    void givenMissingPickAfterDeadline_whenOpening_thenCreateNoRevisionAndRecordActorReasonTime() {
        service.openMissing(race.getId(), 0, actor, "Late first submission allowed");
        assertThat(pick.isManualOpen()).isTrue();
        assertThat(pick.getLatestRevisionNumber()).isNull();
        assertDecision(PickDecisionKind.OPEN, "Late first submission allowed", 1);
        verifyNoInteractions(revisions, settings);
    }

    @Test
    void givenSubmittedPickWithoutNewDeadline_whenReturning_thenReopenAndPreserveRevision() {
        pick.submit();
        pick.close();
        service.returnForCorrection(race.getId(), 2, actor, "Please correct the weather slots");
        assertThat(pick.getState()).isEqualTo(PickState.RETURNED);
        assertThat(pick.isManualOpen()).isTrue();
        assertThat(pick.isManualClosed()).isFalse();
        assertThat(pick.getLatestRevisionNumber()).isEqualTo(1);
        assertDecision(PickDecisionKind.RETURN, "Please correct the weather slots", 3);
        verifyNoInteractions(settings);
    }

    @Test
    void givenOpenedPickBeforeDeadline_whenClosing_thenDenyTeamEditingAndRecordDecision() {
        pick.openMissing();
        service.close(race.getId(), 1, actor, "Collection closed for review");
        assertThat(pick.isManualClosed()).isTrue();
        assertThat(pick.isManualOpen()).isFalse();
        assertDecision(PickDecisionKind.CLOSE, "Collection closed for review", 2);
    }

    @Test
    void givenExactLatestRevision_whenApproving_thenApplyPayloadAndMarkApprovalInSameOperation() {
        pick.submit();
        var revision = revision(1, 1);
        when(revisions.findByPickIdAndRevisionNumber(pick.getId(), 1)).thenReturn(Optional.of(revision));
        service.approve(race.getId(), 1, 1, actor, "Setup checked");
        verify(eligibility).validate(race, revision.getPayload());
        verify(settings).apply(race, revision.getPayload());
        assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
        assertThat(pick.getApprovedRevisionNumber()).isEqualTo(1);
        assertThat(pick.isManualOpen()).isFalse();
        assertDecision(PickDecisionKind.APPROVE, "Setup checked", 2);
    }

    @Test
    void givenNewerRevision_whenApprovingStaleRevision_thenDoNotTouchSettingsOrHistory() {
        pick.submit();
        pick.submit();
        assertThatThrownBy(() -> service.approve(race.getId(), 2, 1, actor, "Checked old revision"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("latest");
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        assertThat(pick.getVersion()).isEqualTo(2);
        verifyNoInteractions(settings, decisions);
    }

    @Test
    void givenStaleAggregateVersion_whenReturning_thenDoNotChangeStateOrRecordDecision() {
        pick.submit();
        assertThatThrownBy(() -> service.returnForCorrection(race.getId(), 0, actor, "Please correct"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        verifyNoInteractions(decisions);
    }

    @Test
    void givenFailedSettingsApplication_whenApproving_thenDoNotMarkApprovalOrWriteDecision() {
        pick.submit();
        var revision = revision(1, 1);
        when(revisions.findByPickIdAndRevisionNumber(pick.getId(), 1)).thenReturn(Optional.of(revision));
        doThrow(new BusinessRuleException("Settings application failed")).when(settings).apply(race, revision.getPayload());
        assertThatThrownBy(() -> service.approve(race.getId(), 1, 1, actor, "Checked"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("failed");
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        assertThat(pick.getApprovedRevisionNumber()).isNull();
        verifyNoInteractions(decisions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void givenMissingReason_whenReturning_thenRejectBeforeChangingAnything(String reason) {
        pick.submit();
        assertThatThrownBy(() -> service.returnForCorrection(race.getId(), 1, actor, reason))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        assertThat(pick.getVersion()).isEqualTo(1);
        verifyNoInteractions(decisions);
    }

    @Test
    void givenApprovedPick_whenReturning_thenKeepTeamLockedAndDecisionHistoryUnchanged() {
        pick.submit();
        pick.approve(1);
        assertThatThrownBy(() -> service.returnForCorrection(race.getId(), 2, actor, "Reopen"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Approved");
        assertThat(pick.getVersion()).isEqualTo(2);
        verifyNoInteractions(decisions, settings);
    }

    @Test
    void givenApprovedPick_whenApprovingAgain_thenRejectBeforeApplyingAnySettings() {
        // given
        pick.submit();
        pick.approve(1);
        // when / then
        assertThatThrownBy(() -> service.approve(race.getId(), 2, 1, actor, "Approve twice"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Approved");
        verifyNoInteractions(settings, decisions, eligibility);
    }

    @Test
    void givenApprovedPick_whenCorrectingSettings_thenRetainTeamLockAndRecordImmutableCorrectionPayload() {
        // given
        pick.submit();
        pick.approve(1);
        var payload = PickSubmissionServiceTest.payload();
        // when
        service.correct(race.getId(), 2, actor, "Corrected track after management check", payload);
        // then
        assertThat(pick.getVersion()).isEqualTo(3);
        assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
        assertThat(pick.getApprovedRevisionNumber()).isEqualTo(1);
        verify(settings).apply(race, payload);
        verify(eligibility).validate(race, payload);
        var saved = ArgumentCaptor.forClass(MatchdayPickDecision.class);
        verify(decisions).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(PickDecisionKind.CORRECTION);
        assertThat(saved.getValue().getCorrectionPayload()).isEqualTo(payload);
        assertThat(saved.getValue().getReason()).isEqualTo("Corrected track after management check");
        assertThat(saved.getValue().getActorId()).isEqualTo(actor.userId());
        assertThat(saved.getValue().getAggregateVersion()).isEqualTo(3);
    }

    @Test
    void givenUnapprovedPick_whenCorrectingApprovedSettings_thenRejectWithoutSettingsOrHistoryWrites() {
        // given
        pick.submit();
        // when / then
        assertThatThrownBy(() -> service.correct(race.getId(), 1, actor, "Correction", PickSubmissionServiceTest.payload()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved");
        assertThat(pick.getVersion()).isEqualTo(1);
        verifyNoInteractions(settings, decisions);
    }

    private MatchdayPickRevision revision(int number, long version) {
        return new MatchdayPickRevision(pick, number, PickSubmissionServiceTest.payload(), Instant.now(clock), "12345", UUID.randomUUID(), version);
    }

    private void assertDecision(PickDecisionKind kind, String reason, long version) {
        var saved = ArgumentCaptor.forClass(MatchdayPickDecision.class);
        verify(decisions).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(kind);
        assertThat(saved.getValue().getActorId()).isEqualTo(actor.userId());
        assertThat(saved.getValue().getReason()).isEqualTo(reason);
        assertThat(saved.getValue().getDecidedAt()).isEqualTo(clock.instant());
        assertThat(saved.getValue().getAggregateVersion()).isEqualTo(version);
    }
}
