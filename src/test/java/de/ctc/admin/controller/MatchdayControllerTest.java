package de.ctc.admin.controller;

import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Season;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.SeasonRepository;
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
class MatchdayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Test
    void shouldShowMatchdayDetail() throws Exception {
        var season = seasonRepository.save(new Season("MD Detail Season"));
        var matchday = matchdayRepository.save(new Matchday(season, "Test Matchday", 1));

        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-detail"))
                .andExpect(model().attributeExists("matchday"));
    }
}
