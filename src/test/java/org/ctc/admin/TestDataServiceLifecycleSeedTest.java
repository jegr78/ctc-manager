package org.ctc.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class TestDataServiceLifecycleSeedTest {

	@Autowired
	private TestDataService testDataService;

	@Autowired
	private TeamRepository teamRepository;

	@Test
	void givenExistingDevSeedTAlf_whenSeedFullMatchdayLifecycle_thenTAlcIsCreatedWithoutCollision() {
		// given — DevDataSeeder (@Profile dev) already created the T-ALF team at context start
		Team existingDevSeed = teamRepository.findByShortName("T-ALF").orElseThrow();

		// when
		testDataService.seedFullMatchdayLifecycle();

		// then
		assertThat(teamRepository.findByShortName("T-ALF"))
				.as("dev-seed T-ALF must remain a single row after lifecycle-seed runs")
				.isPresent()
				.get()
				.extracting(Team::getId)
				.isEqualTo(existingDevSeed.getId());
		assertThat(teamRepository.findByShortName("T-ALC"))
				.as("lifecycle-seed must register under T-ALC (its isolated short-name)")
				.isPresent();
	}

	@Test
	void whenSeedFullMatchdayLifecycle_thenTAlcTeamIsCreated() {
		// when
		testDataService.seedFullMatchdayLifecycle();

		// then
		assertThat(teamRepository.findByShortName("T-ALC"))
				.as("lifecycle-seed creates the T-ALC team")
				.isPresent();
	}
}
