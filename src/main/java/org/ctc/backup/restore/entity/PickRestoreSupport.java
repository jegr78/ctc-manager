package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.PickTyreSetConverter;

final class PickRestoreSupport {
    static final String PAYLOAD_COLUMNS = "track_id,car_id,car_pool_entry_id,laps,tyre_wear,fuel_consumption,refueling_speed,initial_fuel,required_pit_stops,time_progression_mode,time_progression_multiplier,weather,time_of_day,available_tyres,mandatory_tyres,lobby_rules_profile,start_type,slipstream";
    private PickRestoreSupport() { }

    static void payload(PreparedStatement statement, int start, PickPayload payload) throws SQLException {
        Object[] fields;
        if (payload == null) {
            fields = new Object[18];
        } else {
            var tyres = new PickTyreSetConverter();
            fields = new Object[]{payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(),
                    payload.tyreWear(), payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel().name(),
                    payload.requiredPitStops(), payload.timeProgressionMode().name(), payload.timeProgressionMultiplier(),
                    payload.weather(), payload.timeOfDay(), tyres.convertToDatabaseColumn(payload.availableTyres()),
                    tyres.convertToDatabaseColumn(payload.mandatoryTyres()), payload.lobbyRulesProfile().name(),
                    payload.startType().name(), payload.slipstream().name()};
        }
        for (int index = 0; index < fields.length; index++) statement.setObject(start + index, fields[index]);
    }

    static Timestamp auditTime(JsonNode row, String field) {
        return !row.hasNonNull(field) ? null : Timestamp.valueOf(LocalDateTime.parse(row.get(field).asText()));
    }

    static Timestamp instant(JsonNode row, String field) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(Instant.parse(row.get(field).asText()), ZoneOffset.UTC));
    }
}
