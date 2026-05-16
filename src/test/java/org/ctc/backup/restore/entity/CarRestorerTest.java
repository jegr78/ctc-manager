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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 05 — Unit test for {@link CarRestorer}.
 *
 * <p>Verifies the {@link org.ctc.backup.restore.EntityRestorer} contract for the {@code cars} leaf
 * entity:
 * <ul>
 *   <li>{@link CarRestorer#tableName()} returns {@code "cars"}.</li>
 *   <li>{@code restore} invokes exactly one {@code batchUpdate} with a hard-coded INSERT.</li>
 *   <li>The setter binds {@code id}, {@code manufacturer}, {@code name}, {@code gt7_id},
 *       {@code image_url}, {@code created_at}, {@code updated_at} (7 columns total per V1
 *       schema) in column order.</li>
 *   <li>Nullable {@code gt7Id} / {@code imageUrl} are handled via {@code setNull} when missing
 *       in the source JSON.</li>
 * </ul>
 */
class CarRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CarRestorer restorer = new CarRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void whenTableNameCalled_thenReturnsCars() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("cars");
    }

    @Test
    void givenCarWithAllFields_whenRestoreCalled_thenBatchUpdateInsertsCorrectColumns() throws Exception {
        // given
        String json = """
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "manufacturer": "Toyota",
                    "name": "GR Yaris",
                    "gt7Id": "GT7-123",
                    "imageUrl": "http://example.com/yaris.png",
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
        ArgumentCaptor<List<JsonNode>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);

        verify(jdbc, times(1)).batchUpdate(
                sqlCaptor.capture(),
                rowsCaptor.capture(),
                anyInt(),
                setterCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO cars")
                .contains("(id, manufacturer, name, gt7_id, image_url, created_at, updated_at)")
                .contains("VALUES (?, ?, ?, ?, ?, ?, ?)");

        // verify the setter binds correct values in column order
        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);

        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setString(2, "Toyota");
        verify(ps).setString(3, "GR Yaris");
        verify(ps).setString(4, "GT7-123");
        verify(ps).setString(5, "http://example.com/yaris.png");
        verify(ps).setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:30:00")));
        verify(ps).setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse("2024-02-20T11:45:00")));
    }

    @Test
    void givenCarWithMissingGt7IdAndImageUrl_whenRestoreCalled_thenNullableFieldsSetToNull() throws Exception {
        // given
        String json = """
                {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "manufacturer": "Mazda",
                    "name": "MX-5",
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

        // verify nullable fields use setNull (VARCHAR) when JSON value missing / null
        verify(ps).setNull(4, java.sql.Types.VARCHAR);
        verify(ps).setNull(5, java.sql.Types.VARCHAR);
    }

    @Test
    void givenCarWithExplicitNullGt7Id_whenRestoreCalled_thenSetNullUsed() throws Exception {
        // given — JSON literal null (not missing field)
        String json = """
                {
                    "id": "33333333-3333-3333-3333-333333333333",
                    "manufacturer": "Honda",
                    "name": "Civic",
                    "gt7Id": null,
                    "imageUrl": null,
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

        verify(ps).setNull(4, java.sql.Types.VARCHAR);
        verify(ps).setNull(5, java.sql.Types.VARCHAR);
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
