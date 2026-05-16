package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
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
 * Phase 75 / Plan 05 — Unit test for {@link PlayoffSeedRestorer}.
 *
 * <p>V1 schema columns: id, playoff_id, team_id, seed, created_at, updated_at.
 */
class PlayoffSeedRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlayoffSeedRestorer restorer = new PlayoffSeedRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void whenTableNameCalled_thenReturnsPlayoffSeeds() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("playoff_seeds");
    }

    @Test
    void givenPlayoffSeedWithAllFields_whenRestoreCalled_thenBatchUpdateInsertsCorrectColumns() throws Exception {
        // given
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "playoff": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "team": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "seed": 4,
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
                .contains("INSERT INTO playoff_seeds")
                .contains("(id, playoff_id, team_id, seed, created_at, updated_at)")
                .contains("VALUES (?, ?, ?, ?, ?, ?)");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(ps).setObject(3, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        verify(ps).setInt(4, 4);
        verify(ps).setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:30:00")));
        verify(ps).setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse("2024-02-20T11:45:00")));
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
