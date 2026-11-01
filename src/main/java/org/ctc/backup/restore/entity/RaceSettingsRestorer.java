package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RaceSettingsRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_settings (id, race_id, number_of_laps, tyre_wear_multiplier, "
          + "fuel_consumption_multiplier, refueling_speed, initial_fuel, "
          + "number_of_required_pit_stops, time_progression_multiplier, weather, "
          + "time_of_day, available_tyres, mandatory_tyres, created_at, updated_at, time_progression_mode) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_settings";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        for (JsonNode row : rows) {
            if (row.has("timeProgressionMode")) {
                JsonNode mode = row.get("timeProgressionMode");
                JsonNode value = row.get("timeProgressionMultiplier");
                boolean numeric = "MULTIPLIER".equals(mode.asText());
                boolean equal = "EQUAL_CONDITIONS".equals(mode.asText());
                if ((!numeric && !equal) || (equal && value != null && !value.isNull())
                        || (numeric && value != null && !value.isNull()
                            && (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0))) {
                    throw new IllegalArgumentException("Invalid time progression mode or multiplier in backup.");
                }
            }
        }
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
            ps.setString(16, row.has("timeProgressionMode") ? row.get("timeProgressionMode").asText() : "MULTIPLIER");
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
