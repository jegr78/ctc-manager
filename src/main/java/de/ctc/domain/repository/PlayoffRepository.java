package de.ctc.domain.repository;

import de.ctc.domain.model.Playoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayoffRepository extends JpaRepository<Playoff, UUID> {

    Optional<Playoff> findBySeasonId(UUID seasonId);
}
