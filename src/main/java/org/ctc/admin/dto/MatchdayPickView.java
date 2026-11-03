package org.ctc.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickState;

public final class MatchdayPickView {
    private MatchdayPickView() { }
    public record Choice(UUID id, String label) { }
    public record Row(UUID raceId, UUID seasonId, UUID matchdayId, String label, String season, String matchday,
                      PickState state, long version, Integer revisionNumber, Integer approvedRevisionNumber, String deadline, String deadlineState,
                      boolean manualOpen, boolean manualClosed, String teamEditing, boolean targetValid,
                      List<PickReviewForm.Action> actions, boolean correctable) {
        public Row { actions = List.copyOf(actions); }
    }
    public record Overview(UUID seasonId, UUID matchdayId, PickState state, List<Choice> seasons, List<Choice> matchdays, List<Row> rows) {
        public Overview { seasons = List.copyOf(seasons); matchdays = List.copyOf(matchdays); rows = List.copyOf(rows); }
    }
    public record Value(String label, String value) { }
    public record Comparison(String label, String existing, String submitted, boolean changed) { }
    public record Revision(int number, long version, String author, Instant confirmedAt, List<Value> values) {
        public Revision { values = List.copyOf(values); }
    }
    public record Decision(PickDecisionKind kind, long version, String actor, String reason, Instant decidedAt, List<Value> correction) {
        public Decision { correction = List.copyOf(correction); }
    }
    public record Publication(String state, Long version, List<String> fields, UUID batchId, boolean pending) {
        public Publication { fields = List.copyOf(fields); }
    }
    public record Detail(Row row, String fingerprint, boolean requiresReplacement, String returnReason, List<Comparison> comparison,
                         List<Revision> revisions, List<Decision> decisions, String generation, Publication publication) {
        public Detail { comparison = List.copyOf(comparison); revisions = List.copyOf(revisions); decisions = List.copyOf(decisions); }
    }
}
