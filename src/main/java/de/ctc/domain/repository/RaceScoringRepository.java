package de.ctc.domain.repository;

import de.ctc.domain.model.RaceScoring;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceScoringRepository extends JpaRepository<RaceScoring, UUID> {
}
