package org.ctc.domain.repository;

import org.ctc.domain.model.RaceScoring;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceScoringRepository extends JpaRepository<RaceScoring, UUID> {
}
