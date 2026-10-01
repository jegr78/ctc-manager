package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SiteSlugRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO site_slugs (id, kind, slug, base_slug, entity_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "site_slugs";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    JsonNode entityId = row.get("entityId");
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setString(2, row.get("kind").asText());
                    ps.setString(3, row.get("slug").asText());
                    ps.setString(4, row.get("baseSlug").asText());
                    ps.setObject(5, entityId == null || entityId.isNull() ? null : UUID.fromString(entityId.asText()));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("SiteSlugRestorer: restored {} rows", rows.size());
    }
}
