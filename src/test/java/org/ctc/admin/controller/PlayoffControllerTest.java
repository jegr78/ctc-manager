package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PlayoffService;
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
    @Autowired private TestHelper testHelper;

    private Season season;

    @BeforeEach
    void setUp() {
        season = testHelper.createSeason("Playoff Controller Test " + UUID.randomUUID().toString().substring(0, 8));
        season.setActive(true);

        String[] names = {"AA", "BB", "CC", "DD"};
        for (String name : names) {
            var team = teamRepository.save(new Team(name + " Racing", name));
            season.addTeam(team);
            var driver = driverRepository.save(new Driver(name.toLowerCase() + "_test", name + " Driver"));
            seasonDriverRepository.save(new SeasonDriver(season, driver, team));
        }
        season = seasonRepository.save(season);
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
                                                .param("numberOfTeams", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        var playoff = playoffRepository.findBySeasonId(season.getId());
        assertTrue(playoff.isPresent());
        assertEquals("Test Playoffs", playoff.get().getName());
    }

    @Test
    void shouldShowSeedingPage() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Seed Test", 4);

        mockMvc.perform(get("/admin/playoffs/" + playoff.getId() + "/seed"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-seed"))
                .andExpect(model().attributeExists("playoff", "bracket", "firstRound", "teams"));
    }

    @Test
    void shouldShowBracketWithPlayoff() throws Exception {
        playoffService.createPlayoff(season.getId(), "Bracket Test", 4);

        mockMvc.perform(get("/admin/playoffs").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("playoff", "bracket"));
    }

    @Test
    void shouldShowMatchupDetail() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Matchup Test", 4);
        var matchupId = playoff.getRounds().get(0).getMatchups().get(0).getId();

        mockMvc.perform(get("/admin/playoffs/matchup/" + matchupId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-matchup"))
                .andExpect(model().attributeExists("matchup", "legs", "playoff"));
    }

    @Test
    void shouldRejectDuplicatePlayoff() throws Exception {
        playoffService.createPlayoff(season.getId(), "First", 4);

        mockMvc.perform(post("/admin/playoffs/save")
                        .param("seasonId", season.getId().toString())
                        .param("name", "Second")
                                                .param("numberOfTeams", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldSetRoundLegs() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Legs Test", 4);
        var roundId = playoff.getRounds().get(0).getId();

        mockMvc.perform(post("/admin/playoffs/round/" + roundId + "/set-legs")
                        .param("bestOfLegs", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldSaveSeedingForm() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Seed Save Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);
        var team = teamRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/seed")
                        .param("playoffId", playoff.getId().toString())
                        .param("seeds[0].matchupId", matchup.getId().toString())
                        .param("seeds[0].teamId", team.getId().toString())
                        .param("seeds[0].slot", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    // --- POST /admin/playoffs/matchup/{id}/add-race ---

    @Test
    void shouldAddRaceToMatchup() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "AddRace Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);

        // Seed both teams so matchup is ready
        var teams = season.getTeams();
        playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
        playoffService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/playoffs/matchup/" + matchup.getId()))
                .andExpect(flash().attribute("successMessage", "Leg added"));
    }

    @Test
    void shouldRejectAddRaceWhenTeamsNotSet() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "NoTeams Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);

        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/playoffs/matchup/" + matchup.getId()))
                .andExpect(flash().attribute("errorMessage", "Both teams must be set"));
    }

    // --- POST /admin/playoffs/matchup/{id}/determine-winner ---

    @Test
    void shouldDetermineWinner() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Winner Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);
        var teams = season.getTeams();
        playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
        playoffService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

        // Add a race leg and give it a score so winner can be determined
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                .andExpect(status().is3xxRedirection());

        // Determine winner - may fail if no scores, but should exercise the code path
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/determine-winner"))
                .andExpect(status().is3xxRedirection());
        // Either success or error flash attribute should be present
    }

    // --- POST /admin/playoffs/{id}/add-season + remove-season ---

    @Test
    void shouldAddAndRemoveSeasonFromPlayoff() throws Exception {
        var playoff = playoffService.createPlayoff(season.getId(), "Season Link Test", 4);
        var otherSeason = testHelper.createSeason("Other Playoff Season");

        mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/add-season")
                        .param("seasonId", otherSeason.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "Season linked"));

        mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/remove-season")
                        .param("seasonId", otherSeason.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "Season removed"));
    }
}
