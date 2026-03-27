package de.ctc.admin.controller;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.PlayoffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PlayoffControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private SeasonDriverRepository seasonDriverRepository;
    @Autowired private PlayoffRepository playoffRepository;
    @Autowired private PlayoffService playoffService;
    @Autowired private MatchdayRepository matchdayRepository;

    private Season season;

    @BeforeEach
    void setUp() {
        season = new Season("Playoff Controller Test " + UUID.randomUUID().toString().substring(0, 8));
        season.setActive(true);
        season = seasonRepository.save(season);

        String[] names = {"AA", "BB", "CC", "DD"};
        for (String name : names) {
            var team = teamRepository.save(new Team(name + " Racing", name));
            var driver = driverRepository.save(new Driver(name.toLowerCase() + "_test", name + " Driver"));
            seasonDriverRepository.save(new SeasonDriver(season, driver, team));
        }
    }

    @Test
    void shouldShowPlayoffsPage() throws Exception {
        mockMvc.perform(get("/admin/playoffs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-bracket"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void shouldShowPlayoffsForSeason() throws Exception {
        mockMvc.perform(get("/admin/playoffs").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSeasonId", season.getId()));
    }

    @Test
    void shouldShowNewPlayoffForm() throws Exception {
        mockMvc.perform(get("/admin/playoffs/new").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-form"))
                .andExpect(model().attributeExists("playoffForm", "seasons"));
    }

    @Test
    void shouldCreatePlayoff() throws Exception {
        mockMvc.perform(post("/admin/playoffs/save")
                        .param("seasonId", season.getId().toString())
                        .param("name", "Test Playoffs")
                        .param("bestOfLegs", "2")
                        .param("numberOfTeams", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        var playoff = playoffRepository.findBySeasonId(season.getId());
        assertTrue(playoff.isPresent());
        assertEquals("Test Playoffs", playoff.get().getName());
    }

    @Test
    void shouldShowSeedingPage() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Seed Test", 2, 4);

        mockMvc.perform(get("/admin/playoffs/" + playoff.getId() + "/seed"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-seed"))
                .andExpect(model().attributeExists("playoff", "bracket", "firstRound", "teams"));
    }

    @Test
    void shouldShowBracketWithPlayoff() throws Exception {
        playoffService.createPlayoff(season.getId(), "Bracket Test", 2, 4);

        mockMvc.perform(get("/admin/playoffs").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("playoff", "bracket"));
    }

    @Test
    void shouldShowMatchupDetail() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Matchup Test", 2, 4);
        var matchupId = playoff.getRounds().get(0).getMatchups().get(0).getId();

        // Create a matchday for the matchup page
        matchdayRepository.save(new Matchday(season, "HF Hinspiel", 1));

        mockMvc.perform(get("/admin/playoffs/matchup/" + matchupId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-matchup"))
                .andExpect(model().attributeExists("matchup", "legs", "playoff", "matchdays"));
    }

    @Test
    void shouldRejectDuplicatePlayoff() throws Exception {
        playoffService.createPlayoff(season.getId(), "First", 2, 4);

        mockMvc.perform(post("/admin/playoffs/save")
                        .param("seasonId", season.getId().toString())
                        .param("name", "Second")
                        .param("bestOfLegs", "2")
                        .param("numberOfTeams", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
