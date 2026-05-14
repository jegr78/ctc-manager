package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link SeasonDriverRestorer}.
 *
 * <p>Note: the schema-level column is {@code team_id} (not {@code season_team_id}); the join
 * is to {@code teams} not {@code season_teams} — see {@code V1__initial_schema.sql:68-79} and
 * {@code SeasonDriver.team @JoinColumn(name = "team_id")}. The plan's interfaces block
 * referenced {@code season_team_id} which contradicts the V1 schema; the schema is the source
 * of truth (Rule 3 — blocking issue resolved by following V1__initial_schema.sql).
 */
@ExtendWith(MockitoExtension.class)
class SeasonDriverRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private SeasonDriverRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new SeasonDriverRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsSeasonDrivers() {
        assertThat(restorer.tableName()).isEqualTo("season_drivers");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(SeasonDriverRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(SeasonDriverRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleSeasonDriverRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "11111111-5555-5555-5555-111111111111",
                  "season": "aaaaaaaa-5555-5555-5555-aaaaaaaaaaaa",
                  "driver": "bbbbbbbb-5555-5555-5555-bbbbbbbbbbbb",
                  "team": "cccccccc-5555-5555-5555-cccccccccccc",
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(List.of(row)), eq(500),
                setterCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).matches("^INSERT INTO season_drivers \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "season_id", "driver_id", "team_id",
                "created_at", "updated_at");

        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("11111111-5555-5555-5555-111111111111"));
        verify(preparedStatement).setObject(2, UUID.fromString("aaaaaaaa-5555-5555-5555-aaaaaaaaaaaa"));
        verify(preparedStatement).setObject(3, UUID.fromString("bbbbbbbb-5555-5555-5555-bbbbbbbbbbbb"));
        verify(preparedStatement).setObject(4, UUID.fromString("cccccccc-5555-5555-5555-cccccccccccc"));
        verify(preparedStatement).setTimestamp(5,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(6,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
    }
}
