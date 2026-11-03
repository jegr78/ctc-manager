package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.IncidentDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentDeliveryRepository extends JpaRepository<IncidentDelivery, UUID> {
    Optional<IncidentDelivery> findByOperationKey(String key);
    java.util.List<IncidentDelivery> findByReportWindowId(UUID windowId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from IncidentDelivery d where d.id=:id")
    Optional<IncidentDelivery> findByIdForUpdate(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from IncidentDelivery d where d.operationKey=:key")
    Optional<IncidentDelivery> findByOperationKeyForUpdate(String key);
    @org.springframework.data.jpa.repository.Query("select d.id from IncidentDelivery d where d.operation='RECEIPT' and d.report.window.deliveryPaused=false and ((d.state in ('PENDING','FAILED') and d.nextAttemptAt<=:now) or (d.state='CLAIMED' and d.leaseUntil<=:now)) order by d.capturedAt,d.id")
    java.util.List<UUID> findDueReceiptIds(java.time.Instant now, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"report", "revision"})
    @org.springframework.data.jpa.repository.Query("select d from IncidentDelivery d")
    java.util.List<IncidentDelivery> findAllForBackup();
}
