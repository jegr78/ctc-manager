package org.ctc.admin.controller;

import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.repository.MatchScoringRepository;
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
class MatchScoringControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MatchScoringRepository matchScoringRepository;

    @Test
    void shouldListMatchScorings() throws Exception {
        mockMvc.perform(get("/admin/match-scorings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/match-scoring-list"))
                .andExpect(model().attributeExists("scorings"));
    }

    @Test
    void shouldShowCreateForm() throws Exception {
        mockMvc.perform(get("/admin/match-scorings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/match-scoring-form"))
                .andExpect(model().attributeExists("matchScoringForm"));
    }

    @Test
    void shouldSaveNewMatchScoring() throws Exception {
        mockMvc.perform(post("/admin/match-scorings/save")
                        .param("name", "Custom 2-1-0")
                        .param("pointsWin", "2")
                        .param("pointsDraw", "1")
                        .param("pointsLoss", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/match-scorings"));

        var all = matchScoringRepository.findAll();
        assertTrue(all.stream().anyMatch(s -> "Custom 2-1-0".equals(s.getName())));
    }

    @Test
    void shouldShowEditForm() throws Exception {
        var scoring = matchScoringRepository.save(new MatchScoring("Edit Test MS", 3, 1, 0));

        mockMvc.perform(get("/admin/match-scorings/" + scoring.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/match-scoring-form"))
                .andExpect(model().attributeExists("matchScoringForm"));
    }

    @Test
    void shouldDeleteMatchScoring() throws Exception {
        var scoring = matchScoringRepository.save(new MatchScoring("Delete Test MS", 3, 1, 0));
        var id = scoring.getId();

        mockMvc.perform(post("/admin/match-scorings/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/match-scorings"));

        assertFalse(matchScoringRepository.existsById(id));
    }

    @Test
    void shouldUpdateExistingMatchScoring() throws Exception {
        var scoring = matchScoringRepository.save(new MatchScoring("Update Test MS", 3, 1, 0));

        mockMvc.perform(post("/admin/match-scorings/save")
                        .param("id", scoring.getId().toString())
                        .param("name", "Updated MS")
                        .param("pointsWin", "4")
                        .param("pointsDraw", "2")
                        .param("pointsLoss", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/match-scorings"))
                .andExpect(flash().attributeExists("successMessage"));

        var updated = matchScoringRepository.findById(scoring.getId()).orElseThrow();
        assertEquals("Updated MS", updated.getName());
        assertEquals(4, updated.getPointsWin());
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/admin/match-scorings/save")
                        .param("name", "")
                        .param("pointsWin", "3")
                        .param("pointsDraw", "1")
                        .param("pointsLoss", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/match-scoring-form"));
    }
}
