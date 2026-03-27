package de.ctc.domain.repository;

import de.ctc.domain.model.Matchday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, UUID> {

    List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);
}
