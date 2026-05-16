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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link SeasonPhaseRestorer}. See
 * {@link SeasonRestorerTest} for the test-pattern rationale (well-formedness regex +
 * type-coercion verification).
 */
@ExtendWith(MockitoExtension.class)
class SeasonPhaseRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private SeasonPhaseRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new SeasonPhaseRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsSeasonPhases() {
        assertThat(restorer.tableName()).isEqualTo("season_phases");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(SeasonPhaseRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(SeasonPhaseRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleSeasonPhaseRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given — FK fields render as bare UUID strings (TeamMixIn-family @JsonIdentityReference)
        String json = """
                {
                  "id": "aaaaaaaa-1111-1111-1111-111111111111",
                  "season": "bbbbbbbb-1111-1111-1111-111111111111",
                  "sortIndex": 0,
                  "phaseType": "REGULAR",
                  "layout": "GROUPS",
                  "format": "ROUND_ROBIN",
                  "label": "Regular Season",
                  "startDate": "2024-01-15",
                  "endDate": "2024-06-15",
                  "totalRounds": 22,
                  "legs": 2,
                  "eventDurationMinutes": 90,
                  "raceScoring": "cccccccc-1111-1111-1111-111111111111",
                  "matchScoring": "dddddddd-1111-1111-1111-111111111111",
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-06-01T12:30:45"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then — SQL + setter capture
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(List.of(row)), eq(500),
                setterCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).matches("^INSERT INTO season_phases \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "season_id", "sort_index", "phase_type", "layout",
                "format", "label", "start_date", "end_date", "total_rounds", "legs",
                "event_duration_minutes", "race_scoring_id", "match_scoring_id",
                "created_at", "updated_at");

        // then — drive setter
        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"));
        verify(preparedStatement).setObject(2, UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111"));
        verify(preparedStatement).setInt(3, 0);
        verify(preparedStatement).setString(4, "REGULAR");
        verify(preparedStatement).setString(5, "GROUPS");
        verify(preparedStatement).setString(6, "ROUND_ROBIN");
        verify(preparedStatement).setString(7, "Regular Season");
        verify(preparedStatement).setDate(8, Date.valueOf(LocalDate.parse("2024-01-15")));
        verify(preparedStatement).setDate(9, Date.valueOf(LocalDate.parse("2024-06-15")));
        verify(preparedStatement).setObject(10, 22, java.sql.Types.INTEGER);
        verify(preparedStatement).setInt(11, 2);
        verify(preparedStatement).setObject(12, 90, java.sql.Types.INTEGER);
        verify(preparedStatement).setObject(13, UUID.fromString("cccccccc-1111-1111-1111-111111111111"));
        verify(preparedStatement).setObject(14, UUID.fromString("dddddddd-1111-1111-1111-111111111111"));
        verify(preparedStatement).setTimestamp(15,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(16,
                Timestamp.valueOf(LocalDateTime.parse("2024-06-01T12:30:45")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSeasonPhaseRowWithNullableFieldsNull_whenRestoreCalled_thenBindsNull() throws Exception {
        // given — label, startDate, endDate, totalRounds, eventDurationMinutes are nullable
        String json = """
                {
                  "id": "aaaaaaaa-2222-2222-2222-222222222222",
                  "season": "bbbbbbbb-2222-2222-2222-222222222222",
                  "sortIndex": 1,
                  "phaseType": "PLAYOFF",
                  "layout": "BRACKET",
                  "format": "LEAGUE",
                  "label": null,
                  "startDate": null,
                  "endDate": null,
                  "totalRounds": null,
                  "legs": 1,
                  "eventDurationMinutes": null,
                  "raceScoring": "cccccccc-2222-2222-2222-222222222222",
                  "matchScoring": "dddddddd-2222-2222-2222-222222222222",
                  "createdAt": "2024-02-01T00:00:00",
                  "updatedAt": "2024-02-01T00:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        restorer.restore(List.of(row), jdbcTemplate);

        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(org.mockito.ArgumentMatchers.any(String.class),
                eq(List.of(row)), eq(500), setterCaptor.capture());
        setterCaptor.getValue().setValues(preparedStatement, row);

        // null label, dates, ints
        verify(preparedStatement).setString(7, null);
        verify(preparedStatement).setDate(8, null);
        verify(preparedStatement).setDate(9, null);
        verify(preparedStatement).setObject(10, null, java.sql.Types.INTEGER);
        verify(preparedStatement).setInt(11, 1);
        verify(preparedStatement).setObject(12, null, java.sql.Types.INTEGER);
    }
}
