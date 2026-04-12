package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonDriver;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonDriverRepository extends JpaRepository<SeasonDriver, UUID> {

	@EntityGraph(attributePaths = {"driver", "team"})
	List<SeasonDriver> findBySeasonId(UUID seasonId);

	@EntityGraph(attributePaths = {"driver", "team"})
	List<SeasonDriver> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);

	Optional<SeasonDriver> findBySeasonIdAndDriverId(UUID seasonId, UUID driverId);

	@EntityGraph(attributePaths = {"driver", "team"})
	List<SeasonDriver> findByDriverId(UUID driverId);

	@EntityGraph(attributePaths = {"driver", "team"})
	List<SeasonDriver> findByTeamIdIn(List<UUID> teamIds);
}
