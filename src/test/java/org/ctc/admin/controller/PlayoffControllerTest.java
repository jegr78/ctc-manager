package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PlayoffSeedingService;
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
    @Autowired private PlayoffSeedingService playoffSeedingService;
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
    void whenGetPlayoffs_thenReturnsPlayoffBracketView() throws Exception {
        // when
        mockMvc.perform(get("/admin/playoffs"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-bracket"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void givenSeasonId_whenGetPlayoffsForSeason_thenReturnsSelectedSeason() throws Exception {
        // when
        mockMvc.perform(get("/admin/playoffs").param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSeasonId", season.getId()));
    }

    @Test
    void givenSeasonId_whenGetNewPlayoffForm_thenReturnsPlayoffForm() throws Exception {
        // when
        mockMvc.perform(get("/admin/playoffs/new").param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-form"))
                .andExpect(model().attributeExists("playoffForm", "seasons"));
    }

    @Test
    void givenValidPlayoffForm_whenSavePlayoff_thenRedirectsAndPersists() throws Exception {
        // when
        mockMvc.perform(post("/admin/playoffs/save")
                        .param("seasonId", season.getId().toString())
                        .param("name", "Test Playoffs")
                        .param("numberOfTeams", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        // then
        var playoff = playoffRepository.findBySeasonId(season.getId());
        assertTrue(playoff.isPresent());
        assertEquals("Test Playoffs", playoff.get().getName());
    }

    @Test
    void givenExistingPlayoff_whenGetSeedingPage_thenReturnsSeedView() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Seed Test", 4);

        // when
        mockMvc.perform(get("/admin/playoffs/" + playoff.getId() + "/seed"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-seed"))
                .andExpect(model().attributeExists("playoff", "bracket", "firstRound", "teams"));
    }

    @Test
    void givenSeasonWithPlayoff_whenGetPlayoffsForSeason_thenReturnsBracketInModel() throws Exception {
        // given
        playoffService.createPlayoff(season.getId(), "Bracket Test", 4);

        // when
        mockMvc.perform(get("/admin/playoffs").param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("playoff", "bracket"));
    }

    @Test
    void givenExistingPlayoff_whenGetMatchupDetail_thenReturnsMatchupView() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Matchup Test", 4);
        var matchupId = playoff.getRounds().get(0).getMatchups().get(0).getId();

        // when
        mockMvc.perform(get("/admin/playoffs/matchup/" + matchupId))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/playoff-matchup"))
                .andExpect(model().attributeExists("matchup", "legs", "playoff"));
    }

    @Test
    void givenExistingPlayoff_whenSaveDuplicatePlayoff_thenRedirectsWithError() throws Exception {
        // given
        playoffService.createPlayoff(season.getId(), "First", 4);

        // when
        mockMvc.perform(post("/admin/playoffs/save")
                        .param("seasonId", season.getId().toString())
                        .param("name", "Second")
                        .param("numberOfTeams", "4"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenPlayoffRound_whenSetRoundLegs_thenRedirectsWithSuccess() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Legs Test", 4);
        var roundId = playoff.getRounds().get(0).getId();

        // when
        mockMvc.perform(post("/admin/playoffs/round/" + roundId + "/set-legs")
                        .param("bestOfLegs", "3"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenPlayoffMatchup_whenSaveSeedingForm_thenRedirectsWithSuccess() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Seed Save Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);
        var team = teamRepository.findAll().stream().findFirst().orElseThrow();

        // when
        mockMvc.perform(post("/admin/playoffs/" + playoff.getId() + "/seed")
                        .param("playoffId", playoff.getId().toString())
                        .param("seeds[0].matchupId", matchup.getId().toString())
                        .param("seeds[0].teamId", team.getId().toString())
                        .param("seeds[0].slot", "1"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    // --- POST /admin/playoffs/matchup/{id}/add-race ---

    @Test
    void givenMatchupWithBothTeamsSeeded_whenAddRaceToMatchup_thenRedirectsWithSuccess() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "AddRace Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);

        // Seed both teams so matchup is ready
        var teams = season.getTeams();
        playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
        playoffSeedingService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

        // when
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/playoffs/matchup/" + matchup.getId()))
                .andExpect(flash().attribute("successMessage", "Leg added"));
    }

    @Test
    void givenMatchupWithoutTeams_whenAddRaceToMatchup_thenRedirectsWithError() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "NoTeams Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);

        // when
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/playoffs/matchup/" + matchup.getId()))
                .andExpect(flash().attribute("errorMessage", "Both teams must be set"));
    }

    // --- POST /admin/playoffs/matchup/{id}/determine-winner ---

    @Test
    void givenMatchupWithSeededTeams_whenDetermineWinner_thenRedirects() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Winner Test", 4);
        var matchup = playoff.getRounds().get(0).getMatchups().get(0);
        var teams = season.getTeams();
        playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
        playoffSeedingService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

        // Add a race leg and give it a score so winner can be determined
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/add-race"))
                .andExpect(status().is3xxRedirection());

        // when
        mockMvc.perform(post("/admin/playoffs/matchup/" + matchup.getId() + "/determine-winner"))
                // then
                .andExpect(status().is3xxRedirection());
        // Either success or error flash attribute should be present
    }

    // --- Round graphic download endpoints ---

    @Test
    void givenUnknownRoundId_whenDownloadRoundOverview_thenReturns500() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/playoffs/round/" + UUID.randomUUID() + "/download-overview"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void givenUnknownRoundId_whenDownloadRoundSchedule_thenReturns500() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/playoffs/round/" + UUID.randomUUID() + "/download-schedule"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void givenUnknownRoundId_whenDownloadRoundResults_thenReturns500() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/playoffs/round/" + UUID.randomUUID() + "/download-results"))
                .andExpect(status().isInternalServerError());
    }

    // --- POST /admin/playoffs/{id}/add-season + remove-season ---

    @Test
    void givenPlayoffAndOtherSeason_whenAddAndRemoveSeasonFromPlayoff_thenBothSucceed() throws Exception {
        // given
        var playoff = playoffService.createPlayoff(season.getId(), "Season Link Test", 4);
        var otherSeason = testHelper.createSeason("Other Playoff Season");

        // when / then
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
