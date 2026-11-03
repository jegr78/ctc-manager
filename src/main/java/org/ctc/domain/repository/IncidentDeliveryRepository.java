package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentDeliveryRepository extends JpaRepository<IncidentDelivery, UUID> {
    Optional<IncidentDelivery> findByOperationKey(String key);
    java.util.List<IncidentDelivery> findByReportWindowId(UUID windowId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"report", "revision"})
    @org.springframework.data.jpa.repository.Query("select d from IncidentDelivery d")
    java.util.List<IncidentDelivery> findAllForBackup();
}
