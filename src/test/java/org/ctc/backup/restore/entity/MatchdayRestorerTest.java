package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 04 — Unit test for {@link MatchdayRestorer}.
 *
 * <p>Asserts the V1+V3 schema column order ({@code id, phase_id, group_id, label, sort_index,
 * created_at, updated_at}), the auto-chunking {@code batchUpdate} flavor, and that the setter
 * binds {@code phase}/{@code group} FK references via UUID and {@code sort_index} via setInt.
 */
class MatchdayRestorerTest {

    private final MatchdayRestorer restorer = new MatchdayRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsMatchdays() {
        assertThat(restorer.tableName()).isEqualTo("matchdays");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleRow_whenRestoreCalled_thenBatchUpdateInvokedWithInsertSqlAndBatchSize500() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "phase": "22222222-2222-2222-2222-222222222222",
                  "group": "33333333-3333-3333-3333-333333333333",
                  "label": "Matchday 1",
                  "sortIndex": 5,
                  "createdAt": "2025-01-15T10:00:00",
                  "updatedAt": "2025-01-16T11:30:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then — batchUpdate(sql, list, 500, setter) signature
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<JsonNode>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(sqlCaptor.capture(), rowsCaptor.capture(),
                eq(500), setterCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .matches("^INSERT INTO matchdays \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("matchdays")
                .contains("phase_id")
                .contains("group_id")
                .contains("label")
                .contains("sort_index")
                .contains("created_at")
                .contains("updated_at");
        assertThat(rowsCaptor.getValue()).hasSize(1);

        // then — setter binds 7 columns
        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(3, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        verify(ps).setString(4, "Matchday 1");
        verify(ps).setInt(5, 5);
        verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(7, Timestamp.valueOf("2025-01-16 11:30:00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenRowWithNullGroup_whenRestoreCalled_thenGroupBoundAsSqlNull() throws Exception {
        // given — phase is non-null (DDL NOT NULL), group is nullable
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "phase": "22222222-2222-2222-2222-222222222222",
                  "group": null,
                  "label": "Matchday 2",
                  "sortIndex": 2,
                  "createdAt": "2025-01-15T10:00:00",
                  "updatedAt": "2025-01-16T11:30:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(any(String.class), any(List.class), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setNull(3, java.sql.Types.OTHER);
    }
}
