package org.ctc.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentRuling;
import org.springframework.data.jpa.repository.*;

public interface IncidentRulingRepository extends JpaRepository<IncidentRuling, UUID> {
    Optional<IncidentRuling> findByReportId(UUID reportId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from IncidentRuling e where e.report.id = :reportId")
    Optional<IncidentRuling> findByReportIdForUpdate(UUID reportId);
    @EntityGraph(attributePaths = {"report"})
    @Query("select e from IncidentRuling e")
    java.util.List<IncidentRuling> findAllForBackup();
}
