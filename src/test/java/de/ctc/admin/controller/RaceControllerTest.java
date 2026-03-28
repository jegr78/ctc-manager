package de.ctc.admin.controller;

import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.Season;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Test
    void shouldShowRaceDetail() throws Exception {
        var season = seasonRepository.save(new Season("Race Detail Season"));
        var matchday = matchdayRepository.save(new Matchday(season, "RD Matchday", 1));
        var home = teamRepository.save(new Team("Home Racing", "RDH"));
        var away = teamRepository.save(new Team("Away Racing", "RDA"));
        var race = raceRepository.save(new Race(matchday, home, away));

        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("race"));
    }
}
