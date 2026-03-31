package org.ctc.admin.controller;

import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.repository.RaceScoringRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class RaceScoringControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RaceScoringRepository raceScoringRepository;

    @Test
    void shouldListRaceScorings() throws Exception {
        mockMvc.perform(get("/admin/race-scorings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-list"))
                .andExpect(model().attributeExists("scorings"));
    }

    @Test
    void shouldShowCreateForm() throws Exception {
        mockMvc.perform(get("/admin/race-scorings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"))
                .andExpect(model().attributeExists("raceScoringForm"));
    }

    @Test
    void shouldSaveNewRaceScoring() throws Exception {
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "Test Scoring")
                        .param("racePoints", "20,17,14")
                        .param("qualiPoints", "3,2,1")
                        .param("fastestLapPoints", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"));

        var all = raceScoringRepository.findAll();
        assertTrue(all.stream().anyMatch(s -> "Test Scoring".equals(s.getName())));
    }

    @Test
    void shouldRejectInvalidMonotonicity() throws Exception {
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "Invalid")
                        .param("racePoints", "10,20,5")
                        .param("fastestLapPoints", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldShowEditForm() throws Exception {
        var scoring = raceScoringRepository.save(new RaceScoring("Edit Test", "20,17", null, 0));

        mockMvc.perform(get("/admin/race-scorings/" + scoring.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"))
                .andExpect(model().attributeExists("raceScoringForm"));
    }

    @Test
    void shouldDeleteRaceScoring() throws Exception {
        var scoring = raceScoringRepository.save(new RaceScoring("Delete Test", "20,17", null, 0));
        var id = scoring.getId();

        mockMvc.perform(post("/admin/race-scorings/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"));

        assertFalse(raceScoringRepository.existsById(id));
    }

    @Test
    void shouldUpdateExistingRaceScoring() throws Exception {
        var scoring = raceScoringRepository.save(new RaceScoring("Update Test", "20,17", null, 0));

        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("id", scoring.getId().toString())
                        .param("name", "Updated RS")
                        .param("racePoints", "25,20,15")
                        .param("qualiPoints", "3,2,1")
                        .param("fastestLapPoints", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"))
                .andExpect(flash().attributeExists("successMessage"));

        var updated = raceScoringRepository.findById(scoring.getId()).orElseThrow();
        assertEquals("Updated RS", updated.getName());
        assertEquals("25,20,15", updated.getRacePoints());
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "")
                        .param("racePoints", "20,17")
                        .param("fastestLapPoints", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"));
    }
}
