package org.ctc.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.LineupWindowEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LineupWindowEventRepository extends JpaRepository<LineupWindowEvent, UUID> {
    boolean existsByRaceId(UUID raceId);

    Optional<LineupWindowEvent> findFirstByRaceIdOrderBySequenceNumberDesc(UUID raceId);

    List<LineupWindowEvent> findByRaceIdOrderBySequenceNumberDesc(UUID raceId);

    @Query("select e from LineupWindowEvent e where e.race.id in :raceIds order by e.sequenceNumber desc")
    List<LineupWindowEvent> findByRaceIds(Collection<UUID> raceIds);

    @EntityGraph(attributePaths = {"race"})
    @Query("select e from LineupWindowEvent e")
    List<LineupWindowEvent> findAllForBackup();
}
