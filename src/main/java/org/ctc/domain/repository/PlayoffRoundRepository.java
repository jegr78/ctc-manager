package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffRound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayoffRoundRepository extends JpaRepository<PlayoffRound, UUID> {

    @EntityGraph(attributePaths = {"playoff"})
    List<PlayoffRound> findByPlayoffIdOrderByRoundIndexAsc(UUID playoffId);
}
