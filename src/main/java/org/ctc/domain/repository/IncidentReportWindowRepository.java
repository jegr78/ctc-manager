package org.ctc.domain.repository;

import java.util.UUID;
import java.util.List;
import org.ctc.domain.model.IncidentReportWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IncidentReportWindowRepository extends JpaRepository<IncidentReportWindow, UUID> {
    Optional<IncidentReportWindow> findByContextId(UUID contextId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from IncidentReportWindow e where e.id = :id")
    Optional<IncidentReportWindow> findByIdForUpdate(UUID id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"context"})
    @org.springframework.data.jpa.repository.Query("select e from IncidentReportWindow e")
    List<IncidentReportWindow> findAllForBackup();
}
