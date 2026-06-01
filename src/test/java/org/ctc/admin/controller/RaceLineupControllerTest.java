package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RaceLineupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestHelper testHelper;

	@Autowired
	private RaceLineupRepository raceLineupRepository;

	@Autowired
	private RaceResultRepository raceResultRepository;

	private SeasonFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = testHelper.createFullSeasonFixture("Test_Lineup");
	}

	@Test
	void givenExistingRace_whenGetLineupPage_thenReturnsLineupView() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + fixture.race().getId() + "/lineup"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-lineup"))
				.andExpect(model().attributeExists("race", "teamEntries", "driverAssignments",
						"guestLineups", "allDrivers"));
	}

	@Test
	void givenGuestParam_whenSaveLineup_thenPersistsGuestEntryWithGuestFlag() throws Exception {
		// given — a driver NOT on the season roster
		var guest = testHelper.createDriver("Test_lineup_guest", "Test Lineup Guest");

		// when
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
						.param("guest_" + guest.getId(), fixture.homeTeam().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/lineup"));

		// then
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(1, lineups.size());
		assertTrue(lineups.get(0).isGuest());
		assertEquals(guest.getId(), lineups.get(0).getDriver().getId());
	}

	@Test
	void givenSavedGuestWithResult_whenSaveWithoutGuest_thenGuestAndResultRemoved() throws Exception {
		// given — a guest lineup entry plus a matching result for the race
		var guest = testHelper.createDriver("Test_lineup_guest_rm", "Test Lineup Guest Remove");
		raceLineupRepository.save(new RaceLineup(fixture.race(), guest, fixture.homeTeam(), true));
		raceResultRepository.save(new RaceResult(fixture.race(), guest, 1, 1, false));

		// when — re-save the lineup without the guest param
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup"))
				.andExpect(status().is3xxRedirection());

		// then — both the guest lineup and its orphan result are gone
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(0, lineups.size());
		assertTrue(raceResultRepository.findByRaceIdAndDriverId(fixture.race().getId(), guest.getId()).isEmpty());
	}

	@Test
	void givenExistingGuest_whenReSavedToOtherTeam_thenMovesWithoutUniqueViolation() throws Exception {
		// given — same driver already a guest on the home team
		var guest = testHelper.createDriver("Test_lineup_guest_mv", "Test Lineup Guest Move");
		raceLineupRepository.save(new RaceLineup(fixture.race(), guest, fixture.homeTeam(), true));

		// when — re-save the same driver as a guest for the away team (delete + re-insert same race/driver)
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
						.param("guest_" + guest.getId(), fixture.awayTeam().getId().toString()))
				.andExpect(status().is3xxRedirection());

		// then — exactly one guest entry, now on the away team, no constraint violation
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(1, lineups.size());
		assertTrue(lineups.get(0).isGuest());
		assertEquals(fixture.awayTeam().getId(), lineups.get(0).getTeam().getId());
	}

	@Test
	void givenMalformedGuestKey_whenSaveLineup_thenSkippedWithErrorAndNoCrash() throws Exception {
		// when — a guest_ key whose driver segment is not a UUID
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
						.param("guest_not-a-uuid", fixture.homeTeam().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));

		// then — nothing persisted, no 500
		assertEquals(0, raceLineupRepository.findByRaceId(fixture.race().getId()).size());
	}

	@Test
	void givenGuestRowWithoutTeam_whenSaveLineup_thenReportedAsSkipped() throws Exception {
		// given — a guest driver entered but no fielding team chosen (empty value)
		var guest = testHelper.createDriver("Test_lineup_guest_nt", "Test Lineup Guest NoTeam");

		// when
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
						.param("guest_" + guest.getId(), ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));

		// then — the guest was not silently persisted
		assertEquals(0, raceLineupRepository.findByRaceId(fixture.race().getId()).size());
	}

	@Test
	void givenTwoDriversAssigned_whenSaveLineup_thenRedirectsAndPersistsTwoEntries() throws Exception {
		// given
		var driver1 = testHelper.createDriver("Test_lineup_d1", "Test Lineup Driver 1");
		var driver2 = testHelper.createDriver("Test_lineup_d2", "Test Lineup Driver 2");
		testHelper.createSeasonDriver(fixture.season(), driver1, fixture.homeTeam());
		testHelper.createSeasonDriver(fixture.season(), driver2, fixture.awayTeam());

		// when
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
						.param("driver_" + driver1.getId(), fixture.homeTeam().getId().toString())
						.param("driver_" + driver2.getId(), fixture.awayTeam().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/lineup"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(2, lineups.size());
	}

	@Test
	void givenNoDriverParams_whenSaveLineup_thenRedirectsAndPersistsZeroEntries() throws Exception {
		// when
		// POST with no driver_ params should save 0 entries
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/lineup"));

		// then
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(0, lineups.size());
	}

	@Test
	void givenExistingLineup_whenSaveEmptyLineup_thenClearsAllEntries() throws Exception {
		// given
		var driver = testHelper.createDriver("Test_lineup_replace", "Test Lineup Replace");
		testHelper.createSeasonDriver(fixture.season(), driver, fixture.homeTeam());

		// First save
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup")
				.param("driver_" + driver.getId(), fixture.homeTeam().getId().toString()));

		// when
		// Second save without driver — should clear lineup
		mockMvc.perform(post("/admin/races/" + fixture.race().getId() + "/lineup"))
				.andExpect(status().is3xxRedirection());

		// then
		var lineups = raceLineupRepository.findByRaceId(fixture.race().getId());
		assertEquals(0, lineups.size());
	}
}
