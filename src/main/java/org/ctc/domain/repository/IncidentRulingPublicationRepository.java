package org.ctc.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.ctc.domain.model.IncidentRulingPublication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface IncidentRulingPublicationRepository extends JpaRepository<IncidentRulingPublication, UUID> {
    @EntityGraph(attributePaths = {"revision.ruling.report.window.context.season", "revision.ruling.report.race.matchday.phase.season",
            "revision.ruling.report.originalMatchday.phase.season", "revision.ruling.report.race.match",
            "revision.ruling.report.race.playoffMatchup", "revision.ruling.report.window.context.match",
            "revision.ruling.report.window.context.playoffMatchup", "threadPublication.delivery.revision.report"})
    @Query("select p from IncidentRulingPublication p where p.revision.ruling.report.window.context.season.id=:seasonId "
            + "and p.state=org.ctc.domain.model.IncidentDeliveryState.SENT "
            + "and p.revision.ruling.publishedRevisionNumber=p.revision.revisionNumber "
            + "and p.revision.ruling.publishedMessageId=p.messageId "
            + "and p.completedAt<=:now order by p.completedAt desc, p.id")
    List<IncidentRulingPublication> findConfirmedForSeason(UUID seasonId, Instant now);

    Optional<IncidentRulingPublication> findByRevisionId(UUID revisionId);
    List<IncidentRulingPublication> findByRevisionRulingIdOrderByRequestedAtAscIdAsc(UUID rulingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IncidentRulingPublication p where p.id=:id")
    Optional<IncidentRulingPublication> findByIdForUpdate(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IncidentRulingPublication p where p.revision.ruling.report.window.id=:windowId order by p.id")
    List<IncidentRulingPublication> findByWindowForUpdate(UUID windowId);
    @Query("select p.id from IncidentRulingPublication p where p.revision.ruling.report.window.deliveryPaused=false and "
            + "(p.revision.ruling.report.window.deliveryResumedAt is null or p.restoreReviewed=true) and "
            + "p.revision.ruling.report.window.state='CLOSED' and p.revision.ruling.report.state='ACTIVE' and "
            + "((p.state in ('PENDING','FAILED') and p.nextAttemptAt<=:now) or (p.state='CLAIMED' and p.leaseUntil<=:now)) order by p.requestedAt,p.id")
    List<UUID> findDueIds(Instant now, Pageable page);
    @EntityGraph(attributePaths = {"revision", "threadPublication"})
    @Query("select p from IncidentRulingPublication p")
    List<IncidentRulingPublication> findAllForBackup();
}
