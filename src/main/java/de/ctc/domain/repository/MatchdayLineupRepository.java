package de.ctc.domain.repository;

import de.ctc.domain.model.MatchdayLineup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchdayLineupRepository extends JpaRepository<MatchdayLineup, UUID> {

    List<MatchdayLineup> findByMatchdayId(UUID matchdayId);

    List<MatchdayLineup> findByMatchdayIdAndTeamId(UUID matchdayId, UUID teamId);

    Optional<MatchdayLineup> findByMatchdayIdAndDriverId(UUID matchdayId, UUID driverId);
}
