package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.ctc.domain.model.LineupWindowKind;
import org.junit.jupiter.api.Test;

class LineupWindowPolicyTest {
    private static final LocalDateTime START = LocalDateTime.of(2035, 1, 10, 20, 0);
    private static final Instant DEADLINE = Instant.parse("2035-01-10T18:00:00Z");
    private final LineupWindowPolicy policy = new LineupWindowPolicy(Clock.fixed(DEADLINE, ZoneOffset.UTC));

    @Test
    void givenPublishedSettingsBeforeDeadline_whenAssessing_thenOpenUntilOneHourBeforeStart() {
        // when
        var decision = policy.assessAt(new LineupWindowPolicy.Context(true, true, null, START), DEADLINE.minusNanos(1000));
        // then
        assertThat(decision.editable()).as("one microsecond before the deadline").isTrue();
        assertThat(decision.deadline()).isEqualTo(DEADLINE);
    }

    @Test
    void givenDeadlineReached_whenAssessing_thenRejectTeams() {
        // when
        var decision = policy.assessAt(new LineupWindowPolicy.Context(true, true, LineupWindowKind.OPEN, START), DEADLINE);
        // then
        assertThat(decision.editable()).as("a manual opening never extends the deadline").isFalse();
        assertThat(decision.reason()).isEqualTo("The lineup deadline has passed");
    }

    @Test
    void givenNoStartTime_whenAssessing_thenOpenWithoutDeadline() {
        // when
        var decision = policy.assessAt(new LineupWindowPolicy.Context(true, true, null, null), Instant.parse("2099-01-01T00:00:00Z"));
        // then
        assertThat(decision.editable()).isTrue();
        assertThat(decision.deadline()).isNull();
        assertThat(decision.reason()).isEqualTo("Open, no start time scheduled");
    }

    @Test
    void givenUnpublishedSettings_whenAssessing_thenClosedUntilManagementOpens() {
        // given
        var unpublished = new LineupWindowPolicy.Context(true, false, null, START);
        var opened = new LineupWindowPolicy.Context(true, false, LineupWindowKind.OPEN, START);
        // when / then
        assertThat(policy.assessAt(unpublished, DEADLINE.minusSeconds(60)).reason()).isEqualTo("Opens when the settings for this leg are published");
        assertThat(policy.assessAt(opened, DEADLINE.minusSeconds(60)).editable()).as("management opening replaces the publication").isTrue();
    }

    @Test
    void givenManagementClosed_whenAssessing_thenClosedDespitePublication() {
        // when
        var decision = policy.assessAt(new LineupWindowPolicy.Context(true, true, LineupWindowKind.CLOSE, START), DEADLINE.minusSeconds(60));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Management closed lineups for this leg");
    }

    @Test
    void givenInvalidTarget_whenAssessing_thenClosed() {
        // when
        var decision = policy.assessAt(new LineupWindowPolicy.Context(false, true, LineupWindowKind.OPEN, START), DEADLINE.minusSeconds(60));
        // then
        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("The leg is not open for lineups");
    }

    @Test
    void givenNonexistentOrAmbiguousBerlinStart_whenAssessing_thenRequireReview() {
        // given
        var springGap = LocalDateTime.of(2035, 3, 25, 2, 30);
        var autumnOverlap = LocalDateTime.of(2035, 10, 28, 2, 30);
        // when / then
        for (var start : java.util.List.of(springGap, autumnOverlap)) {
            var decision = policy.assessAt(new LineupWindowPolicy.Context(true, true, null, start), Instant.parse("2035-01-01T00:00:00Z"));
            assertThat(decision.editable()).as("start %s", start).isFalse();
            assertThat(decision.reason()).isEqualTo("The start time requires management review");
        }
    }

    @Test
    void givenStartJustAfterClockChange_whenComputingDeadline_thenSubtractOneElapsedHour() {
        // when / then
        assertThat(LineupWindowPolicy.deadline(LocalDateTime.of(2035, 3, 25, 3, 30)))
                .as("03:30 CEST is 01:30 UTC; one elapsed hour earlier is 00:30 UTC").isEqualTo(Instant.parse("2035-03-25T00:30:00Z"));
        assertThat(LineupWindowPolicy.deadline(LocalDateTime.of(2035, 10, 28, 3, 30)))
                .as("03:30 CET is 02:30 UTC").isEqualTo(Instant.parse("2035-10-28T01:30:00Z"));
    }
}
