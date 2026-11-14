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
    @EnumSource(value = PickState.class, names = {"MISSING", "SUBMITTED", "RETURNED", "APPROVED"})
    void givenAnyPickStateBeforeDeadline_whenAssessing_thenAllowOrdinaryConfirmation(PickState state) {
        // given
        var policy = policy(CUTOFF.minusNanos(1));
        // when
        var decision = policy.assess(context(state, true, false, DEADLINE));
        // then
        assertThat(decision.editable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 3600})
    void givenExactOrElapsedDeadline_whenAssessing_thenCloseOrdinaryConfirmation(long secondsAfter) {
        // given
        var policy = policy(CUTOFF.plusSeconds(secondsAfter));
        // when
        var decision = policy.assess(context(PickState.SUBMITTED, true, false, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("deadline");
    }

    @Test
    void givenUnopenedMatchdayWithoutDeadline_whenAssessing_thenExplainThatCollectionIsClosed() {
        // given / when
        var decision = policy(CUTOFF).assess(context(PickState.MISSING, false, false, null));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("Matchday", "closed");
    }

    @ParameterizedTest
    @EnumSource(value = PickState.class, names = {"MISSING", "SUBMITTED", "RETURNED"})
    void givenOpenedMatchdayWithoutDeadline_whenAssessingUnapprovedPick_thenRemainEditable(PickState state) {
        // given / when
        var decision = policy(CUTOFF.plusSeconds(86400)).assess(context(state, true, false, null));
        // then
        assertThat(decision.editable()).isTrue();
    }

    @Test
    void givenLegacyTargetClosureInOpenedMatchdayBeforeDeadline_whenAssessing_thenCloseTakesPrecedence() {
        // given / when
        var decision = policy(CUTOFF.minusSeconds(3600)).assess(context(PickState.RETURNED, true, true, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("closed");
    }

    @Test
    void givenApprovedPickInOpenedMatchdayBeforeDeadline_whenAssessing_thenTeamMayResubmit() {
        // given / when
        var decision = policy(CUTOFF.minusSeconds(3600)).assess(context(PickState.APPROVED, true, false, DEADLINE));
        // then
        assertThat(decision.editable()).as("approval no longer locks the team before the deadline").isTrue();
    }

    @Test
    void givenInvalidTargetInOpenedMatchday_whenAssessing_thenDenyTeamWrites() {
        // given
        var context = new PickDeadlinePolicy.Context(false, true, false, null);
        // when
        var decision = policy(CUTOFF).assess(context);
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("The target is unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29T02:30:00", "2026-10-25T02:30:00"})
    void givenNonexistentOrAmbiguousBerlinDeadline_whenResolving_thenRejectWithoutChoosingAnOffset(String deadline) {
        // given
        var policy = policy(CUTOFF);
        // when / then
        assertThatThrownBy(() -> policy.resolveDeadline(LocalDateTime.parse(deadline)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Europe/Berlin");
        var decision = policy.assess(context(PickState.MISSING, true, false, LocalDateTime.parse(deadline)));
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

    @Test
    void givenPreparedClosedMatchdayWithFutureDeadline_whenAssessing_thenRequireCentralOpening() {
        // given
        var policy = policy(CUTOFF.minusSeconds(3600));
        // when
        var decision = policy.assess(context(PickState.MISSING, false, false, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("Matchday", "closed");
    }

    @Test
    void givenOpenedMatchdayAfterItsEffectiveDeadline_whenAssessing_thenDoNotBypassTheCutoff() {
        // given
        var policy = policy(CUTOFF.plusNanos(1));
        // when
        var decision = policy.assess(context(PickState.RETURNED, true, false, DEADLINE));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).contains("deadline");
    }

    private PickDeadlinePolicy policy(Instant instant) {
        return new PickDeadlinePolicy(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private PickDeadlinePolicy.Context context(PickState state, boolean open, boolean closed, LocalDateTime deadline) {
        return new PickDeadlinePolicy.Context(true, open, closed, deadline);
    }
}
