package de.ctc.admin.controller;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StandingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;

    private Season activeSeason;
    private Season inactiveSeason;

    @BeforeEach
    void setUp() {
        // Deactivate any existing active seasons from other tests / Flyway data
        seasonRepository.findByActiveTrue().ifPresent(s -> {
            s.setActive(false);
            seasonRepository.save(s);
        });

        activeSeason = new Season("Standings Active " + UUID.randomUUID().toString().substring(0, 8));
        activeSeason.setActive(true);
        activeSeason = seasonRepository.save(activeSeason);

        inactiveSeason = new Season("Standings Inactive " + UUID.randomUUID().toString().substring(0, 8));
        inactiveSeason.setActive(false);
        inactiveSeason = seasonRepository.save(inactiveSeason);

        var teamA = teamRepository.save(new Team("Alpha Racing", "ALP"));
        var teamB = teamRepository.save(new Team("Bravo Racing", "BRV"));
        activeSeason.getTeams().add(teamA);
        activeSeason.getTeams().add(teamB);
        seasonRepository.save(activeSeason);
    }

    @Test
    void shouldShowStandingsForActiveSeason() throws Exception {
        mockMvc.perform(get("/admin/standings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/standings"))
                .andExpect(model().attributeExists("seasons", "standings", "driverRanking", "selectedSeason"))
                .andExpect(model().attribute("selectedSeason", hasProperty("id", is(activeSeason.getId()))));
    }

    @Test
    void shouldShowStandingsForSpecificSeason() throws Exception {
        mockMvc.perform(get("/admin/standings").param("seasonId", inactiveSeason.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/standings"))
                .andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
                .andExpect(model().attribute("selectedSeason", hasProperty("id", is(inactiveSeason.getId()))))
                .andExpect(model().attribute("selectedSeasonId", inactiveSeason.getId().toString()));
    }

    @Test
    void shouldShowAlltimeStandings() throws Exception {
        mockMvc.perform(get("/admin/standings").param("seasonId", "alltime"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/standings"))
                .andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
                .andExpect(model().attribute("isAlltime", true))
                .andExpect(model().attribute("selectedSeasonId", "alltime"))
                .andExpect(model().attributeDoesNotExist("selectedSeason"));
    }

    @Test
    void shouldShowSelectSeasonStateWhenNoActiveSeasonAndNoParam() throws Exception {
        // Deactivate the active season so no fallback exists
        activeSeason.setActive(false);
        seasonRepository.save(activeSeason);

        mockMvc.perform(get("/admin/standings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/standings"))
                .andExpect(model().attributeExists("seasons"))
                .andExpect(model().attribute("isAlltime", false))
                .andExpect(model().attributeDoesNotExist("standings", "driverRanking", "selectedSeason"));
    }

    @Test
    void shouldShowStandingsForSwissSeason() throws Exception {
        var swissSeason = new Season("Swiss Season " + UUID.randomUUID().toString().substring(0, 8));
        swissSeason.setFormat(SeasonFormat.SWISS);
        swissSeason = seasonRepository.save(swissSeason);

        mockMvc.perform(get("/admin/standings").param("seasonId", swissSeason.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/standings"))
                .andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
                .andExpect(model().attribute("selectedSeason", hasProperty("format", is(SeasonFormat.SWISS))));
    }

    @Test
    void shouldAlwaysPopulateSeasonsList() throws Exception {
        mockMvc.perform(get("/admin/standings"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("seasons", hasSize(greaterThanOrEqualTo(2))));
    }
}
