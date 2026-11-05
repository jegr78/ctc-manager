package org.ctc.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PickPublicationAttempt;
import org.ctc.domain.model.PickPublicationState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PickPublicationAttemptRepository extends JpaRepository<PickPublicationAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PickPublicationAttempt a where a.seasonId = :seasonId order by a.id")
    List<PickPublicationAttempt> findRulesHistoryForUpdate(UUID seasonId);

    Optional<PickPublicationAttempt> findByActiveRaceId(UUID raceId);
    Optional<PickPublicationAttempt> findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(UUID raceId);
    List<PickPublicationAttempt> findByBatchId(UUID batchId);
    List<PickPublicationAttempt> findByStateInOrderByCapturedAtDesc(Collection<PickPublicationState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PickPublicationAttempt a where a.id = :id")
    Optional<PickPublicationAttempt> findByIdForUpdate(UUID id);

    @EntityGraph(attributePaths = {"race"})
    @Query("select a from PickPublicationAttempt a")
    List<PickPublicationAttempt> findAllForBackup();
}
