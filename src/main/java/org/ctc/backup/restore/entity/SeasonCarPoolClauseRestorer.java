package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SeasonCarPoolClauseRestorer implements EntityRestorer {
    @Override
    public String tableName() { return "season_car_pool_clauses"; }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate("INSERT INTO season_car_pool_clauses (id, entry_id, category, drivetrain, model_year, sort_index, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("entry").asText()));
            ps.setString(3, row.get("category").asText());
            ps.setString(4, row.hasNonNull("drivetrain") ? row.get("drivetrain").asText() : null);
            if (row.hasNonNull("modelYear")) ps.setInt(5, row.get("modelYear").intValue());
            else ps.setNull(5, Types.INTEGER);
            ps.setInt(6, row.get("sortIndex").intValue());
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
    }
}
