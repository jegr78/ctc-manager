package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.LineupPayload;
import org.junit.jupiter.api.Test;

class LineupBackupValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID race = UUID.randomUUID();
    private final UUID team = UUID.randomUUID();
    private final UUID submission = UUID.randomUUID();
    private final String payload = new LineupPayload(6, List.of(LineupPayload.Entry.proposal("Alpha", null))).toJson();

    @Test
    void givenConsistentHistory_whenValidating_thenAccept() {
        // given
        var validator = base(23);
        validator.accept("lineup_submission_revisions", revision(1, "TEAM", null));
        validator.accept("lineup_submission_revisions", revision(2, "MANAGEMENT", "Late correction"));
        validator.accept("lineup_window_events", window(1));
        // when / then
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenRevisionGap_whenValidating_thenReject() {
        // given
        var validator = base(23);
        validator.accept("lineup_submission_revisions", revision(2, "TEAM", null));
        // when / then
        assertThatThrownBy(validator::validate).hasMessage("Lineup revisions must number 1 to the latest revision without gaps");
    }

    @Test
    void givenOlderSchemaWithLineupRows_whenAccepting_thenReject() {
        // given
        var validator = new LineupBackupValidator(22);
        // when / then
        assertThatThrownBy(() -> validator.accept("lineup_submissions", submission(2, null))).hasMessage("Lineup submissions require backup schema 23");
    }

    @Test
    void givenTeamRevisionWithReasonOrInvalidPayload_whenAccepting_thenReject() {
        // given
        var validator = base(23);
        var invalid = revision(3, "TEAM", null).put("payload", "{}");
        // when / then
        assertThatThrownBy(() -> validator.accept("lineup_submission_revisions", revision(1, "TEAM", "why"))).hasMessage("Only management corrections carry a reason");
        assertThatThrownBy(() -> validator.accept("lineup_submission_revisions", invalid)).hasMessage("Lineup payload has an invalid shape");
    }

    @Test
    void givenAppliedBeyondLatestOrMissingRace_whenValidating_thenReject() {
        // given
        var applied = new LineupBackupValidator(23);
        var orphan = new LineupBackupValidator(23);
        orphan.accept("teams", mapper.createObjectNode().put("id", team.toString()));
        orphan.accept("lineup_submissions", submission(1, null));
        orphan.accept("lineup_submission_revisions", revision(1, "TEAM", null));
        // when / then
        assertThatThrownBy(() -> applied.accept("lineup_submissions", submission(1, 2))).hasMessage("A lineup submission has an inconsistent applied revision");
        assertThatThrownBy(orphan::validate).hasMessage("A lineup submission references a missing race or team");
    }

    private LineupBackupValidator base(int version) {
        var validator = new LineupBackupValidator(version);
        validator.accept("races", mapper.createObjectNode().put("id", race.toString()));
        validator.accept("teams", mapper.createObjectNode().put("id", team.toString()));
        validator.accept("lineup_submissions", submission(2, 1));
        return validator;
    }

    private ObjectNode submission(int latest, Integer applied) {
        var row = mapper.createObjectNode().put("id", submission.toString()).put("race", race.toString()).put("team", team.toString())
                .put("latestRevisionNumber", latest).put("version", latest);
        if (applied != null) row.put("appliedRevisionNumber", applied).put("appliedAt", "2035-01-01T10:00:00Z").put("appliedBy", "manager");
        return row;
    }

    private ObjectNode revision(int number, String kind, String reason) {
        var row = mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("submission", submission.toString())
                .put("revisionNumber", number).put("payload", payload).put("submittedAt", "2035-01-01T09:00:00Z")
                .put("actorKind", kind).put("actorId", "123").put("requestId", UUID.randomUUID().toString()).put("aggregateVersion", number);
        if (reason != null) row.put("reason", reason);
        return row;
    }

    private ObjectNode window(int sequence) {
        return mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("race", race.toString()).put("sequenceNumber", sequence)
                .put("kind", "OPEN").put("reason", "Settings posted by hand").put("actorId", "manager").put("occurredAt", "2035-01-01T08:00:00Z");
    }
}
