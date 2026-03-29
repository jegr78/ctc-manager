package de.ctc.domain.repository;

import de.ctc.domain.model.MatchScoring;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchScoringRepository extends JpaRepository<MatchScoring, UUID> {
}
