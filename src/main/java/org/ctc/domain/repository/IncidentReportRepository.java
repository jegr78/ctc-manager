package org.ctc.domain.repository;

import java.util.UUID;
import java.util.Optional;
import org.ctc.domain.model.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, UUID> {
    List<IncidentReport> findByWindowIdOrderByCaseReferenceAsc(UUID windowId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from IncidentReport e where e.window.id = :windowId order by e.id")
    List<IncidentReport> findByWindowIdForUpdate(UUID windowId);
    boolean existsByRaceId(UUID raceId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    Optional<IncidentReport> findFirstByRaceIdOrderByIdAsc(UUID raceId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from IncidentReport e where e.id = :id")
    Optional<IncidentReport> findByIdForUpdate(UUID id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"window", "race", "originalMatchday", "originalHomeTeam", "originalAwayTeam"})
    @org.springframework.data.jpa.repository.Query("select e from IncidentReport e")
    List<IncidentReport> findAllForBackup();
}
