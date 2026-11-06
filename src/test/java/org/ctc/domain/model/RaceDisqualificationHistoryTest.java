package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RaceDisqualificationHistoryTest {
    private final RaceDisqualificationHistory.Identity identity = new RaceDisqualificationHistory.Identity(UUID.randomUUID(), UUID.randomUUID(),
            new RacePenaltyHistory.Identity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID()));
    private final LocalDateTime time = LocalDateTime.parse("2200-11-05T12:34:56.123456");

    private RaceDisqualificationHistory.Entry entry(int revision, boolean previous, boolean dsq) {
        return new RaceDisqualificationHistory.Entry(UUID.randomUUID(), identity, revision, previous, dsq, 1,
                "Test private evidence", "test-manager", "a".repeat(64), time, time);
    }

    @Test
    void givenNoHistory_whenCheckingState_thenAllowClassifiedButRequireEvidenceForDsq() {
        // when / then
        assertThat(RaceDisqualificationHistory.problems(identity, false, List.of())).isEmpty();
        assertThat(RaceDisqualificationHistory.problems(identity, true, List.of())).containsExactly("DSQ state has no permanent history");
    }

    @Test
    void givenRecordedClearAndSameStateReasonCorrection_whenCheckingChain_thenAcceptCompleteEvidence() {
        // given
        var first = entry(1, false, true); var correction = entry(2, true, true); var clear = entry(3, true, false);
        // when / then
        assertThat(RaceDisqualificationHistory.problems(identity, true, List.of(correction, first))).isEmpty();
        assertThat(RaceDisqualificationHistory.problems(identity, false, List.of(clear, first, correction))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revision", "id", "identity", "previous", "first-state", "position", "reason", "long-reason", "actor", "long-actor", "fingerprint", "created", "updated", "date-range", "precision"})
    void givenInvalidRevisionMetadata_whenCheckingHistory_thenReportWithoutLeakingPrivateValues(String kind) {
        // given
        var row = entry(1, false, true);
        row = new RaceDisqualificationHistory.Entry(kind.equals("id") ? null : row.id(),
                kind.equals("identity") ? new RaceDisqualificationHistory.Identity(UUID.randomUUID(), identity.driver(), identity.race()) : identity,
                kind.equals("revision") ? 2 : 1, kind.equals("previous"), !kind.equals("first-state"), kind.equals("position") ? 13 : 1,
                kind.equals("reason") ? " " : kind.equals("long-reason") ? "R".repeat(2001) : row.reason(),
                kind.equals("actor") ? " " : kind.equals("long-actor") ? "A".repeat(201) : row.actor(),
                kind.equals("fingerprint") ? "z".repeat(64) : row.fingerprint(),
                kind.equals("created") ? null : kind.equals("date-range") ? time.withYear(999) : kind.equals("precision") ? time.withNano(123456789) : time,
                kind.equals("updated") ? null : time);
        // when
        var problems = RaceDisqualificationHistory.problems(identity, true, List.of(row));
        // then
        assertThat(problems).isNotEmpty(); assertThat(problems.toString()).doesNotContain("Test private evidence", "test-manager");
    }

    @Test
    void givenDuplicateIdsOrBrokenStateChain_whenCheckingHistory_thenRejectBoth() {
        // given
        var first = entry(1, false, true); var broken = entry(2, false, false);
        var duplicate = new RaceDisqualificationHistory.Entry(first.id(), identity, 2, true, false, 1,
                first.reason(), first.actor(), first.fingerprint(), time, time);
        // when / then
        assertThat(RaceDisqualificationHistory.problems(identity, false, List.of(first, duplicate))).anyMatch(p -> p.contains("IDs"));
        assertThat(RaceDisqualificationHistory.problems(identity, false, List.of(first, broken))).anyMatch(p -> p.contains("chain"));
        assertThat(RaceDisqualificationHistory.problems(identity, false, List.of(first))).anyMatch(p -> p.contains("latest revision"));
    }

    @Test
    void givenRevisionBudgetExceeded_whenCheckingHistory_thenRejectBeforeTraversingEntries() {
        // given
        var rows = Collections.nCopies(RaceDisqualificationHistory.MAX_REVISIONS + 1, entry(1, false, true));
        // when / then
        assertThat(RaceDisqualificationHistory.problems(identity, true, rows)).containsExactly("DSQ history exceeds its revision budget");
    }
}
