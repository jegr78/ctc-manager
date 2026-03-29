package de.ctc.admin.controller;

import de.ctc.TestHelper;
import de.ctc.domain.model.Matchday;
import de.ctc.domain.repository.MatchdayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MatchdayControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MatchdayRepository matchdayRepository;
    @Autowired private TestHelper testHelper;

    @Test
    void shouldShowMatchdayDetail() throws Exception {
        var season = testHelper.createSeason("MD Detail Season");
        var matchday = matchdayRepository.save(new Matchday(season, "Test Matchday", 1));

        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-detail"))
                .andExpect(model().attributeExists("matchday"));
    }
}
