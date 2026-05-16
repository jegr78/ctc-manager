package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 04 — Unit test for {@link RaceSettingsRestorer}.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL UNIQUE, number_of_laps INT NULL,
 * tyre_wear_multiplier INT NULL, fuel_consumption_multiplier INT NULL, refueling_speed INT NULL,
 * initial_fuel VARCHAR NULL, number_of_required_pit_stops INT NULL,
 * time_progression_multiplier INT NULL, weather VARCHAR NULL, time_of_day VARCHAR NULL,
 * available_tyres VARCHAR NULL, mandatory_tyres VARCHAR NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}.
 *
 * <p>All non-FK columns are nullable per V1 schema and {@code RaceSettings.isComplete()}
 * semantics. The setter MUST bind {@code null} for missing nullable columns.
 */
class RaceSettingsRestorerTest {

    private final RaceSettingsRestorer restorer = new RaceSettingsRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaceSettings() {
        assertThat(restorer.tableName()).isEqualTo("race_settings");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenCompleteSettings_whenRestoreCalled_thenAllColumnsBoundWithCorrectTypes() throws Exception {
        // given — all fields populated (isComplete() == true semantics)
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "numberOfLaps": 24,
                  "tyreWearMultiplier": 3,
                  "fuelConsumptionMultiplier": 2,
                  "refuelingSpeed": 5,
                  "initialFuel": "100%",
                  "numberOfRequiredPitStops": 1,
                  "timeProgressionMultiplier": 30,
                  "weather": "Clear",
                  "timeOfDay": "Day",
                  "availableTyres": "Hard,Medium,Soft",
                  "mandatoryTyres": "Medium",
                  "createdAt": "2025-01-15T10:00:00",
                  "updatedAt": "2025-01-16T11:30:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(sqlCaptor.capture(), any(List.class),
                eq(500), setterCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .matches("^INSERT INTO race_settings \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("race_id")
                .contains("number_of_laps")
                .contains("tyre_wear_multiplier")
                .contains("fuel_consumption_multiplier")
                .contains("refueling_speed")
                .contains("initial_fuel")
                .contains("number_of_required_pit_stops")
                .contains("time_progression_multiplier")
                .contains("weather")
                .contains("time_of_day")
                .contains("available_tyres")
                .contains("mandatory_tyres");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setInt(3, 24);
        verify(ps).setInt(4, 3);
        verify(ps).setInt(5, 2);
        verify(ps).setInt(6, 5);
        verify(ps).setString(7, "100%");
        verify(ps).setInt(8, 1);
        verify(ps).setInt(9, 30);
        verify(ps).setString(10, "Clear");
        verify(ps).setString(11, "Day");
        verify(ps).setString(12, "Hard,Medium,Soft");
        verify(ps).setString(13, "Medium");
        verify(ps).setTimestamp(14, Timestamp.valueOf("2025-01-15 10:00:00"));
        verify(ps).setTimestamp(15, Timestamp.valueOf("2025-01-16 11:30:00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenIncompleteSettings_whenRestoreCalled_thenNullableColumnsBoundAsNull() throws Exception {
        // given — minimum race_id only; all other fields nullable per V1 schema
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "numberOfLaps": null,
                  "tyreWearMultiplier": null,
                  "fuelConsumptionMultiplier": null,
                  "refuelingSpeed": null,
                  "initialFuel": null,
                  "numberOfRequiredPitStops": null,
                  "timeProgressionMultiplier": null,
                  "weather": null,
                  "timeOfDay": null,
                  "availableTyres": null,
                  "mandatoryTyres": null,
                  "createdAt": "2025-01-15T10:00:00",
                  "updatedAt": "2025-01-16T11:30:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(any(String.class), any(List.class), anyInt(), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setNull(3, java.sql.Types.INTEGER);
        verify(ps).setNull(4, java.sql.Types.INTEGER);
        verify(ps).setNull(5, java.sql.Types.INTEGER);
        verify(ps).setNull(6, java.sql.Types.INTEGER);
        verify(ps).setNull(7, java.sql.Types.VARCHAR);
        verify(ps).setNull(8, java.sql.Types.INTEGER);
        verify(ps).setNull(9, java.sql.Types.INTEGER);
        verify(ps).setNull(10, java.sql.Types.VARCHAR);
        verify(ps).setNull(11, java.sql.Types.VARCHAR);
        verify(ps).setNull(12, java.sql.Types.VARCHAR);
        verify(ps).setNull(13, java.sql.Types.VARCHAR);
    }
}
