package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.restore.EntityRestorer;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
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
        rows.forEach(row -> {
            kind(row);
            slug(row, "slug");
            slug(row, "baseSlug");
        });
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    JsonNode entityId = row.get("entityId");
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setString(2, kind(row));
                    ps.setString(3, slug(row, "slug"));
                    ps.setString(4, slug(row, "baseSlug"));
                    ps.setObject(5, entityId == null || entityId.isNull() ? null : UUID.fromString(entityId.asText()));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("SiteSlugRestorer: restored {} rows", rows.size());
    }

    private static String kind(JsonNode row) {
        String kind = text(row, "kind");
        if (Arrays.stream(SiteSlugKind.values()).noneMatch(k -> k.name().equals(kind))) {
            throw new BackupArchiveException(Reason.MANIFEST_INVALID, "unknown site slug kind '" + kind + "'");
        }
        return kind;
    }

    private static String slug(JsonNode row, String field) {
        String slug = text(row, field);
        if (!SiteSlug.FORMAT.matcher(slug).matches()) {
            throw new BackupArchiveException(Reason.MANIFEST_INVALID,
                    "invalid site slug in column '" + field + "' of site_slugs row");
        }
        return slug;
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) {
            throw new BackupArchiveException(Reason.MANIFEST_INVALID,
                    "missing required column '" + field + "' in site_slugs row");
        }
        return node.asText();
    }
}
