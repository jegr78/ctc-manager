package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code season_phases} table from the
 * {@code data/season-phases.json} array in a backup ZIP.
 *
 * <p>Schema reference (V3__add_season_phase_tables.sql:5-26):
 * <pre>
 * CREATE TABLE season_phases (
 *   id UUID PRIMARY KEY,
 *   season_id UUID NOT NULL,
 *   sort_index INT NOT NULL,
 *   phase_type VARCHAR(20) NOT NULL,    (enum SeasonPhase.phaseType)
 *   layout VARCHAR(20) NOT NULL,        (enum SeasonPhase.layout)
 *   format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL,
 *   label VARCHAR(255),
 *   start_date DATE,
 *   end_date DATE,
 *   total_rounds INT,
 *   legs INT NOT NULL DEFAULT 1,
 *   event_duration_minutes INT,
 *   race_scoring_id UUID NOT NULL,
 *   match_scoring_id UUID NOT NULL,
 *   created_at TIMESTAMP, updated_at TIMESTAMP);
 * </pre>
 *
 * <p>JSON shape (from {@code SeasonPhaseMixIn}): FK fields {@code season},
 * {@code raceScoring}, {@code matchScoring} render as bare UUID strings via
 * {@code @JsonIdentityReference(alwaysAsId=true)} — NOT nested {@code {id:...}} objects.
 *
 * <p>Nullable INT columns ({@code total_rounds}, {@code event_duration_minutes}) bind via
 * {@code ps.setObject(idx, Integer | null, Types.INTEGER)} so a {@code null} JSON value is
 * preserved as SQL {@code NULL} rather than collapsed to {@code 0} by {@code asInt()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonPhaseRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
                    + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
                    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "season_phases";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("season").asText()));
                    ps.setInt(3, row.get("sortIndex").asInt());
                    ps.setString(4, row.get("phaseType").asText());
                    ps.setString(5, row.get("layout").asText());
                    ps.setString(6, row.get("format").asText());
                    ps.setString(7, nullableString(row, "label"));
                    ps.setDate(8, nullableDate(row, "startDate"));
                    ps.setDate(9, nullableDate(row, "endDate"));
                    ps.setObject(10, nullableInt(row, "totalRounds"), Types.INTEGER);
                    ps.setInt(11, row.get("legs").asInt());
                    ps.setObject(12, nullableInt(row, "eventDurationMinutes"), Types.INTEGER);
                    ps.setObject(13, UUID.fromString(row.get("raceScoring").asText()));
                    ps.setObject(14, UUID.fromString(row.get("matchScoring").asText()));
                    ps.setTimestamp(15, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(16, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("SeasonPhaseRestorer: restored {} rows", rows.size());
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static Date nullableDate(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : Date.valueOf(LocalDate.parse(n.asText()));
    }

    private static Integer nullableInt(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asInt();
    }
}
