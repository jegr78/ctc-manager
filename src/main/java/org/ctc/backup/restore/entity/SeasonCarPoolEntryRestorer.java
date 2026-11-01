package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SeasonCarPoolEntryRestorer implements EntityRestorer {
    @Override
    public String tableName() { return "season_car_pool_entries"; }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate("INSERT INTO season_car_pool_entries (id, season_id, label, sort_index, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)", rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("season").asText()));
            ps.setString(3, row.get("label").asText());
            ps.setInt(4, row.get("sortIndex").intValue());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
    }
}
