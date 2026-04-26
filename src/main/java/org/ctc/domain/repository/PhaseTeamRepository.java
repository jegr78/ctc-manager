package org.ctc.domain.repository;

import org.ctc.domain.model.PhaseTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PhaseTeamRepository extends JpaRepository<PhaseTeam, UUID> {
}
