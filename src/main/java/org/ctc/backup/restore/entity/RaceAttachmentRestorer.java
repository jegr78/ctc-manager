package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code race_attachments} table from the
 * {@code data/race-attachments.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL, type VARCHAR(10) NOT NULL,
 * name VARCHAR NOT NULL, url VARCHAR(1000) NOT NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}. {@code ON DELETE CASCADE} on the race FK.
 *
 * <p>CRITICAL: {@code type} is {@link org.ctc.domain.model.AttachmentType} declared with
 * {@code @Enumerated(EnumType.STRING)} (per CONVENTIONS.md) — bound via {@code setString} so
 * the enum names {@code "FILE"} / {@code "LINK"} survive the round-trip verbatim.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceAttachmentRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_attachments (id, race_id, type, name, url, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_attachments";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            // AttachmentType is @Enumerated(STRING) — bind as VARCHAR, not as int.
            ps.setString(3, row.get("type").asText());
            ps.setString(4, row.get("name").asText());
            ps.setString(5, row.get("url").asText());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceAttachmentRestorer: restored {} rows", rows.size());
    }
}
