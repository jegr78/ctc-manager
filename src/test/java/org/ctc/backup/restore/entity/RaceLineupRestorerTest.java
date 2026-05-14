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
 * Phase 75 / Plan 04 — Unit test for {@link RaceLineupRestorer}.
 *
 * <p>RaceLineup is source-of-truth for driver-team assignments (memory
 * {@code feedback_racelineup_source_of_truth.md}). The setter MUST preserve the
 * {@code race_id} / {@code driver_id} / {@code team_id} triple verbatim from JSON to JDBC.
 *
 * <p>Schema (V1): {@code id, race_id, driver_id, team_id, created_at, updated_at}.
 * No nullable columns — all four UUIDs are NOT NULL per UNIQUE constraint
 * {@code uk_race_lineup_driver}.
 */
class RaceLineupRestorerTest {

    private final RaceLineupRestorer restorer = new RaceLineupRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaceLineups() {
        assertThat(restorer.tableName()).isEqualTo("race_lineups");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSampleLineup_whenRestoreCalled_thenRaceDriverTeamTripleBoundAsUuids() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "driver": "33333333-3333-3333-3333-333333333333",
                  "team": "44444444-4444-4444-4444-444444444444",
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
                .matches("^INSERT INTO race_lineups \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("race_id")
                .contains("driver_id")
                .contains("team_id");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(3, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        verify(ps).setObject(4, UUID.fromString("44444444-4444-4444-4444-444444444444"));
        verify(ps).setTimestamp(5, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(6, Timestamp.valueOf("2025-01-16 11:30:00"));
    }
}
