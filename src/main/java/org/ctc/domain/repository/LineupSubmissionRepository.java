package org.ctc.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.LineupSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LineupSubmissionRepository extends JpaRepository<LineupSubmission, UUID> {
    Optional<LineupSubmission> findByRaceIdAndTeamId(UUID raceId, UUID teamId);

    @EntityGraph(attributePaths = {"race", "team"})
    List<LineupSubmission> findByRaceIdIn(Collection<UUID> raceIds);

    @EntityGraph(attributePaths = {"race", "team"})
    @Query("select s from LineupSubmission s")
    List<LineupSubmission> findAllForBackup();
}
