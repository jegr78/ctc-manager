package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentThreadPublication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentThreadPublicationRepository extends JpaRepository<IncidentThreadPublication, UUID> {
    java.util.List<IncidentThreadPublication> findByDeliveryReportWindowId(UUID windowId);
    Optional<IncidentThreadPublication> findByDeliveryId(UUID deliveryId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from IncidentThreadPublication p where p.delivery.id=:deliveryId")
    Optional<IncidentThreadPublication> findByDeliveryIdForUpdate(UUID deliveryId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"delivery"})
    @org.springframework.data.jpa.repository.Query("select p from IncidentThreadPublication p")
    java.util.List<IncidentThreadPublication> findAllForBackup();
}
