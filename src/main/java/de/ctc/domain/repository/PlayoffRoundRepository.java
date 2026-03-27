package de.ctc.domain.repository;

import de.ctc.domain.model.PlayoffRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayoffRoundRepository extends JpaRepository<PlayoffRound, UUID> {

    List<PlayoffRound> findByPlayoffIdOrderByRoundIndexAsc(UUID playoffId);
}
