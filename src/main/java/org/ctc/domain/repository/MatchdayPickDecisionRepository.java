package org.ctc.domain.repository;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import org.ctc.domain.model.MatchdayPickDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

public interface MatchdayPickDecisionRepository extends JpaRepository<MatchdayPickDecision, UUID> {
    List<MatchdayPickDecision> findByPickIdOrderByAggregateVersionDesc(UUID pickId);
    java.util.Optional<MatchdayPickDecision> findFirstByPickIdAndKindOrderByAggregateVersionDesc(
            UUID pickId, org.ctc.domain.model.PickDecisionKind kind);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from MatchdayPickDecision h where h.pick.season.id = :seasonId order by h.pick.id, h.id")
    List<MatchdayPickDecision> findHistoryBySeasonForUpdate(UUID seasonId);

    @EntityGraph(attributePaths = {"pick", "pick.race"})
    @Query("select e from MatchdayPickDecision e")
    List<MatchdayPickDecision> findAllForBackup();
 }
