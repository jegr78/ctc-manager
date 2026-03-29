package de.ctc.domain.repository;

import de.ctc.domain.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByMatchdayId(UUID matchdayId);

    List<Match> findByMatchdaySeasonId(UUID seasonId);

    boolean existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(UUID matchdayId, UUID homeTeamId, UUID awayTeamId);
}
