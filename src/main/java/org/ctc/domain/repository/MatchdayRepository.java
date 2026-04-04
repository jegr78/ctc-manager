package org.ctc.domain.repository;

import org.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

    @EntityGraph(attributePaths = {"season"})
    List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);
}
