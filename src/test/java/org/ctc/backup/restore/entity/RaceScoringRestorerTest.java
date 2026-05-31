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
 * Phase 75 / Plan 04 — Unit test for {@link RaceScoringRestorer}.
 *
 * <p>Schema (V1): {@code id UUID PK, name VARCHAR UNIQUE, race_points VARCHAR(500) NOT NULL,
 * quali_points VARCHAR(500) NULL, fastest_lap_points INT NOT NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}.
 *
 * <p>Critical: {@code race_points} and {@code quali_points} are VARCHAR(500) storing
 * comma-separated integers verbatim (e.g., {@code "25,18,15,12,10,8,6,4,2,1"}). MUST bind via
 * {@code setString} — never {@code setInt}/{@code setArray}.
 */
class RaceScoringRestorerTest {

    private final RaceScoringRestorer restorer = new RaceScoringRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaceScorings() {
        assertThat(restorer.tableName()).isEqualTo("race_scorings");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSampleScoring_whenRestoreCalled_thenRacePointsBoundAsString() throws Exception {
        // given — F1-style points list as a verbatim comma-separated VARCHAR(500)
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "F1 2024",
                  "racePoints": "25,18,15,12,10,8,6,4,2,1",
                  "qualiPoints": "3,2,1",
                  "fastestLapPoints": 1,
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
                .matches("^INSERT INTO race_scorings \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("race_points")
                .contains("quali_points")
                .contains("fastest_lap_points");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setString(2, "F1 2024");
        // race_points + quali_points BOTH bound as String — VARCHAR(500) integrity preserved
        verify(ps).setString(3, "25,18,15,12,10,8,6,4,2,1");
        verify(ps).setString(4, "3,2,1");
        verify(ps).setInt(5, 1);
        verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(7, Timestamp.valueOf("2025-01-16 11:30:00"));

        // critical negative assertion: race_points MUST NOT be bound as an int/array
        verify(ps, never()).setInt(3, 0);
        verify(ps, never()).setArray(eq(3), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenScoringWithoutQualiPoints_whenRestoreCalled_thenQualiPointsBoundAsNull() throws Exception {
        // given — qualiPoints is nullable
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Sprint",
                  "racePoints": "8,7,6,5,4,3,2,1",
                  "qualiPoints": null,
                  "fastestLapPoints": 0,
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
        verify(ps).setNull(4, java.sql.Types.VARCHAR);
    }
}
