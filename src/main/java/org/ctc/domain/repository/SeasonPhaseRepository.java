package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeasonPhaseRepository extends JpaRepository<SeasonPhase, UUID> {
}
