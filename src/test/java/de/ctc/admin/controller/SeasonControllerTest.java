package de.ctc.admin.controller;

import de.ctc.TestHelper;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TestHelper testHelper;

    @Test
    void shouldListSeasons() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/seasons"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void shouldShowNewSeasonForm() throws Exception {
        mockMvc.perform(get("/admin/seasons/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"))
                .andExpect(model().attributeExists("seasonForm"));
    }

    @Test
    void shouldCreateSeason() throws Exception {
        // Season creation via form will need scoring references — this tests the form binding
        var rs = testHelper.createSeason("Dummy").getRaceScoring();
        var ms = testHelper.createSeason("Dummy2").getMatchScoring();

        mockMvc.perform(post("/admin/seasons/save")
                        .param("name", "MockMvc Test Season")
                        .param("active", "true")
                        .param("raceScoring", rs.getId().toString())
                        .param("matchScoring", ms.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        var season = testHelper.createSeason("Dummy Blank");
        mockMvc.perform(post("/admin/seasons/save")
                        .param("name", "")
                        .param("raceScoring", season.getRaceScoring().getId().toString())
                        .param("matchScoring", season.getMatchScoring().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"));
    }

    @Test
    void shouldEditSeason() throws Exception {
        var season = testHelper.createSeason("Edit Test");

        mockMvc.perform(get("/admin/seasons/" + season.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"))
                .andExpect(model().attribute("season", hasProperty("name", is("Edit Test"))));
    }

    @Test
    void shouldShowSeasonDetail() throws Exception {
        var season = testHelper.createSeason("Detail Test");

        mockMvc.perform(get("/admin/seasons/" + season.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-detail"))
                .andExpect(model().attributeExists("season"));
    }

    @Test
    void shouldDeleteSeason() throws Exception {
        var season = testHelper.createSeason("Delete Test");

        mockMvc.perform(post("/admin/seasons/" + season.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));

        assertFalse(seasonRepository.findById(season.getId()).isPresent());
    }

    @Test
    void shouldAddTeamToSeason() throws Exception {
        var season = testHelper.createSeason("Add Team Season");
        var team = teamRepository.save(new Team("Add Team Racing", "ATR"));

        mockMvc.perform(post("/admin/seasons/" + season.getId() + "/add-team")
                        .param("teamId", team.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons/" + season.getId() + "/edit"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldSaveExistingSeason() throws Exception {
        var season = testHelper.createSeason("Update Test");

        mockMvc.perform(post("/admin/seasons/save")
                        .param("id", season.getId().toString())
                        .param("name", "Updated Season Name")
                        .param("active", "false")
                        .param("raceScoring", season.getRaceScoring().getId().toString())
                        .param("matchScoring", season.getMatchScoring().getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));

        var updated = seasonRepository.findById(season.getId()).orElseThrow();
        assertEquals("Updated Season Name", updated.getName());
    }
}
