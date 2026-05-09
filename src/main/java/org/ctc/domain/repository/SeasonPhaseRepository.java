package org.ctc.domain.repository;

import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonPhaseRepository extends JpaRepository<SeasonPhase, UUID> {

    Optional<SeasonPhase> findBySeasonIdAndPhaseType(UUID seasonId, PhaseType phaseType);

    List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId);
}
