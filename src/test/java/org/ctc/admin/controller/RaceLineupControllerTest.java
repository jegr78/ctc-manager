package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.repository.RaceLineupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                .andExpect(model().attributeExists("race", "teamEntries", "driverAssignments"));
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
