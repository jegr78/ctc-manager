package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentRulingRevision;
import org.springframework.data.jpa.repository.*;

public interface IncidentRulingRevisionRepository extends JpaRepository<IncidentRulingRevision, UUID> {
    List<IncidentRulingRevision> findByRulingIdOrderByRevisionNumberAsc(UUID rulingId);
    Optional<IncidentRulingRevision> findByRulingIdAndRevisionNumber(UUID rulingId, int revisionNumber);
    @EntityGraph(attributePaths = {"ruling"})
    @Query("select e from IncidentRulingRevision e")
    List<IncidentRulingRevision> findAllForBackup();
}
