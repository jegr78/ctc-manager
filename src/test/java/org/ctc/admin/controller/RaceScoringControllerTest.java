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
    void whenGetRaceScorings_thenReturnsRaceScoringListView() throws Exception {
        // when
        mockMvc.perform(get("/admin/race-scorings"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-list"))
                .andExpect(model().attributeExists("scorings"));
    }

    @Test
    void whenGetNewRaceScoringForm_thenReturnsRaceScoringForm() throws Exception {
        // when
        mockMvc.perform(get("/admin/race-scorings/new"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"))
                .andExpect(model().attributeExists("raceScoringForm"));
    }

    @Test
    void givenValidRaceScoringForm_whenSaveNewRaceScoring_thenRedirectsAndPersists() throws Exception {
        // when
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "Test Scoring")
                        .param("racePoints", "20,17,14")
                        .param("qualiPoints", "3,2,1")
                        .param("fastestLapPoints", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"));

        // then
        var all = raceScoringRepository.findAll();
        assertTrue(all.stream().anyMatch(s -> "Test Scoring".equals(s.getName())));
    }

    @Test
    void givenNonMonotonicPoints_whenSaveRaceScoring_thenRedirectsWithError() throws Exception {
        // when
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "Invalid")
                        .param("racePoints", "10,20,5")
                        .param("fastestLapPoints", "0"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenExistingRaceScoring_whenGetEditForm_thenReturnsRaceScoringForm() throws Exception {
        // given
        var scoring = raceScoringRepository.save(new RaceScoring("Edit Test", "20,17", null, 0));

        // when
        mockMvc.perform(get("/admin/race-scorings/" + scoring.getId() + "/edit"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"))
                .andExpect(model().attributeExists("raceScoringForm"));
    }

    @Test
    void givenExistingRaceScoring_whenDeleteRaceScoring_thenRedirectsAndRemoves() throws Exception {
        // given
        var scoring = raceScoringRepository.save(new RaceScoring("Delete Test", "20,17", null, 0));
        var id = scoring.getId();

        // when
        mockMvc.perform(post("/admin/race-scorings/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"));

        // then
        assertFalse(raceScoringRepository.existsById(id));
    }

    @Test
    void givenExistingRaceScoring_whenSaveUpdatedRaceScoring_thenRedirectsAndUpdates() throws Exception {
        // given
        var scoring = raceScoringRepository.save(new RaceScoring("Update Test", "20,17", null, 0));

        // when
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("id", scoring.getId().toString())
                        .param("name", "Updated RS")
                        .param("racePoints", "25,20,15")
                        .param("qualiPoints", "3,2,1")
                        .param("fastestLapPoints", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/race-scorings"))
                .andExpect(flash().attributeExists("successMessage"));

        // then
        var updated = raceScoringRepository.findById(scoring.getId()).orElseThrow();
        assertEquals("Updated RS", updated.getName());
        assertEquals("25,20,15", updated.getRacePoints());
    }

    @Test
    void givenBlankName_whenSaveRaceScoring_thenReturnsFormWithErrors() throws Exception {
        // when
        mockMvc.perform(post("/admin/race-scorings/save")
                        .param("name", "")
                        .param("racePoints", "20,17")
                        .param("fastestLapPoints", "0"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-scoring-form"));
    }
}
