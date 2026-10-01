package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class PhaseWithoutScoringPagesIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private TestHelper testHelper;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private SeasonFixture fixture;
	private SeasonPhase phase;
	private Driver driver;

	@BeforeEach
	void createScoredMatchInPhaseWithoutScoring() {
		fixture = testHelper.createFullSeasonFixture("Test_NoScoringPage_" + id);
		phase = fixture.matchday().getPhase();
		phaseTeamRepository.save(new PhaseTeam(phase, fixture.homeTeam()));
		phaseTeamRepository.save(new PhaseTeam(phase, fixture.awayTeam()));
		driver = testHelper.createDriver("Test_NoScoringPage_" + id, "Test NoScoring Driver");
		testHelper.createSeasonDriver(fixture.season(), driver, fixture.homeTeam());
		fixture.match().setHomeScore(30);
		fixture.match().setAwayScore(10);
		phase.setMatchScoring(null);
		phase.setRaceScoring(null);
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void givenPhaseWithoutScoring_whenStandingsShown_thenTheyRenderWithTheResult() throws Exception {
		mockMvc.perform(get("/admin/standings").param("phase", phase.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(fixture.homeTeam().getShortName())));
	}

	@Test
	void givenPhaseWithoutRaceScoring_whenResultsFormShown_thenItRendersWithANotice() throws Exception {
		// when
		var response = new MvcResult[1];
		assertThatCode(() -> response[0] = mockMvc.perform(get("/admin/races/" + fixture.race().getId() + "/results")).andReturn())
				.as("rendering the results form").doesNotThrowAnyException();

		// then
		assertThat(response[0].getResponse().getStatus()).as("status of the results form").isEqualTo(200);
		assertThat(response[0].getResponse().getContentAsString()).as("results form")
				.contains("This phase has no race scoring")
				.contains("racePoints = racePoints || [];", "qualiPoints = qualiPoints || [];", "flPoints = flPoints || 0;");
	}

	@Test
	void givenStoredResultsInPhaseWithoutRaceScoring_whenAnEmptyResultListIsSaved_thenTheResultsAreCleared() throws Exception {
		// given
		var race = raceRepository.findById(fixture.race().getId()).orElseThrow();
		race.getResults().add(new RaceResult(race, entityManager.find(Driver.class, driver.getId()), 1, 1, false));
		entityManager.flush();
		entityManager.clear();

		// when
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/results"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("successMessage"));

		// then
		entityManager.clear();
		assertThat(raceRepository.findById(fixture.race().getId()).orElseThrow().getResults())
				.as("results after clearing them without race scoring").isEmpty();
	}

	@Test
	void givenPhaseWithoutRaceScoring_whenResultsSaved_thenRejectedWithAMessageAndNothingStored() throws Exception {
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/results")
						.param("results[0].driverId", driver.getId().toString())
						.param("results[0].driverPsnId", driver.getPsnId())
						.param("results[0].position", "1")
						.param("results[0].qualiPosition", "1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/results"))
				.andExpect(flash().attribute("errorMessage",
						"This phase has no race scoring. Set one on the phase before entering results."));
		entityManager.clear();
		assertThat(raceRepository.findById(fixture.race().getId()).orElseThrow().getResults())
				.as("results stored without race scoring").isEmpty();
	}
}
