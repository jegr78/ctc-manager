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
 * Phase 75 / Plan 04 — Unit test for {@link RaceRestorer}.
 *
 * <p>Schema (V1): {@code id, matchday_id, match_id, playoff_matchup_id, track_id, car_id,
 * home_team_id, away_team_id, date_time, calendar_event_id, created_at, updated_at}. All
 * FKs except {@code matchday_id} are nullable.
 */
class RaceRestorerTest {

    private final RaceRestorer restorer = new RaceRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaces() {
        assertThat(restorer.tableName()).isEqualTo("races");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenFullyPopulatedRow_whenRestoreCalled_thenAllFkColumnsBoundAsUuid() throws Exception {
        // given
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "matchday": "22222222-2222-2222-2222-222222222222",
                  "match": "33333333-3333-3333-3333-333333333333",
                  "playoffMatchup": "44444444-4444-4444-4444-444444444444",
                  "track": "55555555-5555-5555-5555-555555555555",
                  "car": "66666666-6666-6666-6666-666666666666",
                  "homeTeamOverride": "77777777-7777-7777-7777-777777777777",
                  "awayTeamOverride": "88888888-8888-8888-8888-888888888888",
                  "dateTime": "2025-04-15T20:00:00",
                  "calendarEventId": "gcal-event-abc",
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
                .matches("^INSERT INTO races \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("matchday_id")
                .contains("playoff_matchup_id")
                .contains("home_team_id")
                .contains("away_team_id")
                .contains("date_time")
                .contains("calendar_event_id");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(3, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        verify(ps).setObject(4, UUID.fromString("44444444-4444-4444-4444-444444444444"));
        verify(ps).setObject(5, UUID.fromString("55555555-5555-5555-5555-555555555555"));
        verify(ps).setObject(6, UUID.fromString("66666666-6666-6666-6666-666666666666"));
        verify(ps).setObject(7, UUID.fromString("77777777-7777-7777-7777-777777777777"));
        verify(ps).setObject(8, UUID.fromString("88888888-8888-8888-8888-888888888888"));
        verify(ps).setTimestamp(9, Timestamp.valueOf("2025-04-15 20:00:00"));
        verify(ps).setString(10, "gcal-event-abc");
        verify(ps).setTimestamp(11, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(12, Timestamp.valueOf("2025-01-16 11:30:00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenMinimalRow_whenRestoreCalled_thenNullableFksBoundAsNull() throws Exception {
        // given — only the NOT NULL matchday_id is populated
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "matchday": "22222222-2222-2222-2222-222222222222",
                  "match": null,
                  "playoffMatchup": null,
                  "track": null,
                  "car": null,
                  "homeTeamOverride": null,
                  "awayTeamOverride": null,
                  "dateTime": null,
                  "calendarEventId": null,
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
        verify(ps).setNull(3, java.sql.Types.OTHER);  // match
        verify(ps).setNull(4, java.sql.Types.OTHER);  // playoff_matchup
        verify(ps).setNull(5, java.sql.Types.OTHER);  // track
        verify(ps).setNull(6, java.sql.Types.OTHER);  // car
        verify(ps).setNull(7, java.sql.Types.OTHER);  // home_team
        verify(ps).setNull(8, java.sql.Types.OTHER);  // away_team
        verify(ps).setNull(9, java.sql.Types.TIMESTAMP); // date_time
        verify(ps).setNull(10, java.sql.Types.VARCHAR);  // calendar_event_id
    }
}
