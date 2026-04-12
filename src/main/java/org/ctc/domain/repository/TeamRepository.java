package org.ctc.domain.repository;

import org.ctc.domain.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

	Optional<Team> findByShortName(String shortName);

	Optional<Team> findByShortNameIgnoreCase(String shortName);
}
