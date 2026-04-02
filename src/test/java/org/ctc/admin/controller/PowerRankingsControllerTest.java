package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PowerRankingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private SeasonTeamRepository seasonTeamRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TestHelper testHelper;

    private Season season;
    private Team teamA;
    private Team teamB;

    @BeforeEach
    void setUp() {
        season = testHelper.createSeason("PR Test Season", 2026, 7);
        teamA = teamRepository.save(new Team("PR Alpha Racing", "PR-ALF"));
        teamA.setPrimaryColor("#FF0000");
        teamA = teamRepository.save(teamA);
        teamB = teamRepository.save(new Team("PR Beta Racing", "PR-BET"));
        teamB.setPrimaryColor("#0000FF");
        teamB = teamRepository.save(teamB);

        season.addTeam(teamA);
        season.addTeam(teamB);
        season = seasonRepository.save(season);

        // Set ratings
        var stA = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), teamA.getId()).orElseThrow();
        stA.setRating(1500);
        seasonTeamRepository.save(stA);
        var stB = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), teamB.getId()).orElseThrow();
        stB.setRating(1400);
        seasonTeamRepository.save(stB);
    }

    @Test
    void whenGetPowerRankings_thenReturnsPageWithSeasonGroups() throws Exception {
        mockMvc.perform(get("/admin/tools/power-rankings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/power-rankings"))
                .andExpect(model().attributeExists("seasonGroups"));
    }

    @Test
    void givenSeasonSelected_whenGetPowerRankings_thenReturnsTeams() throws Exception {
        mockMvc.perform(get("/admin/tools/power-rankings")
                        .param("year", "2026")
                        .param("number", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/power-rankings"))
                .andExpect(model().attributeExists("teams"))
                .andExpect(model().attribute("teams", hasSize(2)))
                .andExpect(model().attribute("selectedYear", 2026))
                .andExpect(model().attribute("selectedNumber", 7));
    }

    @Test
    void givenNonExistentSeason_whenGetPowerRankings_thenReturnsEmptyTeams() throws Exception {
        mockMvc.perform(get("/admin/tools/power-rankings")
                        .param("year", "2099")
                        .param("number", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("teams", empty()));
    }
}
