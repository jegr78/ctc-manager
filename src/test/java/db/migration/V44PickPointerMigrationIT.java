package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class V44PickPointerMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    TestHelper.SeasonFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = tx.execute(status -> helper.createFullSeasonFixture("V44" + UUID.randomUUID().toString().substring(0, 6)));
    }

    @AfterEach
    void cleanUp() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("DELETE FROM matchday_picks WHERE race_id = ?")) {
            statement.setString(1, fixture.race().getId().toString());
            statement.executeUpdate();
        }
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            teams.deleteAllById(java.util.List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,2,1", "RETURNED,3,2", "SUBMITTED,1,", "APPROVED,2,2"})
    void givenMigratedSchema_whenStoringResubmissionAfterApprovalFromNewConnection_thenAcceptValidPointers(String state, int latest, Integer approved) {
        // when / then
        assertThatCode(() -> insert(state, latest, approved)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,1,1", "SUBMITTED,2,3", "RETURNED,2,0", "APPROVED,3,2"})
    void givenMigratedSchema_whenStoringImpossiblePointersFromNewConnection_thenRejectThem(String state, int latest, Integer approved) {
        // when / then
        assertThatThrownBy(() -> insert(state, latest, approved)).isInstanceOf(SQLException.class);
    }

    @Test
    void givenMigratedSchema_whenInspectingColumns_thenActivationFlagsAreGone() throws SQLException {
        // when / then
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            for (var table : java.util.List.of(new String[] {"races", "picks_managed"}, new String[] {"seasons", "picks_collection_enabled"})) {
                boolean found = false;
                for (var name : java.util.List.of(table[0], table[0].toUpperCase(java.util.Locale.ROOT))) {
                    for (var column : java.util.List.of(table[1], table[1].toUpperCase(java.util.Locale.ROOT))) {
                        try (var columns = metadata.getColumns(connection.getCatalog(), null, name, column)) { found |= columns.next(); }
                    }
                }
                assertThat(found).as("%s.%s must be dropped", table[0], table[1]).isFalse();
            }
        }
    }

    private void insert(String state, int latest, Integer approved) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO matchday_picks (id, race_id, season_id, team_id, original_matchday_id, original_away_team_id,
                    aggregate_version, state, latest_revision_number, approved_revision_number, manual_open, manual_closed)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, FALSE, FALSE)""")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, fixture.race().getId().toString());
            statement.setString(3, fixture.season().getId().toString());
            statement.setString(4, fixture.homeTeam().getId().toString());
            statement.setString(5, fixture.matchday().getId().toString());
            statement.setString(6, fixture.awayTeam().getId().toString());
            statement.setString(7, state);
            statement.setInt(8, latest);
            if (approved == null) statement.setNull(9, java.sql.Types.INTEGER); else statement.setInt(9, approved);
            statement.executeUpdate();
        }
    }
}
