package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PickPublicationAttemptRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO pick_publication_attempts (id,batch_id,race_id,season_id,original_matchday_id,original_home_team_id,original_away_team_id,content_version,source_version,origin,output_type,track_id,car_id,car_pool_entry_id,track_name,car_name,pool_entry_label,laps,tyre_wear,fuel_consumption,refueling_speed,initial_fuel,required_pit_stops,time_progression_mode,time_progression_multiplier,weather,time_of_day,available_tyres,mandatory_tyres,content_hash,field_mask,actor_id,channel_id,webhook_id,target_message_id,template_hash,request_manifest,request_hash,runtime_generation,captured_at,state,active_race_id,message_id,completed_at,failure_code,reconciled_by,reconciliation_reason,resolved_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "pick_publication_attempts"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            Object[] values = {
                uuid(row, "id"),
                uuid(row, "batchId"),
                uuid(row, "race"),
                uuid(row, "seasonId"),
                uuid(row, "originalMatchdayId"),
                uuid(row, "originalHomeTeamId"),
                uuid(row, "originalAwayTeamId"),
                number(row, "contentVersion"),
                number(row, "sourceVersion"),
                text(row, "origin"),
                text(row, "output"),
                uuid(row.path("content"), "trackId"),
                uuid(row.path("content"), "carId"),
                uuid(row.path("content"), "carPoolEntryId"),
                text(row.path("content"), "trackName"),
                text(row.path("content"), "carName"),
                text(row.path("content"), "poolEntryLabel"),
                integer(row.path("content"), "laps"),
                integer(row.path("content"), "tyreWear"),
                integer(row.path("content"), "fuelConsumption"),
                integer(row.path("content"), "refuelingSpeed"),
                text(row.path("content"), "initialFuel"),
                integer(row.path("content"), "requiredPitStops"),
                text(row.path("content"), "timeProgressionMode"),
                integer(row.path("content"), "timeProgressionMultiplier"),
                text(row.path("content"), "weather"),
                text(row.path("content"), "timeOfDay"),
                text(row.path("content"), "availableTyres"),
                text(row.path("content"), "mandatoryTyres"),
                text(row, "contentHash"),
                integer(row, "fieldMask"),
                text(row, "actorId"),
                text(row, "channelId"),
                text(row, "webhookId"),
                text(row, "targetMessageId"),
                text(row, "templateHash"),
                text(row, "requestManifest"),
                text(row, "requestHash"),
                text(row, "runtimeGeneration"),
                row.hasNonNull("capturedAt") ? PickRestoreSupport.instant(row, "capturedAt") : null,
                text(row, "state"),
                uuid(row, "activeRaceId"),
                text(row, "messageId"),
                row.hasNonNull("completedAt") ? PickRestoreSupport.instant(row, "completedAt") : null,
                text(row, "failureCode"),
                text(row, "reconciledBy"),
                text(row, "reconciliationReason"),
                row.hasNonNull("resolvedAt") ? PickRestoreSupport.instant(row, "resolvedAt") : null,
                PickRestoreSupport.auditTime(row, "createdAt"),
                PickRestoreSupport.auditTime(row, "updatedAt")
            };
            for (int index = 0; index < values.length; index++) ps.setObject(index + 1, values[index]);
        });
    }

    private static Object uuid(JsonNode row, String field) {
        return row.hasNonNull(field) ? UUID.fromString(row.get(field).asText()) : null;
    }
    private static Object integer(JsonNode row, String field) {
        return row.hasNonNull(field) ? row.get(field).intValue() : null;
    }
    private static Object number(JsonNode row, String field) {
        return row.hasNonNull(field) ? row.get(field).longValue() : null;
    }
    private static String text(JsonNode row, String field) {
        return row.hasNonNull(field) ? row.get(field).asText() : null;
    }
}
