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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 04 — Unit test for {@link MatchRestorer}.
 *
 * <p>Schema (V1, no V3+ additions): {@code id, matchday_id, home_team_id, away_team_id,
 * home_score, away_score, bye, created_at, updated_at}. The {@code bye} column is boolean —
 * the test verifies {@code ps.setBoolean}, not {@code setInt}/{@code setString}. Nullable
 * columns ({@code away_team_id}, {@code home_score}, {@code away_score}) bind via
 * {@code setNull}.
 */
class MatchRestorerTest {

    private final MatchRestorer restorer = new MatchRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsMatches() {
        assertThat(restorer.tableName()).isEqualTo("matches");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenFullRow_whenRestoreCalled_thenAllColumnsBoundWithCorrectTypes() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "matchday": "22222222-2222-2222-2222-222222222222",
                  "homeTeam": "33333333-3333-3333-3333-333333333333",
                  "awayTeam": "44444444-4444-4444-4444-444444444444",
                  "homeScore": 3,
                  "awayScore": 1,
                  "bye": false,
                  "createdAt": "2025-01-15T10:00:00",
                  "updatedAt": "2025-01-16T11:30:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(sqlCaptor.capture(), any(List.class),
                eq(500), setterCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .matches("^INSERT INTO matches \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(3, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        verify(ps).setObject(4, UUID.fromString("44444444-4444-4444-4444-444444444444"));
        verify(ps).setInt(5, 3);
        verify(ps).setInt(6, 1);
        verify(ps).setBoolean(7, false);
        verify(ps).setTimestamp(8, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(9, Timestamp.valueOf("2025-01-16 11:30:00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenByeRow_whenRestoreCalled_thenAwayTeamAndScoresBoundAsNull() throws Exception {
        // given — bye match: no away team, no scores
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "matchday": "22222222-2222-2222-2222-222222222222",
                  "homeTeam": "33333333-3333-3333-3333-333333333333",
                  "awayTeam": null,
                  "homeScore": null,
                  "awayScore": null,
                  "bye": true,
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
        verify(ps).setNull(4, java.sql.Types.OTHER);  // away_team_id
        verify(ps).setNull(5, java.sql.Types.INTEGER); // home_score
        verify(ps).setNull(6, java.sql.Types.INTEGER); // away_score
        verify(ps).setBoolean(7, true);
    }
}
