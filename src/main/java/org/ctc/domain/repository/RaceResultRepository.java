package org.ctc.domain.repository;

import org.ctc.domain.model.RaceResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

	@EntityGraph(attributePaths = {"driver"})
	List<RaceResult> findByRaceId(UUID raceId);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByDriverId(UUID driverId);

	Optional<RaceResult> findByRaceIdAndDriverId(UUID raceId, UUID driverId);

	// Phase 61 MIGR-06: post-V6 matchdays.season_id is gone — resolve via matchday.phase.season.id.
	@EntityGraph(attributePaths = {"driver", "race"})
	@Query("SELECT rr FROM RaceResult rr WHERE rr.race.matchday.phase.season.id = :seasonId")
	List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRacePlayoffMatchupIsNull();

	@EntityGraph(attributePaths = {"driver", "race"})
	@Query("SELECT rr FROM RaceResult rr WHERE rr.race.playoffMatchup IS NULL AND rr.race.matchday.phase.season.id IN :seasonIds")
	List<RaceResult> findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(List<UUID> seasonIds);

	/**
	 * D-22: Per-phase REGULAR ranking — 4-step magic-name navigation via Race.matchday.phase.id.
	 */
	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId);

	/**
	 * D-22 magic-naming for 5-step navigation: Race.playoffMatchup.round.playoff.phase.id.
	 * If Spring Data fails at boot with PropertyReferenceException, replace with the JPQL @Query below.
	 *
	 * Fallback (Pitfall 1 — uncomment + delete the magic-name declaration above if boot fails):
	 * {@code @Query("SELECT rr FROM RaceResult rr JOIN rr.race r JOIN r.playoffMatchup pm " +
	 *   "JOIN pm.round pr JOIN pr.playoff p WHERE p.phase.id = :phaseId")}
	 * {@code @Param("phaseId") UUID phaseId}
	 */
	@EntityGraph(attributePaths = {"driver", "race"})
	List<RaceResult> findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId);
}
