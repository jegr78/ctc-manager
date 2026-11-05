package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RacePenaltyHistoryTest {
    private final RacePenaltyHistory.Identity identity = new RacePenaltyHistory.Identity(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            UUID.randomUUID(), UUID.randomUUID());

    @Test
    void givenLegacyNegativeGrossWithoutAnyCorrection_whenCalculating_thenPreserveExistingScore() {
        // given / when
        var totals = RacePenaltyHistory.totals(new Race(), UUID.randomUUID(), -5, -8);
        // then
        assertThat(totals.home().official()).isEqualTo(-5); assertThat(totals.away().official()).isEqualTo(-8);
    }

    @Test
    void givenCompleteHistory_whenValidated_thenAcceptEveryRevisionIncludingZeroReset() {
        // when / then
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, 20, 10, "Reviewed", "manager", "a".repeat(64)),
                entry(2, 0, 0, "Correction reset", "manager", "b".repeat(64))))).isEmpty();
    }

    @Test
    void givenInvalidHistory_whenValidated_thenReportEveryRequiredGuardWithoutPrivateText() {
        // when / then
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(2, 0, 0, "Reviewed", "manager", "a".repeat(64)))))
                .anyMatch(message -> message.contains("sequence"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, -1, 0, "Reviewed", "manager", "a".repeat(64)))))
                .anyMatch(message -> message.contains("nonnegative"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, 0, -1, "Reviewed", "manager", "a".repeat(64)))))
                .anyMatch(message -> message.contains("nonnegative"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, 0, 0, " ", "manager", "a".repeat(64)))))
                .anyMatch(message -> message.contains("reason"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, 0, 0, "Reviewed", " ", "a".repeat(64)))))
                .anyMatch(message -> message.contains("actor"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(entry(1, 0, 0, "Private verdict", "Private actor", "invalid"))))
                .anyMatch(message -> message.contains("fingerprint"))
                .noneMatch(message -> message.contains("Private"));
        var other = new RacePenaltyHistory.Identity(identity.race(), identity.season(), identity.phase(), identity.matchday(),
                UUID.randomUUID(), null, identity.home(), identity.away());
        assertThat(RacePenaltyHistory.problems(other, List.of(entry(1, 0, 0, "Reviewed", "manager", "a".repeat(64)))))
                .anyMatch(message -> message.contains("identity"));
        assertThat(RacePenaltyHistory.problems(identity, List.of(new RacePenaltyHistory.Entry(UUID.randomUUID(), identity,
                1, 0, 0, "Reviewed", "manager", "a".repeat(64), null, null))))
                .anyMatch(message -> message.contains("timestamps"));
    }

    @Test
    void givenOverBudgetOrDuplicateRevisionIds_whenValidating_thenRejectBoundedHistory() {
        // given
        var row = entry(1, 0, 0, "Reviewed", "manager", "a".repeat(64));
        // when / then
        assertThat(RacePenaltyHistory.problems(identity, java.util.Collections.nCopies(RacePenaltyHistory.MAX_REVISIONS + 1, row))).containsExactly("Penalty history exceeds its revision budget");
        assertThat(RacePenaltyHistory.problems(identity, List.of(row, row))).anyMatch(problem -> problem.contains("IDs"));
        var oldTime = LocalDateTime.of(999, 1, 1, 0, 0);
        assertThat(RacePenaltyHistory.problems(identity, List.of(new RacePenaltyHistory.Entry(row.id(), identity, 1, 0, 0, row.reason(), row.actor(), row.fingerprint(), oldTime, oldTime))))
                .anyMatch(problem -> problem.contains("range"));
    }

    @Test
    void givenReversedLegsOrUniqueParentAlias_whenMatchingParticipants_thenAcceptOnlyUnambiguousParentPairing() {
        // given
        var home = identity.home(); var away = identity.away(); var sub = UUID.randomUUID(); var stranger = UUID.randomUUID();
        // when / then
        assertThat(RacePenaltyHistory.sameParticipants(home, away, away, home, away, home)).isTrue();
        assertThat(RacePenaltyHistory.sameParticipants(home, away, sub, away, home, away)).isTrue();
        assertThat(RacePenaltyHistory.sameParticipants(home, away, stranger, away, stranger, away)).isFalse();
        assertThat(RacePenaltyHistory.sameParticipants(home, away, sub, stranger, home, home)).isFalse();
        assertThat(RacePenaltyHistory.sameParticipants(null, away, home, away, home, away)).isFalse();
        assertThat(RacePenaltyHistory.sameParticipants(home, home, home, away, home, away)).isFalse();
    }

    private RacePenaltyHistory.Entry entry(int revision, int home, int away, String reason, String actor, String fingerprint) {
        var now = LocalDateTime.of(2026, 11, 4, 12, 0);
        return new RacePenaltyHistory.Entry(UUID.randomUUID(), identity, revision, home, away, reason, actor, fingerprint, now, now);
    }
}
