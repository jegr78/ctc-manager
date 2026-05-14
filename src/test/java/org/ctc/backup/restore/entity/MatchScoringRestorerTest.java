package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 04 — Unit test for {@link MatchScoringRestorer}.
 *
 * <p>Leaf entity (no FKs). Schema (V1): {@code id, name, points_win, points_draw,
 * points_loss, created_at, updated_at}. All three point columns are {@code INT NOT NULL}.
 */
class MatchScoringRestorerTest {

    private final MatchScoringRestorer restorer = new MatchScoringRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsMatchScorings() {
        assertThat(restorer.tableName()).isEqualTo("match_scorings");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSampleScoring_whenRestoreCalled_thenIntPointsBoundAsInt() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Standard 3-1-0",
                  "pointsWin": 3,
                  "pointsDraw": 1,
                  "pointsLoss": 0,
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
                .matches("^INSERT INTO match_scorings \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("points_win")
                .contains("points_draw")
                .contains("points_loss");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setString(2, "Standard 3-1-0");
        verify(ps).setInt(3, 3);
        verify(ps).setInt(4, 1);
        verify(ps).setInt(5, 0);
        verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(7, Timestamp.valueOf("2025-01-16 11:30:00"));
    }
}
