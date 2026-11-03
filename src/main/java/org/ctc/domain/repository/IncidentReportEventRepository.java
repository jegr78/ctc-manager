package org.ctc.domain.repository;

import java.util.UUID;
import org.ctc.domain.model.IncidentReportEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface IncidentReportEventRepository extends JpaRepository<IncidentReportEvent, UUID> {
    Optional<IncidentReportEvent> findByRequestId(UUID requestId);
    List<IncidentReportEvent> findByWindowIdOrderByAcceptedAtAscIdAsc(UUID windowId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"window", "report"})
    @org.springframework.data.jpa.repository.Query("select e from IncidentReportEvent e")
    List<IncidentReportEvent> findAllForBackup();
}
