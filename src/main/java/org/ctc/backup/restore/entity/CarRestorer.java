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
public class CarRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO cars (id, manufacturer, name, gt7_id, image_url, created_at, updated_at, category, drivetrain, model_year, proposed_model_year, category_source, drivetrain_source, model_year_source, model_year_evidence, proposed_model_year_evidence, category_override, drivetrain_override, model_year_override) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "cars";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("CarRestorer: skipping batchUpdate — no rows");
            return;
        }
        for (JsonNode row : rows) validateMetadata(row);
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("manufacturer").asText());
            ps.setString(3, row.get("name").asText());
            nullableString(ps, 4, row, "gt7Id");
            nullableString(ps, 5, row, "imageUrl");
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
            nullableString(ps, 8, row, "category");
            nullableString(ps, 9, row, "drivetrain");
            nullableInt(ps, 10, row, "modelYear");
            nullableInt(ps, 11, row, "proposedModelYear");
            nullableString(ps, 12, row, "categorySource");
            nullableString(ps, 13, row, "drivetrainSource");
            nullableString(ps, 14, row, "modelYearSource");
            nullableString(ps, 15, row, "modelYearEvidence");
            nullableString(ps, 16, row, "proposedModelYearEvidence");
            ps.setBoolean(17, row.path("categoryOverride").asBoolean(false));
            ps.setBoolean(18, row.path("drivetrainOverride").asBoolean(false));
            ps.setBoolean(19, row.path("modelYearOverride").asBoolean(false));
        });
        log.debug("CarRestorer: inserted {} car rows", rows.size());
    }

    private static void validateMetadata(JsonNode row) {
        for (String field : List.of("modelYear", "proposedModelYear")) {
            JsonNode value = row.get(field);
            if (value != null && !value.isNull() && (!value.isIntegralNumber() || !value.canConvertToInt()
                    || value.intValue() < 1000 || value.intValue() > 9999)) {
                throw new IllegalArgumentException("Invalid car model year in backup");
            }
        }
        if (row.hasNonNull("category")) org.ctc.domain.model.CarCategory.valueOf(row.get("category").asText());
        if (row.hasNonNull("drivetrain")) org.ctc.domain.model.Drivetrain.valueOf(row.get("drivetrain").asText());
        for (String field : List.of("categorySource", "drivetrainSource", "modelYearSource", "modelYearEvidence", "proposedModelYearEvidence")) {
            JsonNode value = row.get(field);
            int maximum = field.endsWith("Evidence") ? 1000 : 500;
            if (value != null && !value.isNull() && (!value.isTextual() || value.textValue().length() > maximum))
                throw new IllegalArgumentException("Invalid car metadata provenance in backup");
        }
        if (row.hasNonNull("modelYear") && (row.path("modelYearSource").asText("").isBlank()
                || row.path("modelYearEvidence").asText("").isBlank())) {
            throw new IllegalArgumentException("Accepted model year lacks provenance in backup");
        }
        for (String field : List.of("categoryOverride", "drivetrainOverride", "modelYearOverride"))
            if (row.has(field) && !row.get(field).isBoolean()) throw new IllegalArgumentException("Invalid car override flag in backup");
    }

    private static void nullableInt(PreparedStatement ps, int index, JsonNode row, String field) throws SQLException {
        if (row.hasNonNull(field)) ps.setInt(index, row.get(field).intValue());
        else ps.setNull(index, Types.INTEGER);
    }

    private static void nullableString(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value.asText());
        }
    }
}
