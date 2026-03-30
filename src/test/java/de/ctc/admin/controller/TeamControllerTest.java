package de.ctc.admin.controller;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private RaceLineupRepository raceLineupRepository;

    @Test
    void shouldListTeams() throws Exception {
        mockMvc.perform(get("/admin/teams"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/teams"))
                .andExpect(model().attributeExists("parentTeams"));
    }

    @Test
    void shouldCreateTeam() throws Exception {
        mockMvc.perform(post("/admin/teams/save")
                        .param("name", "MockMvc Racing")
                        .param("shortName", "MVR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"));

        var saved = teamRepository.findByShortName("MVR");
        assertTrue(saved.isPresent());
        assertEquals("MockMvc Racing", saved.get().getName());
    }

    @Test
    void shouldShowTeamDetail() throws Exception {
        var team = teamRepository.save(new Team("Detail Racing", "DTL"));

        mockMvc.perform(get("/admin/teams/" + team.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-detail"))
                .andExpect(model().attributeExists("team", "seasons", "seasonDriverGroups", "seasonsWithoutDrivers"));
    }

    @Test
    void shouldShowTeamDetailWithGroupedDrivers() throws Exception {
        var parent = teamRepository.save(new Team("Grouped Racing", "GRP"));
        var sub = teamRepository.save(new Team("Grouped Racing A", "GRP A", parent));

        var season = seasonRepository.findByActiveTrue().orElseThrow();
        var matchday = matchdayRepository.save(new Matchday(season, "GRP MD", 99));
        var match = new Match();
        match.setMatchday(matchday);
        match.setHomeTeam(parent);
        match.setAwayTeam(sub);
        matchRepository.save(match);
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);

        var d1 = driverRepository.save(new Driver("grp_driver1", "GRP Driver 1"));
        var d2 = driverRepository.save(new Driver("grp_driver2", "GRP Driver 2"));
        raceLineupRepository.save(new RaceLineup(race, d1, parent));
        raceLineupRepository.save(new RaceLineup(race, d2, sub));

        mockMvc.perform(get("/admin/teams/" + parent.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("seasonDriverGroups"))
                .andExpect(model().attribute("seasonDriverGroups",
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void shouldShowTeamDetailWithEmptyDriverGroups() throws Exception {
        var team = teamRepository.save(new Team("Empty Racing", "EMP"));

        mockMvc.perform(get("/admin/teams/" + team.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("seasonDriverGroups",
                        org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void shouldIncludeSubTeamDriversInParentDetail() throws Exception {
        var parent = teamRepository.save(new Team("Parent Inc", "PIN"));
        var sub = teamRepository.save(new Team("Parent Inc A", "PIN A", parent));

        var season = seasonRepository.findByActiveTrue().orElseThrow();
        var matchday = matchdayRepository.save(new Matchday(season, "PIN MD", 98));
        var match = new Match();
        match.setMatchday(matchday);
        match.setHomeTeam(parent);
        match.setAwayTeam(sub);
        matchRepository.save(match);
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);

        var d1 = driverRepository.save(new Driver("pin_parent1", "Parent Driver"));
        var d2 = driverRepository.save(new Driver("pin_sub1", "Sub Driver"));
        raceLineupRepository.save(new RaceLineup(race, d1, parent));
        raceLineupRepository.save(new RaceLineup(race, d2, sub));

        mockMvc.perform(get("/admin/teams/" + parent.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pin_parent1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pin_sub1")));
    }

    @Test
    void shouldShowOnlyOwnDriversForSubTeam() throws Exception {
        var parent = teamRepository.save(new Team("Own Inc", "OWN"));
        var sub = teamRepository.save(new Team("Own Inc A", "OWN A", parent));

        var season = seasonRepository.findByActiveTrue().orElseThrow();
        var matchday = matchdayRepository.save(new Matchday(season, "OWN MD", 97));
        var match = new Match();
        match.setMatchday(matchday);
        match.setHomeTeam(parent);
        match.setAwayTeam(sub);
        matchRepository.save(match);
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);

        var d1 = driverRepository.save(new Driver("own_parent1", "Parent Only"));
        var d2 = driverRepository.save(new Driver("own_sub1", "Sub Only"));
        raceLineupRepository.save(new RaceLineup(race, d1, parent));
        raceLineupRepository.save(new RaceLineup(race, d2, sub));

        mockMvc.perform(get("/admin/teams/" + sub.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("own_sub1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("own_parent1"))));
    }

    @Test
    void shouldDeleteTeam() throws Exception {
        var team = teamRepository.save(new Team("Delete Racing", "DLR"));

        mockMvc.perform(post("/admin/teams/" + team.getId() + "/delete"))
                .andExpect(status().is3xxRedirection());

        assertFalse(teamRepository.findById(team.getId()).isPresent());
    }
}
