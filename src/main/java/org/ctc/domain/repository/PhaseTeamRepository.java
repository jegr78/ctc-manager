package org.ctc.domain.repository;

import org.ctc.domain.model.PhaseTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhaseTeamRepository extends JpaRepository<PhaseTeam, UUID> {

    List<PhaseTeam> findByPhaseId(UUID phaseId);

    List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId); // groupId=null derives IS NULL

    Optional<PhaseTeam> findByPhaseIdAndTeamId(UUID phaseId, UUID teamId); // Phase 59 D-05 — UNIQUE (phase_id, team_id) → at most one hit

    boolean existsByPhaseSeasonId(UUID seasonId); // for D-18 delete-guard
}
