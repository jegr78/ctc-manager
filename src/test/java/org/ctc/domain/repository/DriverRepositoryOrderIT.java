package org.ctc.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * QUAL-01: verifies {@link DriverRepository#findDetailById(UUID)} returns the driver with its
 * {@code seasonDrivers} collection ordered by ascending {@code season.year} then {@code season.number},
 * enforced by SQL {@code ORDER BY} in the JPQL fetch query.
 */
@CtcDevSpringBootContext
@Transactional
@Tag("integration")
class DriverRepositoryOrderIT {

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private SeasonDriverRepository seasonDriverRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void givenMultiSeasonDriver_whenFindDetailById_thenSeasonDriversAreOrderedByYearAsc() {
		// given — insert in reverse year order to confirm ordering is DB-driven, not insertion-driven
		var team = teamRepository.save(new Team("Phase83-Test-OrderIT-T", "P83T"));
		var season2025 = seasonRepository.save(new Season("Phase83-Test-Order-2025", 2025, 1));
		var season2023 = seasonRepository.save(new Season("Phase83-Test-Order-2023", 2023, 1));
		var season2024 = seasonRepository.save(new Season("Phase83-Test-Order-2024", 2024, 1));
		var driver = driverRepository.save(new Driver("P83T_OrderIT_Driver", "Phase83-Test-Order-Driver"));
		seasonDriverRepository.save(new SeasonDriver(season2025, driver, team));
		seasonDriverRepository.save(new SeasonDriver(season2023, driver, team));
		seasonDriverRepository.save(new SeasonDriver(season2024, driver, team));
		entityManager.flush();
		entityManager.clear();

		// when
		var reloaded = driverRepository.findDetailById(driver.getId()).orElseThrow();
		List<SeasonDriver> ordered = reloaded.getSeasonDrivers();

		// then — SQL ORDER BY enforces ascending year regardless of insertion order
		assertThat(ordered).hasSize(3);
		assertThat(ordered.get(0).getSeason().getYear()).isEqualTo(2023);
		assertThat(ordered.get(1).getSeason().getYear()).isEqualTo(2024);
		assertThat(ordered.get(2).getSeason().getYear()).isEqualTo(2025);
	}

	@Test
	void givenSplitYearSeasons_whenFindDetailById_thenSeasonDriversAreOrderedByYearThenNumber() {
		// given — two seasons in the same year, inserted with the higher number first
		var team = teamRepository.save(new Team("Phase83-Test-OrderIT-Split-T", "P83S"));
		var season2024_2 = seasonRepository.save(new Season("Phase83-Test-Order-2024-2", 2024, 2));
		var season2024_1 = seasonRepository.save(new Season("Phase83-Test-Order-2024-1", 2024, 1));
		var driver = driverRepository.save(new Driver("P83T_OrderIT_SplitDriver", "Phase83-Test-Order-Split-Driver"));
		seasonDriverRepository.save(new SeasonDriver(season2024_2, driver, team));
		seasonDriverRepository.save(new SeasonDriver(season2024_1, driver, team));
		entityManager.flush();
		entityManager.clear();

		// when
		var reloaded = driverRepository.findDetailById(driver.getId()).orElseThrow();
		List<SeasonDriver> ordered = reloaded.getSeasonDrivers();

		// then — secondary sort by season.number ASC within the same year
		assertThat(ordered).hasSize(2);
		assertThat(ordered.get(0).getSeason().getNumber()).isEqualTo(1);
		assertThat(ordered.get(1).getSeason().getNumber()).isEqualTo(2);
	}
}
