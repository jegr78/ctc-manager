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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MatchScoringControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MatchScoringRepository matchScoringRepository;

	@Test
	void whenGetMatchScorings_thenReturnsMatchScoringListView() throws Exception {
		mockMvc.perform(get("/admin/match-scorings"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-scoring-list"))
				.andExpect(model().attributeExists("scorings"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-list-search")));
	}

	@Test
	void whenGetNewMatchScoringForm_thenReturnsMatchScoringForm() throws Exception {
		mockMvc.perform(get("/admin/match-scorings/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-scoring-form"))
				.andExpect(model().attributeExists("matchScoringForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("entity-editor-actions")));
	}

	@Test
	void givenValidMatchScoringForm_whenSaveNewMatchScoring_thenRedirectsAndPersists() throws Exception {
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
	void givenExistingMatchScoring_whenGetEditForm_thenReturnsMatchScoringForm() throws Exception {
		var scoring = matchScoringRepository.save(new MatchScoring("Edit Test MS", 3, 1, 0));

		mockMvc.perform(get("/admin/match-scorings/" + scoring.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-scoring-form"))
				.andExpect(model().attributeExists("matchScoringForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("entity-editor-actions")));
	}

	@Test
	void givenExistingMatchScoring_whenDeleteMatchScoring_thenRedirectsAndRemoves() throws Exception {
		var scoring = matchScoringRepository.save(new MatchScoring("Delete Test MS", 3, 1, 0));
		var id = scoring.getId();

		mockMvc.perform(post("/admin/match-scorings/" + id + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/match-scorings"));

		assertFalse(matchScoringRepository.existsById(id));
	}

	@Test
	void givenExistingMatchScoring_whenSaveUpdatedMatchScoring_thenRedirectsAndUpdates() throws Exception {
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
	void givenBlankName_whenSaveMatchScoring_thenReturnsFormWithErrors() throws Exception {
		mockMvc.perform(post("/admin/match-scorings/save")
						.param("name", "")
						.param("pointsWin", "3")
						.param("pointsDraw", "1")
						.param("pointsLoss", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-scoring-form"))
                .andExpect(model().attributeExists("pageTitle"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name-error")));
	}
}
