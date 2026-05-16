package org.ctc.backup.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DataImportAudit}. No custom finders.
 *
 * <p>Co-located with {@link DataImportAudit} so the IMPORT-08 package-name filter
 * ({@code startsWith("org.ctc.domain.model")}) remains the single source of truth for
 * export-scope exclusion — no marker annotation needed.
 */
public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> {
}
