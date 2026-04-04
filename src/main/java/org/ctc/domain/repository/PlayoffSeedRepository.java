package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffSeed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayoffSeedRepository extends JpaRepository<PlayoffSeed, UUID> {

    @EntityGraph(attributePaths = {"team"})
    List<PlayoffSeed> findByPlayoffId(UUID playoffId);

    Optional<PlayoffSeed> findByPlayoffIdAndTeamId(UUID playoffId, UUID teamId);

    void deleteByPlayoffId(UUID playoffId);
}
