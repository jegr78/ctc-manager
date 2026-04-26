package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonPhaseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeasonPhaseGroupRepository extends JpaRepository<SeasonPhaseGroup, UUID> {
}
