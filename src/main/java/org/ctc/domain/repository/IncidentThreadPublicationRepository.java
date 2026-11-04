package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentThreadPublication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentThreadPublicationRepository extends JpaRepository<IncidentThreadPublication, UUID> {
    java.util.List<IncidentThreadPublication> findByDeliveryReportWindowId(UUID windowId);
    Optional<IncidentThreadPublication> findByDeliveryId(UUID deliveryId);
}
