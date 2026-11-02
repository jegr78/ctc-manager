package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.ctc.domain.model.MatchdayPickRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface MatchdayPickRevisionRepository extends JpaRepository<MatchdayPickRevision, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from MatchdayPickRevision h where h.pick.season.id = :seasonId order by h.pick.id, h.id")
    List<MatchdayPickRevision> findHistoryBySeasonForUpdate(UUID seasonId);

    @EntityGraph(attributePaths = {"pick", "pick.race"})
    @Query("select e from MatchdayPickRevision e")
    List<MatchdayPickRevision> findAllForBackup();

    @Query("select r from MatchdayPickRevision r where r.pick.id = :pickId and r.revisionNumber = :revisionNumber")
    Optional<MatchdayPickRevision> findSnapshotByPickIdAndRevisionNumber(UUID pickId, int revisionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchdayPickRevision> findByRequestId(UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchdayPickRevision> findByPickIdAndRevisionNumber(UUID pickId, int revisionNumber);
}
