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
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 05 — Unit test for {@link TrackRestorer}.
 *
 * <p>Verifies the {@link org.ctc.backup.restore.EntityRestorer} contract for the {@code tracks}
 * leaf entity (V1 schema: id, name, country, image_url, created_at, updated_at).
 */
class TrackRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TrackRestorer restorer = new TrackRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void whenTableNameCalled_thenReturnsTracks() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("tracks");
    }

    @Test
    void givenTrackWithAllFields_whenRestoreCalled_thenBatchUpdateInsertsCorrectColumns() throws Exception {
        // given
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "name": "Nürburgring",
                    "country": "Germany",
                    "imageUrl": "http://example.com/nurburgring.png",
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
                sqlCaptor.capture(),
                anyList(),
                anyInt(),
                setterCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO tracks")
                .contains("(id, name, country, image_url, created_at, updated_at)")
                .contains("VALUES (?, ?, ?, ?, ?, ?)");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setString(2, "Nürburgring");
        verify(ps).setString(3, "Germany");
        verify(ps).setString(4, "http://example.com/nurburgring.png");
        verify(ps).setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:30:00")));
        verify(ps).setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse("2024-02-20T11:45:00")));
    }

    @Test
    void givenTrackWithMissingCountryAndImageUrl_whenRestoreCalled_thenNullableFieldsSetToNull() throws Exception {
        // given
        String json = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "name": "Some Local Track",
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

        verify(ps).setNull(3, java.sql.Types.VARCHAR);
        verify(ps).setNull(4, java.sql.Types.VARCHAR);
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
