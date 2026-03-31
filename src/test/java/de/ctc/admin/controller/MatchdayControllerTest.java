package de.ctc.admin.controller;

import de.ctc.TestHelper;
import de.ctc.domain.model.Matchday;
import de.ctc.domain.repository.MatchdayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void shouldListMatchdays() throws Exception {
        mockMvc.perform(get("/admin/matchdays"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchdays"))
                .andExpect(model().attributeExists("matchdays", "seasons"));
    }

    @Test
    void shouldListMatchdaysBySeasonId() throws Exception {
        var season = testHelper.createSeason("MD List Season");
        matchdayRepository.save(new Matchday(season, "List MD1", 1));

        mockMvc.perform(get("/admin/matchdays").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchdays"))
                .andExpect(model().attributeExists("matchdays", "selectedSeasonId"));
    }

    @Test
    void shouldShowCreateForm() throws Exception {
        mockMvc.perform(get("/admin/matchdays/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeExists("matchday", "seasons"));
    }

    @Test
    void shouldShowCreateFormWithSeasonId() throws Exception {
        var season = testHelper.createSeason("MD Create Season");

        mockMvc.perform(get("/admin/matchdays/new").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"));
    }

    @Test
    void shouldShowEditForm() throws Exception {
        var season = testHelper.createSeason("MD Edit Season");
        var matchday = matchdayRepository.save(new Matchday(season, "Edit MD", 1));

        mockMvc.perform(get("/admin/matchdays/" + matchday.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeExists("matchday", "seasons"));
    }

    @Test
    void shouldSaveNewMatchday() throws Exception {
        var season = testHelper.createSeason("MD Save Season");

        mockMvc.perform(post("/admin/matchdays/save")
                        .param("label", "New Test Matchday")
                        .param("sortIndex", "1")
                        .param("seasonId", season.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldSaveExistingMatchday() throws Exception {
        var season = testHelper.createSeason("MD Update Season");
        var matchday = matchdayRepository.save(new Matchday(season, "Original", 1));

        mockMvc.perform(post("/admin/matchdays/save")
                        .param("id", matchday.getId().toString())
                        .param("label", "Updated Label")
                        .param("sortIndex", "2")
                        .param("seasonId", season.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        var updated = matchdayRepository.findById(matchday.getId()).orElseThrow();
        assertEquals("Updated Label", updated.getLabel());
    }

    @Test
    void shouldDeleteMatchday() throws Exception {
        var season = testHelper.createSeason("MD Delete Season");
        var matchday = matchdayRepository.save(new Matchday(season, "Delete MD", 1));

        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        assertFalse(matchdayRepository.findById(matchday.getId()).isPresent());
    }

    @Test
    void shouldReturnMatchdaysBySeasonJson() throws Exception {
        var season = testHelper.createSeason("MD JSON Season");
        matchdayRepository.save(new Matchday(season, "JSON MD1", 1));

        mockMvc.perform(get("/admin/matchdays/by-season")
                        .param("seasonName", season.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].label").value("JSON MD1"));
    }

    @Test
    void shouldReturnEmptyListForUnknownSeason() throws Exception {
        mockMvc.perform(get("/admin/matchdays/by-season")
                        .param("seasonName", "NonExistent Season XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldCreateMatchdayInline() throws Exception {
        var season = testHelper.createSeason("MD Inline Season");

        mockMvc.perform(post("/admin/matchdays/create-inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seasonName\":\"" + season.getName() + "\",\"label\":\"Inline MD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Inline MD"));
    }

    @Test
    void shouldRejectDuplicateInlineMatchday() throws Exception {
        var season = testHelper.createSeason("MD Dup Inline Season");
        matchdayRepository.save(new Matchday(season, "Existing MD", 1));

        mockMvc.perform(post("/admin/matchdays/create-inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seasonName\":\"" + season.getName() + "\",\"label\":\"Existing MD\"}"))
                .andExpect(status().isConflict());
    }
}
