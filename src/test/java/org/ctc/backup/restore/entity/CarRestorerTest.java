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
import static org.mockito.Mockito.*;

class CarRestorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CarRestorer restorer = new CarRestorer();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    static java.util.stream.Stream<String> invalidMetadataRows() {
        return java.util.stream.Stream.of(
                "{\"modelYear\":\"2020\"}", "{\"proposedModelYear\":2000.5}",
                "{\"category\":\"BOGUS\"}", "{\"drivetrain\":\"AWD\"}",
                "{\"modelYear\":2020,\"modelYearSource\":\"\",\"modelYearEvidence\":\"\"}",
                "{\"modelYearOverride\":\"true\"}", "{\"categorySource\":true}",
                "{\"modelYearEvidence\":\"" + "x".repeat(1001) + "\"}");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidMetadataRows")
    void givenInvalidMetadata_whenRestoring_thenRejectsEntireBatchBeforeAnyWrite(String json) throws Exception {
        // given
        var valid = mapper.readTree("{\"id\":\"11111111-1111-1111-1111-111111111111\"}");
        var invalid = mapper.readTree(json);
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restorer.restore(List.of(valid, invalid), jdbc))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void givenInvalidAcceptedYear_whenRestoring_thenRejectsBeforeAnyWrite() throws Exception {
        // given
        var row = mapper.readTree("{\"modelYear\":99}");
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restorer.restore(List.of(row), jdbc))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("year");
        verifyNoInteractions(jdbc);
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
                    "category": "GR_3", "drivetrain": "FOUR_WD", "modelYear": 2020, "proposedModelYear": 2025,
                    "categorySource": "MANUAL", "drivetrainSource": "MANUAL", "modelYearSource": "MANUAL",
                    "modelYearEvidence": "Administrative model year 2020", "proposedModelYearEvidence": "GTPlus code 3531 year 2025",
                    "categoryOverride": true, "drivetrainOverride": true, "modelYearOverride": true,
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
                .contains("id, manufacturer, name, gt7_id, image_url, created_at, updated_at,")
                .contains("proposed_model_year_evidence, category_override, drivetrain_override, model_year_override")
                .contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

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
        verify(ps).setString(8, "GR_3");
        verify(ps).setString(9, "FOUR_WD");
        verify(ps).setInt(10, 2020);
        verify(ps).setInt(11, 2025);
        verify(ps).setString(14, "MANUAL");
        verify(ps).setString(15, "Administrative model year 2020");
        verify(ps).setString(16, "GTPlus code 3531 year 2025");
        verify(ps).setBoolean(17, true);
        verify(ps).setBoolean(18, true);
        verify(ps).setBoolean(19, true);
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
        verify(ps).setNull(10, java.sql.Types.INTEGER);
        verify(ps).setNull(11, java.sql.Types.INTEGER);
        verify(ps).setBoolean(17, false);
        verify(ps).setBoolean(18, false);
        verify(ps).setBoolean(19, false);
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
        verify(ps).setNull(10, java.sql.Types.INTEGER);
        verify(ps).setNull(11, java.sql.Types.INTEGER);
        verify(ps).setBoolean(17, false);
        verify(ps).setBoolean(18, false);
        verify(ps).setBoolean(19, false);
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
