package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffMatchup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, UUID> {

    List<PlayoffMatchup> findByRoundIdOrderByBracketPositionAsc(UUID roundId);

    List<PlayoffMatchup> findByRoundPlayoffId(UUID playoffId);
}
