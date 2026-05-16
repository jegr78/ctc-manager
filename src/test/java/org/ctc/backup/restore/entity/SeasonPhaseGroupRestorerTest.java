package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
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
 * Phase 75 / Plan 03 — Surefire unit test for {@link SeasonPhaseGroupRestorer}.
 */
@ExtendWith(MockitoExtension.class)
class SeasonPhaseGroupRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private SeasonPhaseGroupRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new SeasonPhaseGroupRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsSeasonPhaseGroups() {
        assertThat(restorer.tableName()).isEqualTo("season_phase_groups");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(SeasonPhaseGroupRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(SeasonPhaseGroupRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleSeasonPhaseGroupRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "11111111-3333-3333-3333-111111111111",
                  "phase": "aaaaaaaa-1111-1111-1111-111111111111",
                  "name": "Group A",
                  "sortIndex": 0,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(List.of(row)), eq(500),
                setterCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).matches("^INSERT INTO season_phase_groups \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "phase_id", "name", "sort_index", "created_at", "updated_at");

        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("11111111-3333-3333-3333-111111111111"));
        verify(preparedStatement).setObject(2, UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"));
        verify(preparedStatement).setString(3, "Group A");
        verify(preparedStatement).setInt(4, 0);
        verify(preparedStatement).setTimestamp(5,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(6,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
    }
}
