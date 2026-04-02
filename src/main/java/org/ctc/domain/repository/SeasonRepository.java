package org.ctc.domain.repository;

import org.ctc.domain.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    Optional<Season> findByActiveTrue();

    List<Season> findBySeasonTeamsTeamId(UUID teamId);

    List<Season> findByYearAndNumber(int year, int number);
}
