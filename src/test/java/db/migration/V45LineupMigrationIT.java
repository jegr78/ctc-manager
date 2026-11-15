package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.TestHelper;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class V45LineupMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    TestHelper.SeasonFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = tx.execute(status -> helper.createFullSeasonFixture("V45" + UUID.randomUUID().toString().substring(0, 6)));
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            teams.deleteAllById(java.util.List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenMigratedSeason_whenReading_thenDefaultToSixDrivers() {
        // when / then
        assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLineupSize()).isEqualTo(6);
    }

    @ParameterizedTest
    @CsvSource({"1,true", "12,true", "0,false", "13,false"})
    void givenNewConnection_whenStoringLineupSize_thenEnforceRange(int size, boolean valid) {
        // when
        var update = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> execute("UPDATE seasons SET lineup_size = ? WHERE id = ?", size, fixture.season().getId().toString());
        // then
        if (valid) assertThatCode(update).doesNotThrowAnyException(); else assertThatThrownBy(update).isInstanceOf(SQLException.class);
    }

    @ParameterizedTest
    @CsvSource({"1,", "2,1", "2,2"})
    void givenNewConnection_whenStoringValidSubmissionPointers_thenAccept(int latest, Integer applied) {
        // when / then
        assertThatCode(() -> submission(UUID.randomUUID(), latest, applied, applied != null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"0,,false", "1,2,true", "1,1,false", "1,0,true"})
    void givenNewConnection_whenStoringImpossibleSubmissionPointers_thenReject(int latest, Integer applied, boolean appliedMetadata) {
        // when / then
        assertThatThrownBy(() -> submission(UUID.randomUUID(), latest, applied, appliedMetadata)).isInstanceOf(SQLException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"TEAM,NULL,true", "MANAGEMENT,Late correction,true", "TEAM,Reason,false", "MANAGEMENT,NULL,false", "OTHER,NULL,false"}, nullValues = "NULL")
    void givenNewConnection_whenStoringRevisionActors_thenRequireReasonOnlyForManagement(String kind, String reason, boolean valid) throws SQLException {
        // given
        var submission = UUID.randomUUID();
        submission(submission, 1, null, false);
        // when
        var insert = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> execute("""
                INSERT INTO lineup_submission_revisions (id, submission_id, revision_number, payload, submitted_at, actor_kind, actor_id, reason,
                    request_id, aggregate_version, created_at, updated_at) VALUES (?, ?, 1, '{}', ?, ?, 'actor', ?, ?, 1, ?, ?)""",
                UUID.randomUUID().toString(), submission.toString(), now(), kind, reason, UUID.randomUUID().toString(), now(), now());
        // then
        if (valid) assertThatCode(insert).doesNotThrowAnyException(); else assertThatThrownBy(insert).isInstanceOf(SQLException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOGGLE", "REOPEN"})
    void givenNewConnection_whenStoringUnknownWindowKind_thenReject(String kind) {
        // when / then
        assertThatThrownBy(() -> window(kind, 1)).isInstanceOf(SQLException.class);
        assertThatCode(() -> window("CLOSE", 1)).doesNotThrowAnyException();
    }

    @Test
    void givenStoredHistory_whenDeletingRaceFromNewConnection_thenRejectAndKeepHistory() throws SQLException {
        // given
        submission(UUID.randomUUID(), 1, null, false);
        window("OPEN", 1);
        // when / then
        assertThatThrownBy(() -> execute("DELETE FROM races WHERE id = ?", fixture.race().getId().toString())).isInstanceOf(SQLException.class);
        assertThat(count("lineup_submissions")).as("lineup history is never cascaded away").isEqualTo(1);
        assertThat(count("lineup_window_events")).isEqualTo(1);
    }

    private void submission(UUID id, int latest, Integer applied, boolean appliedMetadata) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO lineup_submissions (id, race_id, team_id, latest_revision_number, applied_revision_number, applied_at, applied_by,
                    version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)""")) {
            statement.setString(1, id.toString());
            statement.setString(2, fixture.race().getId().toString());
            statement.setString(3, fixture.homeTeam().getId().toString());
            statement.setInt(4, latest);
            if (applied == null) statement.setNull(5, Types.INTEGER); else statement.setInt(5, applied);
            statement.setTimestamp(6, appliedMetadata ? now() : null);
            statement.setString(7, appliedMetadata ? "manager" : null);
            statement.setTimestamp(8, now());
            statement.setTimestamp(9, now());
            statement.executeUpdate();
        }
    }

    private void window(String kind, int sequence) throws SQLException {
        execute("""
                INSERT INTO lineup_window_events (id, race_id, sequence_number, kind, reason, actor_id, occurred_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Reason', 'actor', ?, ?, ?)""", UUID.randomUUID().toString(), fixture.race().getId().toString(), sequence, kind, now(), now(), now());
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private long count(String table) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE race_id = ?")) {
            statement.setString(1, fixture.race().getId().toString());
            try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.of(2035, 1, 1, 12, 0)); }
}
