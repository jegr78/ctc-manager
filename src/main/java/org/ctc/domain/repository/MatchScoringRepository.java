package org.ctc.domain.repository;

import org.ctc.domain.model.MatchScoring;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchScoringRepository extends JpaRepository<MatchScoring, UUID> {
}
