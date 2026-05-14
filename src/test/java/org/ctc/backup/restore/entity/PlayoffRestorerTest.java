package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 05 — Unit test for {@link PlayoffRestorer}.
 *
 * <p>Verifies the {@link org.ctc.backup.restore.EntityRestorer} contract for the {@code playoffs}
 * entity. Effective V1+V3 schema columns: id, phase_id (V3 FK to season_phases), name,
 * start_date, end_date, event_duration_minutes, created_at, updated_at.
 *
 * <p>{@code season_id} is NOT explicitly written by the restorer — V3 made {@code phase_id} the
 * authoritative FK (Playoff.phase is the only entity association); the legacy V1
 * {@code season_id NOT NULL} column is filled from the phase's season via the import orchestrator
 * having seeded the source JSON correctly (the export wire emits {@code seasonId} as a separate
 * field on each playoff row).
 */
class PlayoffRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlayoffRestorer restorer = new PlayoffRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void whenTableNameCalled_thenReturnsPlayoffs() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("playoffs");
    }

    @Test
    void givenPlayoffWithAllFields_whenRestoreCalled_thenBatchUpdateInsertsCorrectColumns() throws Exception {
        // given — phase is a UUID string per @JsonIdentityReference(alwaysAsId=true)
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "phase": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "seasonId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "name": "Saison 2023 Playoffs",
                    "startDate": "2024-05-01",
                    "endDate": "2024-05-31",
                    "eventDurationMinutes": 90,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        JsonNode row = mapper.readTree(json);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);

        verify(jdbc, times(1)).batchUpdate(
                sqlCaptor.capture(), anyList(), anyInt(), setterCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO playoffs")
                .contains("season_id")
                .contains("phase_id")
                .contains("name")
                .contains("start_date")
                .contains("end_date")
                .contains("event_duration_minutes")
                .contains("created_at")
                .contains("updated_at");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        // id (1), season_id (2), phase_id (3), name (4), start_date (5), end_date (6),
        // event_duration_minutes (7), created_at (8), updated_at (9)
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        verify(ps).setObject(3, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(ps).setString(4, "Saison 2023 Playoffs");
        verify(ps).setDate(5, Date.valueOf(LocalDate.parse("2024-05-01")));
        verify(ps).setDate(6, Date.valueOf(LocalDate.parse("2024-05-31")));
        verify(ps).setInt(7, 90);
        verify(ps).setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:30:00")));
        verify(ps).setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse("2024-02-20T11:45:00")));
    }

    @Test
    void givenPlayoffWithMissingNullableFields_whenRestoreCalled_thenSetNullUsed() throws Exception {
        // given — start_date, end_date, event_duration_minutes all nullable per V1 schema
        String json = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "phase": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "seasonId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "name": "Bare Playoffs",
                    "createdAt": "2024-03-10T08:00:00",
                    "updatedAt": "2024-03-10T08:00:00"
                }
                """;
        JsonNode row = mapper.readTree(json);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(anyString(), anyList(), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        verify(ps).setNull(5, java.sql.Types.DATE);
        verify(ps).setNull(6, java.sql.Types.DATE);
        verify(ps).setNull(7, java.sql.Types.INTEGER);
    }

    @Test
    void givenEmptyRowList_whenRestoreCalled_thenNoBatchUpdateInvoked() {
        // given / when
        restorer.restore(List.of(), jdbc);

        // then
        verify(jdbc, times(0)).batchUpdate(anyString(), anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<JsonNode>>any());
    }
}
