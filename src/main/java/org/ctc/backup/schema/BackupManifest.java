package org.ctc.backup.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Wire-format spec for the {@code manifest.json} entry in every backup ZIP.
 *
 * <p>Phase 72 ships only the record definition. Phase 73's BackupExportService is
 * responsible for serializing this through the {@code backupObjectMapper} qualifier
 * (FAIL_ON_UNKNOWN_PROPERTIES=true / WRITE_DATES_AS_TIMESTAMPS=false / JavaTimeModule)
 * and writing it as the FIRST entry in the ZipOutputStream (D-14, owned by Phase 73).
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code schemaVersion} — monotonic integer; equals {@link BackupSchema#SCHEMA_VERSION}
 *       at export time. Phase 74's import service refuses ZIPs whose value does not match.</li>
 *   <li>{@code appVersion} — CTC application version sourced from {@code @Value("${app.version}")}
 *       (Maven resource-filtered {@code @project.version@}; see {@code GlobalModelAdvice}).
 *       Phase 72 does NOT inject this — Phase 73's BackupExportService caller supplies the value
 *       when constructing the record. This avoids the unnecessary {@code BuildProperties} pom.xml
 *       wiring documented in RESEARCH §Pitfall P-3.</li>
 *   <li>{@code exportDate} — {@link Instant} at export start. ISO-8601 string in JSON.</li>
 *   <li>{@code tableCounts} — snake_case table name (matching {@code @Table(name=...)}) to row count.
 *       Keys mirror Phase 73's per-entity {@code data/&lt;entity&gt;.json} filename derivation.</li>
 * </ul>
 *
 * <p>JSON shape: per-field {@code @JsonProperty} annotations lock the snake_case keys
 * regardless of any project-wide {@code PropertyNamingStrategies} setting — the rule lives
 * with the record, not with the mapper.
 */
public record BackupManifest(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("app_version") String appVersion,
        @JsonProperty("export_date") Instant exportDate,
        @JsonProperty("table_counts") Map<String, Long> tableCounts
) {
}
