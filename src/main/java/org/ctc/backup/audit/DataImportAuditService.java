package org.ctc.backup.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQUIRES_NEW audit-row writer for {@link DataImportAudit}.
 *
 * <p>{@link #recordResult(UUID, String, int, Map, Map, String, boolean)} runs in its own
 * transaction (propagation = {@link Propagation#REQUIRES_NEW}) so that an audit row written
 * during a mid-restore failure survives the outer wipe-and-restore rollback. A same-transaction
 * audit write would be rolled back together with the wipe — REQUIRES_NEW is the only correct
 * propagation.
 *
 * <p>The {@code executedBy} value is profile-aware:
 * {@code dev}/{@code local} → literal {@code "dev"};
 * otherwise → {@link SecurityContextHolder} authentication name.
 *
 * <p>JSON serialization uses {@code @Qualifier("backupObjectMapper")} so the strict backup
 * contract applies. A {@link JsonProcessingException} is re-thrown as {@link IllegalStateException}.
 *
 * <p><strong>Deliberately bypasses {@code AuditingEntityListener}</strong>:
 * {@link DataImportAudit} does NOT extend {@code BaseEntity}, so {@code executedAt} is set
 * explicitly via {@link Instant#now()} — imported timestamps must not be overwritten.
 */
@Slf4j
@Service
public class DataImportAuditService {

    /**
     * Writes the audit row via {@link JdbcTemplate#update(String, Object...)} instead of
     * {@code repository.save(...)} or {@code em.persist(...)}.
     *
     * <p>Rationale: {@link DataImportAudit} carries {@code @GeneratedValue(strategy = UUID)} but
     * the UUID is pre-allocated by the controller. Spring Data {@code SimpleJpaRepository.save(...)}
     * dispatches to {@code em.merge(...)} when the ID is non-null, producing an
     * {@code ObjectOptimisticLockingFailureException} on a brand-new row. Switching to
     * {@code em.persist(...)} instead throws {@code EntityExistsException} ("Detached entity
     * passed to persist") because Hibernate's identity-strategy logic flags any non-null
     * pre-allocated UUID as "already persisted elsewhere". A direct JDBC INSERT sidesteps both
     * traps (AuditingEntityListener bypass via {@link JdbcTemplate}).
     */
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper backupObjectMapper;
    private final BackupExecutedByResolver executedByResolver;

    private static final String INSERT_SQL =
            "INSERT INTO data_import_audit "
                    + "(id, executed_at, executed_by, schema_version, table_counts_wiped, "
                    + "table_counts_restored, source_filename, success) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public DataImportAuditService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
            BackupExecutedByResolver executedByResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.backupObjectMapper = backupObjectMapper;
        this.executedByResolver = executedByResolver;
    }

    /**
     * Writes one audit row in its own REQUIRES_NEW transaction.
     *
     * @param auditId            pre-generated UUID for the audit row (so the caller can echo it
     *                           in the user-facing flash message before the outer tx commits)
     * @param executedByCaller   nullable / blank → service resolves via {@link Environment} +
     *                           {@link SecurityContextHolder}; non-blank caller value wins
     *                           outside the dev/local profile fork
     * @param schemaVersion      from the manifest of the imported ZIP
     * @param tableCountsWiped   map of {tableName → row count wiped}; empty map on
     *                           failure-before-wipe; serialized via {@code backupObjectMapper}
     * @param tableCountsRestored map of {tableName → row count restored}; empty map on failure
     * @param sourceFilename     original multipart filename (carried via staging UUID)
     * @param success            {@code true} only when DB commit + audit save succeeded
     * @return the saved {@link DataImportAudit} entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataImportAudit recordResult(
            UUID auditId,
            String executedByCaller,
            int schemaVersion,
            Map<String, Long> tableCountsWiped,
            Map<String, Long> tableCountsRestored,
            String sourceFilename,
            boolean success) {

        Instant executedAt = Instant.now();
        String resolvedExecutedBy = executedByResolver.resolve(executedByCaller);
        String wipedJson = writeJson(tableCountsWiped);
        String restoredJson = writeJson(tableCountsRestored);

        jdbcTemplate.update(INSERT_SQL,
                auditId,
                Timestamp.from(executedAt),
                resolvedExecutedBy,
                schemaVersion,
                wipedJson,
                restoredJson,
                sourceFilename,
                success);

        long totalRestored = tableCountsRestored == null
                ? 0L
                : tableCountsRestored.values().stream().mapToLong(Long::longValue).sum();
        log.info("Audit row written: id={}, success={}, totalRestored={}",
                auditId, success, totalRestored);

        return DataImportAudit.builder()
                .id(auditId)
                .executedAt(executedAt)
                .executedBy(resolvedExecutedBy)
                .schemaVersion(schemaVersion)
                .tableCountsWiped(wipedJson)
                .tableCountsRestored(restoredJson)
                .sourceFilename(sourceFilename)
                .success(success)
                .build();
    }

    /** Serializes a (possibly null) Map to a JSON-text string using {@code backupObjectMapper}. */
    private String writeJson(Map<String, Long> map) {
        Map<String, Long> safeMap = map == null ? Map.of() : map;
        try {
            return backupObjectMapper.writeValueAsString(safeMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize tableCounts map via backupObjectMapper", e);
        }
    }
}
