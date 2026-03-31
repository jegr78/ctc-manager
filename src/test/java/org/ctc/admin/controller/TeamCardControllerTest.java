package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TeamCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestHelper testHelper;

    @Test
    void shouldShowTeamCardsPage() throws Exception {
        mockMvc.perform(get("/admin/tools/team-cards"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void shouldShowTeamCardsPageWithSeasonFilter() throws Exception {
        var season = testHelper.createSeason("Test_TeamCard Season");
        season.setActive(true);

        mockMvc.perform(get("/admin/tools/team-cards")
                        .param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons", "season", "cardStates", "selectedSeasonId"));
    }

    @Test
    void shouldShowTeamCardsWithActiveSeason() throws Exception {
        // When no seasonId param is given, the active season is auto-selected
        var season = testHelper.createSeason("Test_TeamCard Active Season");
        season.setActive(true);

        mockMvc.perform(get("/admin/tools/team-cards"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons"));
    }
}
