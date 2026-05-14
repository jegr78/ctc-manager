package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 04 — restores rows into the {@code race_settings} table from the
 * {@code data/race-settings.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL UNIQUE, number_of_laps INT NULL,
 * tyre_wear_multiplier INT NULL, fuel_consumption_multiplier INT NULL, refueling_speed INT NULL,
 * initial_fuel VARCHAR NULL, number_of_required_pit_stops INT NULL,
 * time_progression_multiplier INT NULL, weather VARCHAR NULL, time_of_day VARCHAR NULL,
 * available_tyres VARCHAR NULL, mandatory_tyres VARCHAR NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}.
 *
 * <p>All non-FK columns are nullable per {@link org.ctc.domain.model.RaceSettings#isComplete()}
 * semantics (incomplete settings are allowed during draft race definition).
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceSettingsRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_settings (id, race_id, number_of_laps, tyre_wear_multiplier, "
          + "fuel_consumption_multiplier, refueling_speed, initial_fuel, "
          + "number_of_required_pit_stops, time_progression_multiplier, weather, "
          + "time_of_day, available_tyres, mandatory_tyres, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_settings";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            setNullableInt(ps, 3, row, "numberOfLaps");
            setNullableInt(ps, 4, row, "tyreWearMultiplier");
            setNullableInt(ps, 5, row, "fuelConsumptionMultiplier");
            setNullableInt(ps, 6, row, "refuelingSpeed");
            setNullableString(ps, 7, row, "initialFuel");
            setNullableInt(ps, 8, row, "numberOfRequiredPitStops");
            setNullableInt(ps, 9, row, "timeProgressionMultiplier");
            setNullableString(ps, 10, row, "weather");
            setNullableString(ps, 11, row, "timeOfDay");
            setNullableString(ps, 12, row, "availableTyres");
            setNullableString(ps, 13, row, "mandatoryTyres");
            ps.setTimestamp(14, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(15, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceSettingsRestorer: restored {} rows", rows.size());
    }

    private static void setNullableInt(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, n.asInt());
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, n.asText());
        }
    }
}
