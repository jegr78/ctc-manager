package org.ctc.domain.repository;

import org.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {

	Optional<Playoff> findBySeasonId(UUID seasonId);

	@Query("SELECT p FROM Playoff p JOIN p.seasons s WHERE s.id = :seasonId")
	Optional<Playoff> findByLinkedSeasonId(UUID seasonId);
}
