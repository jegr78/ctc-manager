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
 * Phase 75 / Plan 03 — Surefire unit test for {@link TeamRestorer}.
 *
 * <p>The {@code Team.parentTeam} self-FK forces a 2-pass restore (CONTEXT D-06):
 * <ol>
 *   <li>Pass 1: INSERT every team with {@code parent_team_id = NULL}.</li>
 *   <li>Pass 2: UPDATE {@code parent_team_id} for the subset of rows whose source JSON
 *       carried a non-null {@code parentTeam} UUID — issued after the entire
 *       {@code teams.json} array was pass-1-inserted so the FK target row is guaranteed
 *       present.</li>
 * </ol>
 *
 * <p>Pass 2 is SKIPPED when no row has a parent — saves a no-op batch round-trip.
 */
@ExtendWith(MockitoExtension.class)
class TeamRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private TeamRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new TeamRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsTeams() {
        assertThat(restorer.tableName()).isEqualTo("teams");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(TeamRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(TeamRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenTeamsWithAndWithoutParents_whenRestoreCalled_thenTwoBatchUpdatesIssuedInOrder() throws Exception {
        // given — 3 teams: 2 root-level (parentTeam null) + 1 sub-team (parentTeam set)
        JsonNode rootA = MAPPER.readTree("""
                {
                  "id": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "name": "Alpha Racing",
                  "shortName": "T-ALF",
                  "logoUrl": "/img/alpha.png",
                  "primaryColor": "#ff0000",
                  "secondaryColor": "#00ff00",
                  "accentColor": "#0000ff",
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-02T00:00:00"
                }""");
        JsonNode rootB = MAPPER.readTree("""
                {
                  "id": "22222222-aaaa-aaaa-aaaa-222222222222",
                  "name": "Beta Racing",
                  "shortName": "T-BET",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode sub = MAPPER.readTree("""
                {
                  "id": "33333333-aaaa-aaaa-aaaa-333333333333",
                  "name": "Alpha Racing II",
                  "shortName": "T-ALF2",
                  "logoUrl": "/img/alpha2.png",
                  "primaryColor": "#aa0000",
                  "secondaryColor": "#00aa00",
                  "accentColor": "#0000aa",
                  "parentTeam": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "createdAt": "2024-02-01T10:00:00",
                  "updatedAt": "2024-02-15T15:30:00"
                }""");
        List<JsonNode> rows = List.of(rootA, rootB, sub);

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
                org.mockito.ArgumentMatchers.startsWith("INSERT INTO teams"),
                anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
        ordering.verify(jdbcTemplate).batchUpdate(
                eq("UPDATE teams SET parent_team_id = ? WHERE id = ?"),
                anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));

        // Pass 1 SQL: INSERT with NULL self-FK hard-coded
        String pass1Sql = sqlCaptor.getAllValues().get(0);
        assertThat(pass1Sql).startsWith("INSERT INTO teams");
        assertThat(pass1Sql).contains("parent_team_id");
        assertThat(pass1Sql).contains("NULL");

        // Pass 1 rows: all 3 input rows
        assertThat(rowsCaptor.getAllValues().get(0)).hasSize(3);

        // Pass 2 SQL: UPDATE parent_team_id only
        String pass2Sql = sqlCaptor.getAllValues().get(1);
        assertThat(pass2Sql).isEqualTo("UPDATE teams SET parent_team_id = ? WHERE id = ?");

        // Pass 2 rows: only the 1 sub-team
        List<JsonNode> pass2Rows = rowsCaptor.getAllValues().get(1);
        assertThat(pass2Rows).hasSize(1);
        assertThat(pass2Rows.get(0).get("id").asText())
                .isEqualTo("33333333-aaaa-aaaa-aaaa-333333333333");

        // Drive Pass-1 setter against one root row — verify NULL is hard-coded in SQL,
        // so parent_team_id is NOT a setter parameter (10 binds: id, name, short_name,
        // logo_url, primary_color, secondary_color, accent_color, discord_role_id,
        // created_at, updated_at). Use a separate PreparedStatement mock for each pass
        // to keep the Mockito invocation tallies independent.
        PreparedStatement psPass1 = org.mockito.Mockito.mock(PreparedStatement.class);
        setterCaptor.getAllValues().get(0).setValues(psPass1, rootA);
        verify(psPass1).setObject(1, UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111"));
        verify(psPass1).setString(2, "Alpha Racing");
        verify(psPass1).setString(3, "T-ALF");
        verify(psPass1).setString(4, "/img/alpha.png");
        verify(psPass1).setString(5, "#ff0000");
        verify(psPass1).setString(6, "#00ff00");
        verify(psPass1).setString(7, "#0000ff");
        verify(psPass1).setString(8, null);
        verify(psPass1).setTimestamp(9,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(psPass1).setTimestamp(10,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-02T00:00:00")));

        // Drive Pass-2 setter — 2 binds: parent_team_id, id (separate mock).
        PreparedStatement psPass2 = org.mockito.Mockito.mock(PreparedStatement.class);
        setterCaptor.getAllValues().get(1).setValues(psPass2, sub);
        verify(psPass2).setObject(1, UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111"));
        verify(psPass2).setObject(2, UUID.fromString("33333333-aaaa-aaaa-aaaa-333333333333"));
    }

    @Test
    void givenAllTeamsAreRootLevel_whenRestoreCalled_thenOnlyPass1Executes() throws Exception {
        // given — 2 teams, both root-level (parentTeam null)
        JsonNode rootA = MAPPER.readTree("""
                {
                  "id": "11111111-aaaa-aaaa-aaaa-111111111111",
                  "name": "Alpha Racing",
                  "shortName": "T-ALF",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");
        JsonNode rootB = MAPPER.readTree("""
                {
                  "id": "22222222-aaaa-aaaa-aaaa-222222222222",
                  "name": "Beta Racing",
                  "shortName": "T-BET",
                  "logoUrl": null,
                  "primaryColor": null,
                  "secondaryColor": null,
                  "accentColor": null,
                  "parentTeam": null,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }""");

        // when
        restorer.restore(List.of(rootA, rootB), jdbcTemplate);

        // then — Pass 1 executes once
        verify(jdbcTemplate).batchUpdate(
                org.mockito.ArgumentMatchers.startsWith("INSERT INTO teams"),
                anyList(),
                anyInt(),
                any(ParameterizedPreparedStatementSetter.class));

        // Pass 2 SQL (UPDATE) is NEVER issued because the filtered withParent list is empty
        verify(jdbcTemplate, never()).batchUpdate(
                eq("UPDATE teams SET parent_team_id = ? WHERE id = ?"),
                anyList(),
                anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
    }
}
