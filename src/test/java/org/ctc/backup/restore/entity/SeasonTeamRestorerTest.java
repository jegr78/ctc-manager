package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link SeasonTeamRestorer}.
 *
 * <p>PLAN-Q1 resolution: {@code season_teams.successor_season_team_id} is a self-FK
 * (V1__initial_schema.sql:90 + the {@code @JoinColumn} on {@code SeasonTeam.successor}). It is
 * structurally identical to {@code teams.parent_team_id} (D-06) and therefore receives the
 * same 2-pass treatment: Pass-1 INSERT with {@code successor_season_team_id = NULL},
 * Pass-2 UPDATE for the subset of rows whose {@code successor} JSON field is non-null.
 *
 * <p>JSON property naming (from {@code SeasonTeamMixIn} camelCase + Jackson default): the
 * self-FK is emitted under the property name {@code successor} (NOT
 * {@code successorSeasonTeam}, despite the plan's interfaces block — corrected here).
 */
@ExtendWith(MockitoExtension.class)
class SeasonTeamRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private SeasonTeamRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new SeasonTeamRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsSeasonTeams() {
        assertThat(restorer.tableName()).isEqualTo("season_teams");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(SeasonTeamRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(SeasonTeamRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSeasonTeamsWithAndWithoutSuccessor_whenRestoreCalled_thenTwoBatchUpdatesIssuedInOrder() throws Exception {
        // given — 3 SeasonTeams: 2 without successor + 1 with successor (replaced mid-season)
        JsonNode originalA = MAPPER.readTree("""
                {
                  "id": "11111111-bbbb-bbbb-bbbb-111111111111",
                  "season": "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa",
                  "team": "cccccccc-bbbb-bbbb-bbbb-cccccccccccc",
                  "rating": 90,
                  "primaryColor": "#001100",
                  "secondaryColor": "#002200",
                  "accentColor": "#003300",
                  "logoUrl": "/img/aa.png",
                  "successor": null,
                  "replacedAt": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode replaced = MAPPER.readTree("""
                {
                  "id": "22222222-bbbb-bbbb-bbbb-222222222222",
                  "season": "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa",
                  "team": "dddddddd-bbbb-bbbb-bbbb-dddddddddddd",
                  "rating": 80,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "logoUrl": null,
                  "successor": "33333333-bbbb-bbbb-bbbb-333333333333",
                  "replacedAt": "2024-04-01",
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-04-01T00:00:00"
                }""");
        JsonNode successor = MAPPER.readTree("""
                {
                  "id": "33333333-bbbb-bbbb-bbbb-333333333333",
                  "season": "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa",
                  "team": "eeeeeeee-bbbb-bbbb-bbbb-eeeeeeeeeeee",
                  "rating": 75,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "logoUrl": null,
                  "successor": null,
                  "replacedAt": null,
                  "createdAt": "2024-04-01T00:00:00",
                  "updatedAt": "2024-04-01T00:00:00"
                }""");
        List<JsonNode> rows = List.of(originalA, replaced, successor);

        // when
        restorer.restore(rows, jdbcTemplate);

        // then — exactly 2 batchUpdate calls, captured via Mockito captors. InOrder
        // separately verifies Pass-1 (INSERT) precedes Pass-2 (UPDATE).
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<JsonNode>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .batchUpdate(sqlCaptor.capture(), rowsCaptor.capture(),
                        eq(500), setterCaptor.capture());

        InOrder ordering = inOrder(jdbcTemplate);
        ordering.verify(jdbcTemplate).batchUpdate(
                org.mockito.ArgumentMatchers.startsWith("INSERT INTO season_teams"),
                anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
        ordering.verify(jdbcTemplate).batchUpdate(
                eq("UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?"),
                anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));

        // Pass 1 SQL — INSERT with NULL self-FK
        String pass1Sql = sqlCaptor.getAllValues().get(0);
        assertThat(pass1Sql).startsWith("INSERT INTO season_teams");
        assertThat(pass1Sql).contains("successor_season_team_id");
        assertThat(pass1Sql).contains("NULL");

        // Pass 1 rows: all 3
        assertThat(rowsCaptor.getAllValues().get(0)).hasSize(3);

        // Pass 2 SQL — UPDATE for successor
        String pass2Sql = sqlCaptor.getAllValues().get(1);
        assertThat(pass2Sql).isEqualTo("UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?");

        // Pass 2 rows: only the "replaced" row
        List<JsonNode> pass2Rows = rowsCaptor.getAllValues().get(1);
        assertThat(pass2Rows).hasSize(1);
        assertThat(pass2Rows.get(0).get("id").asText())
                .isEqualTo("22222222-bbbb-bbbb-bbbb-222222222222");

        // Drive Pass-1 setter against "replaced" row — verify column bindings (11 binds:
        // id, season_id, team_id, rating, primary_color, secondary_color, accent_color,
        // logo_url, replaced_at, created_at, updated_at). successor_season_team_id is
        // hard-coded NULL in SQL, NOT a setter parameter. Separate mocks per pass keep
        // the invocation tallies clean.
        PreparedStatement psPass1 = org.mockito.Mockito.mock(PreparedStatement.class);
        setterCaptor.getAllValues().get(0).setValues(psPass1, replaced);
        verify(psPass1).setObject(1, UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222"));
        verify(psPass1).setObject(2, UUID.fromString("aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa"));
        verify(psPass1).setObject(3, UUID.fromString("dddddddd-bbbb-bbbb-bbbb-dddddddddddd"));
        verify(psPass1).setObject(4, 80, Types.INTEGER);
        verify(psPass1).setString(5, null);
        verify(psPass1).setString(6, null);
        verify(psPass1).setString(7, null);
        verify(psPass1).setString(8, null);
        verify(psPass1).setDate(9, Date.valueOf(LocalDate.parse("2024-04-01")));
        verify(psPass1).setTimestamp(10,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(psPass1).setTimestamp(11,
                Timestamp.valueOf(LocalDateTime.parse("2024-04-01T00:00:00")));

        // Drive Pass-2 setter — 2 binds: successor UUID, self id (separate mock).
        PreparedStatement psPass2 = org.mockito.Mockito.mock(PreparedStatement.class);
        setterCaptor.getAllValues().get(1).setValues(psPass2, replaced);
        verify(psPass2).setObject(1, UUID.fromString("33333333-bbbb-bbbb-bbbb-333333333333"));
        verify(psPass2).setObject(2, UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222"));
    }

    @Test
    void givenNoSuccessors_whenRestoreCalled_thenOnlyPass1Executes() throws Exception {
        // given — 2 SeasonTeams, both successor null
        JsonNode rowA = MAPPER.readTree("""
                {
                  "id": "11111111-bbbb-bbbb-bbbb-111111111111",
                  "season": "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa",
                  "team": "cccccccc-bbbb-bbbb-bbbb-cccccccccccc",
                  "rating": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "logoUrl": null,
                  "successor": null,
                  "replacedAt": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode rowB = MAPPER.readTree("""
                {
                  "id": "22222222-bbbb-bbbb-bbbb-222222222222",
                  "season": "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa",
                  "team": "dddddddd-bbbb-bbbb-bbbb-dddddddddddd",
                  "rating": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "logoUrl": null,
                  "successor": null,
                  "replacedAt": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");

        // when
        restorer.restore(List.of(rowA, rowB), jdbcTemplate);

        // then — Pass-1 executes once; Pass-2 UPDATE SQL is NEVER issued
        verify(jdbcTemplate).batchUpdate(
                org.mockito.ArgumentMatchers.startsWith("INSERT INTO season_teams"),
                anyList(),
                anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
        verify(jdbcTemplate, never()).batchUpdate(
                eq("UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?"),
                anyList(),
                anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
    }
}
