package org.ctc.domain.model;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MatchdayPickTest {
    @Test
    void givenLateMissingPick_whenOpening_thenDoNotInventARevision() {
        // given / when / then
        var pick = pick();
        pick.openMissing();
        assertThat(pick.getState()).isEqualTo(PickState.MISSING);
        assertThat(pick.getLatestRevisionNumber()).isNull();
        assertThat(pick.getVersion()).isEqualTo(1);
        assertThat(pick.isManualOpen()).isTrue();
        assertThat(pick.isManualClosed()).isFalse();
    }

    @Test
    void givenSubmittedPick_whenReturningAndReplacing_thenPreserveOpeningAndAdvanceRevision() {
        // given / when / then
        var pick = pick();
        assertThat(pick.submit()).isEqualTo(1);
        pick.returnForCorrection();
        assertThat(pick.getState()).isEqualTo(PickState.RETURNED);
        assertThat(pick.isManualOpen()).isTrue();
        assertThat(pick.submit()).isEqualTo(2);
        assertThat(pick.getVersion()).isEqualTo(3);
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        assertThat(pick.getLatestRevisionNumber()).isEqualTo(2);
        assertThat(pick.isManualOpen()).isTrue();
    }

    @Test
    void givenManuallyOpenedPick_whenClosing_thenClearOpeningAndRetainRevision() {
        // given / when / then
        var pick = pick();
        pick.submit();
        pick.returnForCorrection();
        pick.close();
        assertThat(pick.isManualClosed()).isTrue();
        assertThat(pick.isManualOpen()).isFalse();
        assertThatThrownBy(pick::submit).isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
        assertThat(pick.getVersion()).isEqualTo(3);
        assertThat(pick.getLatestRevisionNumber()).isEqualTo(1);
        assertThat(pick.getState()).isEqualTo(PickState.RETURNED);
    }

    @Test
    void givenManualClose_whenReturningOrOpeningMissing_thenClearClose() {
        // given / when / then
        var missing = pick();
        missing.close();
        missing.openMissing();
        assertThat(missing.isManualClosed()).isFalse();
        assertThat(missing.isManualOpen()).isTrue();
        var submitted = pick();
        submitted.submit();
        submitted.close();
        submitted.returnForCorrection();
        assertThat(submitted.isManualClosed()).isFalse();
        assertThat(submitted.isManualOpen()).isTrue();
    }

    @Test
    void givenLatestRevision_whenApproving_thenRetainExactPointerAndDenyManagementTransitions() {
        // given / when / then
        var pick = pick();
        pick.submit();
        pick.returnForCorrection();
        pick.submit();
        pick.approve(2);
        assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
        assertThat(pick.getApprovedRevisionNumber()).isEqualTo(2);
        assertThat(pick.getVersion()).isEqualTo(4);
        assertThat(pick.isManualOpen()).isFalse();
        assertThatThrownBy(pick::returnForCorrection).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(pick::openMissing).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(pick::close).isInstanceOf(BusinessRuleException.class);
        assertThat(pick.getVersion()).isEqualTo(4);
    }

    @Test
    void givenApprovedPick_whenTeamResubmits_thenStartNewRevisionAndKeepApprovedPointer() {
        // given
        var pick = pick();
        pick.submit();
        pick.approve(1);
        // when
        int revision = pick.submit();
        // then
        assertThat(revision).isEqualTo(2);
        assertThat(pick.getState()).isEqualTo(PickState.SUBMITTED);
        assertThat(pick.getApprovedRevisionNumber()).as("the applied revision stays recorded").isEqualTo(1);
        assertThat(pick.isSubmittedAfterApproval()).isTrue();
        pick.approve(2);
        assertThat(pick.getApprovedRevisionNumber()).isEqualTo(2);
        assertThat(pick.isSubmittedAfterApproval()).isFalse();
        assertThat(pick.getVersion()).isEqualTo(4);
    }

    @Test
    void givenMissingOrStaleRevision_whenReviewing_thenLeaveAggregateUnchanged() {
        // given / when / then
        var pick = pick();
        assertThatThrownBy(pick::returnForCorrection).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> pick.approve(1)).isInstanceOf(BusinessRuleException.class);
        assertThat(pick.getVersion()).isZero();
        pick.submit();
        assertThatThrownBy(pick::openMissing).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> pick.approve(2)).isInstanceOf(BusinessRuleException.class);
        assertThat(pick.getVersion()).isEqualTo(1);
        assertThat(pick.getApprovedRevisionNumber()).isNull();
    }

    @Test
    void givenStaleExpectedVersion_whenChecking_thenRejectWithoutChanges() {
        // given / when / then
        var pick = pick();
        pick.requireVersion(0);
        pick.submit();
        assertThatThrownBy(() -> pick.requireVersion(0)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        pick.requireVersion(1);
        assertThat(pick.getVersion()).isEqualTo(1);
    }

    @Test
    void givenApprovedHistory_whenReplayingCorrection_thenAdvanceVersionAndPreserveApprovedPointer() {
        // given
        var pick = pick();
        assertThatThrownBy(pick::correct).isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved");
        pick.submit();
        pick.approve(1);
        // when
        pick.correct();
        // then
        assertThat(pick.getVersion()).isEqualTo(3);
        assertThat(pick.getState()).isEqualTo(PickState.APPROVED);
        assertThat(pick.getApprovedRevisionNumber()).isEqualTo(1);
        assertThat(pick.isManualOpen()).isFalse();
    }

    private MatchdayPick pick() {
        var race = new Race();
        race.setId(UUID.randomUUID());
        var season = new Season("Test_PrivatePicks");
        season.setId(UUID.randomUUID());
        var team = new Team();
        team.setId(UUID.randomUUID());
        return new MatchdayPick(race, season, team);
    }
}
