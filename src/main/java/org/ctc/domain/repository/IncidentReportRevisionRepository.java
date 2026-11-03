package org.ctc.domain.repository;

import java.util.UUID;
import org.ctc.domain.model.IncidentReportRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface IncidentReportRevisionRepository extends JpaRepository<IncidentReportRevision, UUID> {
    List<IncidentReportRevision> findByReportIdOrderByRevisionNumberAsc(UUID reportId);
    Optional<IncidentReportRevision> findByReportIdAndRevisionNumber(UUID reportId, int revisionNumber);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"report"})
    @org.springframework.data.jpa.repository.Query("select e from IncidentReportRevision e")
    List<IncidentReportRevision> findAllForBackup();
}
