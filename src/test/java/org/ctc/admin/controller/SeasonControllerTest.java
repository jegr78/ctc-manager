package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private SeasonRepository seasonRepository;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private CarRepository carRepository;
	@Autowired
	private TrackRepository trackRepository;
	@Autowired
	private TestHelper testHelper;

	@Test
	void whenGetSeasons_thenReturnsSeasonsView() throws Exception {
		// when
		mockMvc.perform(get("/admin/seasons"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/seasons"))
				.andExpect(model().attributeExists("seasons"));
	}

	@Test
	void whenGetNewSeasonForm_thenReturnsSeasonForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/seasons/new"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/season-form"))
				.andExpect(model().attributeExists("seasonForm"));
	}


	@Test
	void givenSlimForm_whenSaveSeason_thenRedirectsAndSeasonPersistedWithoutScoringFields() throws Exception {
		// when
		mockMvc.perform(post("/admin/seasons/save")
						.param("name", "T-Phase60-Slim Save")
						.param("year", "2027")
						.param("number", "1")
						.param("description", "Slim test")
						.param("active", "true"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons"));

		var saved = seasonRepository.findAll().stream()
				.filter(s -> "T-Phase60-Slim Save".equals(s.getName())).findFirst().orElseThrow();
		assertThat(saved.getYear()).isEqualTo(2027);
	}

	@Test
	void whenGetNewSeasonForm_thenScoringListsAttributesAbsent() throws Exception {
		// The slim form does not need raceScorings/matchScorings model attributes (UI-01)
		mockMvc.perform(get("/admin/seasons/new"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/season-form"))
				.andExpect(model().attributeDoesNotExist("raceScorings"))
				.andExpect(model().attributeDoesNotExist("matchScorings"));
	}

	@Test
	void givenBlankName_whenSaveSeason_thenReturnsFormWithErrors() throws Exception {
		// when: slim form — no scoring params needed
		mockMvc.perform(post("/admin/seasons/save")
						.param("name", ""))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/season-form"));
	}

	@Test
	void givenExistingSeason_whenGetEditForm_thenReturnsSeasonForm() throws Exception {
		// given
		var season = testHelper.createSeason("Edit Test");

		// when
		mockMvc.perform(get("/admin/seasons/" + season.getId() + "/edit"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/season-form"))
				.andExpect(model().attribute("season", hasProperty("name", is("Edit Test"))));
	}

	@Test
	void givenExistingSeason_whenGetSeasonDetail_thenRedirectsToRegularPhaseTab() throws Exception {
		// given: season with auto-bootstrapped REGULAR phase (TestHelper)
		var season = testHelper.createSeason("Detail Test");

		// when / then: D-08 + D-03 redirect to REGULAR phase tab (3xx), NOT direct 200 render
		mockMvc.perform(get("/admin/seasons/" + season.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/phases/*"));
	}

	@Test
	void givenExistingSeason_whenDeleteSeason_thenRedirectsAndRemoves() throws Exception {
		// given
		var season = testHelper.createSeason("Delete Test");

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons"));

		// then
		assertFalse(seasonRepository.findById(season.getId()).isPresent());
	}

	@Test
	void givenSeasonAndTeam_whenAddTeamToSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Add Team Season");
		var team = teamRepository.save(new Team("Add Team Racing", "ATR"));

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/add-team")
						.param("teamId", team.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void givenExistingSeason_whenSaveUpdatedSeason_thenRedirectsAndUpdates() throws Exception {
		// given
		var season = testHelper.createSeason("Update Test");

		// when: UI-01 slim form — no scoring params
		mockMvc.perform(post("/admin/seasons/save")
						.param("id", season.getId().toString())
						.param("name", "Updated Season Name")
						.param("active", "false"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons"));

		// then
		var updated = seasonRepository.findById(season.getId()).orElseThrow();
		assertEquals("Updated Season Name", updated.getName());
	}


	@Test
	void givenSeasonWithTeam_whenRemoveTeamFromSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Remove Team Season");
		var team = teamRepository.save(new Team("Remove Team Racing", "RTR"));
		season.addTeam(team);
		seasonRepository.save(season);

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/remove-team")
						.param("teamId", team.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));
	}


	@Test
	void givenSeasonAndCar_whenAddCarsToSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Add Cars Season");
		var car = carRepository.save(new Car("Test Toyota", "GR86"));

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/cars/add")
						.param("carIds", car.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/edit*"))
				.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void givenSeasonWithCar_whenRemoveCarsFromSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Remove Cars Season");
		var car = carRepository.save(new Car("Test Honda", "NSX-R"));
		season.getCars().add(car);
		seasonRepository.save(season);

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/cars/remove")
						.param("carIds", car.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/edit*"))
				.andExpect(flash().attributeExists("successMessage"));
	}


	@Test
	void givenSeasonAndTrack_whenAddTracksToSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Add Tracks Season");
		var track = trackRepository.save(new Track("Test Fuji Speedway", "JP"));

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/tracks/add")
						.param("trackIds", track.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/edit*"))
				.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void givenSeasonWithTrack_whenRemoveTracksFromSeason_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Remove Tracks Season");
		var track = trackRepository.save(new Track("Test Autopolis", "JP"));
		season.getTracks().add(track);
		seasonRepository.save(season);

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/tracks/remove")
						.param("trackIds", track.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/edit*"))
				.andExpect(flash().attributeExists("successMessage"));
	}


	@Test
	void givenValidTeams_whenReplaceTeam_thenRedirectsWithSuccess() throws Exception {
		// given
		var season = testHelper.createSeason("Replace Test Season");
		var predecessor = teamRepository.save(new Team("Old Team", "OLD_" + java.util.UUID.randomUUID().toString().substring(0, 4)));
		var successor = teamRepository.save(new Team("New Team", "NEW_" + java.util.UUID.randomUUID().toString().substring(0, 4)));
		season.addTeam(predecessor);
		seasonRepository.save(season);

		// when
		mockMvc.perform(post("/admin/seasons/" + season.getId() + "/replace-team")
						.param("predecessorTeamId", predecessor.getId().toString())
						.param("successorTeamId", successor.getId().toString())
						.param("replacedAt", "2026-03-15"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/seasons/" + season.getId()))
				.andExpect(flash().attributeExists("successMessage"));

		// Verify successor is in season and predecessor is replaced
		var updatedSeason = seasonRepository.findById(season.getId()).orElseThrow();
		assertTrue(updatedSeason.containsTeam(successor));
		var stOld = updatedSeason.findSeasonTeam(predecessor).orElseThrow();
		assertTrue(stOld.isReplaced());
	}


	@Test
	void givenSwissSeason_whenGetSwissRoundsPage_thenReturnsSwissView() throws Exception {
		// given
		var season = testHelper.createSeason("Swiss Test Season");
		seasonRepository.save(season);

		// when
		mockMvc.perform(get("/admin/seasons/" + season.getId() + "/swiss"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/swiss-rounds"))
				.andExpect(model().attributeExists("season", "raceScores", "currentRound", "canGenerateNext"));
	}
}
