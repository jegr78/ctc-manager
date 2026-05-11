package org.ctc.backup.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Stock Spring Data repository for {@link DataImportAudit}.
 *
 * <p>Phase 72 needs no custom finders — Phase 75 calls only {@code save(...)}. A future
 * admin audit-history UI may add {@code findTop10ByOrderByExecutedAtDesc()} (CONTEXT
 * §"Claude's Discretion"), but that is out of v1.10 scope.
 *
 * <p><strong>Lives in {@code org.ctc.backup.audit}, NOT {@code org.ctc.domain.repository}.</strong>
 * Co-located with {@link DataImportAudit} so the IMPORT-08 package-name filter remains the
 * single source of truth for export-scope exclusion (D-06).
 */
public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> {
}
