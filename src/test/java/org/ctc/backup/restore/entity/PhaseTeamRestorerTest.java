package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link PhaseTeamRestorer}. Covers the
 * nullable {@code group} FK (PhaseTeam.group is a {@code @ManyToOne} without {@code nullable=false}
 * — phase teams without a sub-group bracket assignment land here).
 */
@ExtendWith(MockitoExtension.class)
class PhaseTeamRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private PhaseTeamRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new PhaseTeamRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsPhaseTeams() {
        assertThat(restorer.tableName()).isEqualTo("phase_teams");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(PhaseTeamRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(PhaseTeamRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSinglePhaseTeamRowWithGroup_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "11111111-4444-4444-4444-111111111111",
                  "phase": "aaaaaaaa-1111-1111-1111-111111111111",
                  "team": "bbbbbbbb-2222-2222-2222-222222222222",
                  "group": "cccccccc-3333-3333-3333-333333333333",
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
        assertThat(sql).matches("^INSERT INTO phase_teams \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "phase_id", "team_id", "group_id",
                "created_at", "updated_at");

        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("11111111-4444-4444-4444-111111111111"));
        verify(preparedStatement).setObject(2, UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"));
        verify(preparedStatement).setObject(3, UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222"));
        verify(preparedStatement).setObject(4, UUID.fromString("cccccccc-3333-3333-3333-333333333333"));
        verify(preparedStatement).setTimestamp(5,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(6,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenPhaseTeamRowWithNullGroup_whenRestoreCalled_thenGroupBoundAsNullUuid() throws Exception {
        // given — group is the only nullable FK on phase_teams
        String json = """
                {
                  "id": "11111111-4444-4444-4444-111111111111",
                  "phase": "aaaaaaaa-1111-1111-1111-111111111111",
                  "team": "bbbbbbbb-2222-2222-2222-222222222222",
                  "group": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        restorer.restore(List.of(row), jdbcTemplate);

        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), eq(List.of(row)), eq(500),
                setterCaptor.capture());
        setterCaptor.getValue().setValues(preparedStatement, row);

        // group bound to null with VARCHAR/UUID-compatible binding via setObject(idx, null)
        verify(preparedStatement).setObject(4, null);
    }
}
