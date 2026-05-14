package org.ctc.backup.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 75 / Plan 02 — REQUIRES_NEW audit-row writer for {@link DataImportAudit}.
 *
 * <p>The single public method {@link #recordResult(UUID, String, int, Map, Map, String, boolean)}
 * runs in its own JPA transaction (propagation = {@link Propagation#REQUIRES_NEW}) so that an
 * audit row written during a mid-restore failure survives the outer wipe-and-restore rollback
 * (CONTEXT §D-01 / IMPORT-07 success criterion 3). A same-transaction audit write would be
 * rolled back together with the wipe — REQUIRES_NEW is the only correct propagation.
 *
 * <p>The {@code executedBy} resolution is profile-aware per CONTEXT §D-02:
 * <ul>
 *   <li>{@code dev} or {@code local} profile → literal string {@code "dev"};</li>
 *   <li>otherwise → {@link SecurityContextHolder} authentication name, falling back to the
 *       caller-supplied value if no security context is bound (defensive fallback).</li>
 * </ul>
 *
 * <p>JSON-text serialization of the {@code tableCountsWiped} / {@code tableCountsRestored}
 * maps uses the {@code @Qualifier("backupObjectMapper")} {@link ObjectMapper} so the strict
 * Phase 72 contract ({@code FAIL_ON_UNKNOWN_PROPERTIES=true}, {@code WRITE_DATES_AS_TIMESTAMPS=false})
 * applies. A {@link JsonProcessingException} is re-thrown as {@link IllegalStateException} —
 * the outer caller (Plan 06's failure-handler) is responsible for logging and continuing.
 *
 * <p><strong>The audit entity deliberately bypasses {@code AuditingEntityListener}</strong>:
 * {@link DataImportAudit} does NOT extend {@code BaseEntity}, so {@code executedAt} is set
 * EXPLICITLY here ({@link Instant#now()}) — this is exactly what Phase 75's IMPORT-05
 * transaction strategy enables.
 */
@Slf4j
@Service
public class DataImportAuditService {

    private final DataImportAuditRepository repository;
    private final ObjectMapper backupObjectMapper;
    private final Environment environment;

    public DataImportAuditService(
            DataImportAuditRepository repository,
            @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
            Environment environment) {
        this.repository = repository;
        this.backupObjectMapper = backupObjectMapper;
        this.environment = environment;
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

        String resolvedExecutedBy = resolveExecutedBy(executedByCaller);
        DataImportAudit audit = DataImportAudit.builder()
                .id(auditId)
                .executedAt(Instant.now())
                .executedBy(resolvedExecutedBy)
                .schemaVersion(schemaVersion)
                .tableCountsWiped(writeJson(tableCountsWiped))
                .tableCountsRestored(writeJson(tableCountsRestored))
                .sourceFilename(sourceFilename)
                .success(success)
                .build();
        DataImportAudit saved = repository.save(audit);
        long totalRestored = tableCountsRestored == null
                ? 0L
                : tableCountsRestored.values().stream().mapToLong(Long::longValue).sum();
        log.info("Audit row written: id={}, success={}, totalRestored={}",
                saved.getId(), success, totalRestored);
        return saved;
    }

    /**
     * Resolves {@code executedBy} per CONTEXT §D-02.
     * <ul>
     *   <li>{@code dev} or {@code local} profile → literal {@code "dev"};</li>
     *   <li>otherwise → SecurityContext authentication name;</li>
     *   <li>fallback when neither yields a value → caller-supplied value or {@code "unknown"}.</li>
     * </ul>
     */
    private String resolveExecutedBy(String executedByCaller) {
        if (environment.matchesProfiles("dev | local")) {
            return "dev";
        }
        if (executedByCaller != null && !executedByCaller.isBlank()) {
            return executedByCaller;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "unknown";
    }

    /** Serializes a (possibly null) Map to a JSON-text string using {@code backupObjectMapper}. */
    private String writeJson(Map<String, Long> map) {
        Map<String, Long> safeMap = (map == null) ? Map.of() : map;
        try {
            return backupObjectMapper.writeValueAsString(safeMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize tableCounts map via backupObjectMapper", e);
        }
    }
}
