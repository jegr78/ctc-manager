package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickDeadlinePolicyTest {
    private static final LocalDateTime DEADLINE = LocalDateTime.parse("2026-11-02T20:00:00");
    private static final Instant CUTOFF = Instant.parse("2026-11-02T19:00:00Z");

    @ParameterizedTest
    @EnumSource(value = PickState.class, names = {"MISSING", "SUBMITTED", "RETURNED"})
    void givenUnapprovedPickBeforeDeadline_whenAssessing_thenAllowOrdinaryConfirmation(PickState state) {
        // given
        var policy = policy(CUTOFF.minusNanos(1));
        // when
        var decision = policy.assess(context(state, false, false, DEADLINE));
        // then
        assertThat(decision.editable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 3600})
    void givenExactOrElapsedDeadline_whenAssessing_thenCloseOrdinaryConfirmation(long secondsAfter) {
        // given
        var policy = policy(CUTOFF.plusSeconds(secondsAfter));
        // when
        var decision = policy.assess(context(PickState.SUBMITTED, false, false, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("deadline");
    }

    @Test
    void givenNoDeadline_whenAssessingOrdinaryMissingPick_thenRequireDeadlineOrManagementOpening() {
        // given / when
        var decision = policy(CUTOFF).assess(context(PickState.MISSING, false, false, null));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("deadline", "management");
    }

    @ParameterizedTest
    @EnumSource(value = PickState.class, names = {"MISSING", "SUBMITTED", "RETURNED"})
    void givenManualOpeningWithoutDeadline_whenAssessingUnapprovedPick_thenRemainEditable(PickState state) {
        // given / when
        var decision = policy(CUTOFF.plusSeconds(86400)).assess(context(state, true, false, null));
        // then
        assertThat(decision.editable()).isTrue();
    }

    @Test
    void givenManualCloseAndOpeningBeforeDeadline_whenAssessing_thenCloseTakesPrecedence() {
        // given / when
        var decision = policy(CUTOFF.minusSeconds(3600)).assess(context(PickState.RETURNED, true, true, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("closed");
    }

    @Test
    void givenApprovedPickAndManualOpeningBeforeDeadline_whenAssessing_thenApprovalLocksTeamEditing() {
        // given / when
        var decision = policy(CUTOFF.minusSeconds(3600)).assess(context(PickState.APPROVED, true, false, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("Approved");
    }

    @ParameterizedTest
    @ValueSource(strings = {"season-disabled", "bot-disabled", "invalid-target"})
    void givenUnavailableCollectionOrTargetWithManualOpening_whenAssessing_thenDenyTeamWrites(String unavailable) {
        // given
        var context = new PickDeadlinePolicy.Context(PickState.SUBMITTED, !unavailable.equals("season-disabled"),
                !unavailable.equals("bot-disabled"), !unavailable.equals("invalid-target"), true, false, null);
        // when
        var decision = policy(CUTOFF).assess(context);
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29T02:30:00", "2026-10-25T02:30:00"})
    void givenNonexistentOrAmbiguousBerlinDeadline_whenResolving_thenRejectWithoutChoosingAnOffset(String deadline) {
        // given
        var policy = policy(CUTOFF);
        // when / then
        assertThatThrownBy(() -> policy.resolveDeadline(LocalDateTime.parse(deadline)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Europe/Berlin");
        var decision = policy.assess(context(PickState.MISSING, false, false, LocalDateTime.parse(deadline)));
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("review");
    }

    @Test
    void givenWinterAndSummerDeadlines_whenResolvingWithUtcClock_thenUseBerlinOffsets() {
        // given
        var policy = policy(CUTOFF);
        // when / then
        assertThat(policy.resolveDeadline(DEADLINE)).isEqualTo(CUTOFF);
        assertThat(policy.resolveDeadline(LocalDateTime.parse("2026-07-01T20:00:00")))
                .isEqualTo(Instant.parse("2026-07-01T18:00:00Z"));
    }

    private PickDeadlinePolicy policy(Instant instant) {
        return new PickDeadlinePolicy(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private PickDeadlinePolicy.Context context(PickState state, boolean open, boolean closed, LocalDateTime deadline) {
        return new PickDeadlinePolicy.Context(state, true, true, true, open, closed, deadline);
    }
}
