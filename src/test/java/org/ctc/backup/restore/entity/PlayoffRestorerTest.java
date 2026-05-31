package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 05 + Plan 06 fix — Unit test for {@link PlayoffRestorer}.
 *
 * <p>Verifies the {@link org.ctc.backup.restore.EntityRestorer} contract for the {@code playoffs}
 * entity. Effective V6 schema columns (after {@code V6__CleanupLegacySeasonColumns.java} dropped
 * the legacy {@code season_id} column): id, phase_id (V3 FK to season_phases — NOT NULL after V4),
 * name, start_date, end_date, event_duration_minutes, created_at, updated_at.
 *
 * <p>Plan 06 Rule 3 fix: the legacy {@code season_id} column is NO LONGER part of the INSERT
 * since V6 dropped it. The original Plan 05 PlayoffRestorer included it and would fail on H2
 * (no such column) and MariaDB (column does not exist). The test is updated accordingly.
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
                .contains("phase_id")
                .contains("name")
                .contains("start_date")
                .contains("end_date")
                .contains("event_duration_minutes")
                .contains("created_at")
                .contains("updated_at")
                .doesNotContain("season_id");  // V6 dropped this column

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        // id (1), phase_id (2), name (3), start_date (4), end_date (5),
        // event_duration_minutes (6), created_at (7), updated_at (8)
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(ps).setString(3, "Saison 2023 Playoffs");
        verify(ps).setDate(4, Date.valueOf(LocalDate.parse("2024-05-01")));
        verify(ps).setDate(5, Date.valueOf(LocalDate.parse("2024-05-31")));
        verify(ps).setInt(6, 90);
        verify(ps).setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:30:00")));
        verify(ps).setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse("2024-02-20T11:45:00")));
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

        // After V6 drop of season_id: positions shift by one — start_date=4, end_date=5,
        // event_duration_minutes=6
        verify(ps).setNull(4, java.sql.Types.DATE);
        verify(ps).setNull(5, java.sql.Types.DATE);
        verify(ps).setNull(6, java.sql.Types.INTEGER);
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
