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
public class SeasonCarPoolMemberRestorer implements EntityRestorer {
    @Override
    public String tableName() { return "season_car_pool_members"; }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate("INSERT INTO season_car_pool_members (id, entry_id, car_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("entry").asText()));
            ps.setObject(3, UUID.fromString(row.get("car").asText()));
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
    }
}
