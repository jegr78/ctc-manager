package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.ArrayList;
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
 * Phase 75 / Plan 05 — Unit test for {@link PlayoffMatchupRestorer} (2-pass restorer).
 *
 * <p>Verifies PATTERNS Q2 resolution: {@code next_matchup_id} self-FK is structurally identical
 * to {@code Team.parent_team_id} and gets the same 2-pass NULL-then-UPDATE treatment locked by
 * D-06.
 *
 * <p>The nullable {@code team1_id}, {@code team2_id}, {@code winner_id} team FKs use a local
 * {@code setNullableUuid} helper.
 */
class PlayoffMatchupRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlayoffMatchupRestorer restorer = new PlayoffMatchupRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void whenTableNameCalled_thenReturnsPlayoffMatchups() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("playoff_matchups");
    }

    @Test
    void givenMatchupsWithAndWithoutNextRefs_whenRestoreCalled_thenTwoBatchUpdatesIssuedInOrder()
            throws Exception {
        // given — one matchup with nextMatchup, one leaf without
        String jsonA = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 0,
                    "team1": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "team2": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "winner": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "nextMatchup": "22222222-2222-2222-2222-222222222222",
                    "homeScore": 3,
                    "awayScore": 1,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        String jsonB = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 1,
                    "homeScore": null,
                    "awayScore": null,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        List<JsonNode> rows = new ArrayList<>();
        rows.add(mapper.readTree(jsonA));
        rows.add(mapper.readTree(jsonB));

        // when
        restorer.restore(rows, jdbc);

        // then — capture both batchUpdate calls; verify order via InOrder + the captured SQL
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).batchUpdate(sqlCaptor.capture(), anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<JsonNode>>any());

        List<String> capturedSqls = sqlCaptor.getAllValues();
        assertThat(capturedSqls).hasSize(2);
        assertThat(capturedSqls.get(0))
                .as("Pass 1 (first invocation) must be INSERT with next_matchup_id hard-coded NULL")
                .contains("INSERT INTO playoff_matchups")
                .contains("next_matchup_id")
                .containsPattern("VALUES\\s*\\([^)]*NULL[^)]*\\)");
        assertThat(capturedSqls.get(1))
                .as("Pass 2 (second invocation) must be UPDATE setting next_matchup_id")
                .isEqualTo("UPDATE playoff_matchups SET next_matchup_id = ? WHERE id = ?");
    }

    @Test
    void givenAllMatchupsAreLeavesNoNextRef_whenRestoreCalled_thenOnlyPass1Executes() throws Exception {
        // given — both matchups are leaves (no nextMatchup field)
        String jsonA = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 0,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        String jsonB = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 1,
                    "nextMatchup": null,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        List<JsonNode> rows = List.of(mapper.readTree(jsonA), mapper.readTree(jsonB));

        // when
        restorer.restore(rows, jdbc);

        // then — exactly 1 batchUpdate invocation; pass-2 skipped (no rows to update)
        verify(jdbc, times(1)).batchUpdate(anyString(), anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<JsonNode>>any());
    }

    @Test
    void givenMatchupWithNullableTeamFKs_whenRestoreCalled_thenSetNullableUuidUsedForTeam1Team2Winner()
            throws Exception {
        // given — all three team FKs absent (TBD / not-yet-decided matchup)
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 0,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        JsonNode row = mapper.readTree(json);

        // when
        restorer.restore(List.of(row), jdbc);

        // then — capture the pass-1 setter, replay it on a mock PreparedStatement, verify setNull
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(anyString(), anyList(), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        // Pass 1 column order: id (1), round_id (2), team1_id (3), team2_id (4),
        // winner_id (5), bracket_position (6), home_score (7), away_score (8),
        // created_at (9), updated_at (10) — next_matchup_id is hard-coded NULL in SQL
        verify(ps).setNull(3, java.sql.Types.OTHER);
        verify(ps).setNull(4, java.sql.Types.OTHER);
        verify(ps).setNull(5, java.sql.Types.OTHER);
    }

    @Test
    void givenMatchupWithAllFields_whenRestoreCalled_thenPass1BindsAllColumnsCorrectly()
            throws Exception {
        // given
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 0,
                    "team1": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "team2": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "winner": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "homeScore": 3,
                    "awayScore": 1,
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        JsonNode row = mapper.readTree(json);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(anyString(), anyList(), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(ps).setObject(eq(3), eq(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")));
        verify(ps).setObject(eq(4), eq(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")));
        verify(ps).setObject(eq(5), eq(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")));
        verify(ps).setInt(6, 0);
        verify(ps).setInt(7, 3);
        verify(ps).setInt(8, 1);
    }

    @Test
    void givenPass2UpdateSetter_whenInvoked_thenBindsNextMatchupAndIdInCorrectOrder()
            throws Exception {
        // given — one matchup with nextMatchup
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "round": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "bracketPosition": 0,
                    "nextMatchup": "22222222-2222-2222-2222-222222222222",
                    "createdAt": "2024-01-15T10:30:00",
                    "updatedAt": "2024-02-20T11:45:00"
                }
                """;
        JsonNode row = mapper.readTree(json);

        // when
        restorer.restore(List.of(row), jdbc);

        // then — capture both setters; the second one is the Pass-2 UPDATE setter
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(2)).batchUpdate(anyString(), anyList(), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getAllValues().get(1).setValues(ps, row);

        // Pass-2 binds: ?1 = nextMatchup, ?2 = id
        verify(ps).setObject(1, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setObject(2, UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void givenEmptyRowList_whenRestoreCalled_thenNoBatchUpdateInvoked() {
        // given / when
        restorer.restore(List.of(), jdbc);

        // then
        verify(jdbc, never()).batchUpdate(anyString(), anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<JsonNode>>any());
    }
}
