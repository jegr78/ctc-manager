package org.ctc.domain.repository;

import org.ctc.domain.model.PhaseTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhaseTeamRepository extends JpaRepository<PhaseTeam, UUID> {

    List<PhaseTeam> findByPhaseId(UUID phaseId);

    List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId);

    Optional<PhaseTeam> findByPhaseIdAndTeamId(UUID phaseId, UUID teamId);

    boolean existsByPhaseSeasonId(UUID seasonId);
}
