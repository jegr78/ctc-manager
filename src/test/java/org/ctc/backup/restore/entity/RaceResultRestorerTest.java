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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 04 — Unit test for {@link RaceResultRestorer}.
 *
 * <p>Hottest path of the entire restore on the Season-2023 fixture (~1000 rows).
 *
 * <p>Schema (V1): {@code id, race_id, driver_id, position, quali_position, fastest_lap,
 * points_race, points_quali, points_fl, points_total, created_at, updated_at}. {@code position}
 * and {@code quali_position} bind via {@code setInt}; {@code fastest_lap} via
 * {@code setBoolean} (NOT {@code setString}); the four point columns via {@code setInt}.
 */
class RaceResultRestorerTest {

    private final RaceResultRestorer restorer = new RaceResultRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaceResults() {
        assertThat(restorer.tableName()).isEqualTo("race_results");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSampleResult_whenRestoreCalled_thenIntsAndBooleanBoundCorrectly() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "driver": "33333333-3333-3333-3333-333333333333",
                  "position": 2,
                  "qualiPosition": 4,
                  "fastestLap": true,
                  "pointsRace": 18,
                  "pointsQuali": 6,
                  "pointsFl": 1,
                  "pointsTotal": 25,
                  "createdAt": "2025-04-15T22:00:00",
                  "updatedAt": "2025-04-15T22:00:00"
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
                .matches("^INSERT INTO race_results \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("position")
                .contains("quali_position")
                .contains("fastest_lap")
                .contains("points_race")
                .contains("points_quali")
                .contains("points_fl")
                .contains("points_total");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(3, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        verify(ps).setInt(4, 2);
        verify(ps).setInt(5, 4);
        verify(ps).setBoolean(6, true);
        verify(ps).setInt(7, 18);
        verify(ps).setInt(8, 6);
        verify(ps).setInt(9, 1);
        verify(ps).setInt(10, 25);
        verify(ps).setTimestamp(11, Timestamp.valueOf("2025-04-15 22:00:00"));
        verify(ps).setTimestamp(12, Timestamp.valueOf("2025-04-15 22:00:00"));
    }
}
