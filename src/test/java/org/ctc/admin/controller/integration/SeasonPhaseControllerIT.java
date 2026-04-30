package org.ctc.admin.controller.integration;

import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase CRUD with persistent test data (UI-02, UI-03).
 * All tests are RED in Wave 0 — SeasonPhaseController does not yet exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired
    private SeasonPhaseService seasonPhaseService;
    @Autowired
    private TestHelper testHelper;

    // UI-02 (D-29): GROUPS-layout phase shows group sub-tabs
    @Test
    void givenGroupsLayoutPhase_whenGetSeasonDetail_thenGroupSubTabsRendered() throws Exception {
        // given: season with a GROUPS-layout REGULAR phase + 2 groups
        var season = testHelper.createSeason("T-Phase60-GroupSubTabs");
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();

        // Create two groups (via service — will fail RED until service method exists)
        seasonPhaseService.createGroup(regular.getId(), "Group A", 0);
        seasonPhaseService.createGroup(regular.getId(), "Group B", 1);

        // when: GET phase-detail tab
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}", season.getId(), regular.getId()))
                // then: model must contain groups list with 2 entries
                .andExpect(status().isOk())
                .andExpect(model().attribute("groups", hasSize(2)));
    }

    // UI-02: Phase detail page renders phase information
    @Test
    void givenSeasonWithRegularPhase_whenGetPhaseDetail_thenReturnsPhaseDetailView() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-PhaseDetail");
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();

        // when
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}", season.getId(), regular.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("phase"))
                .andExpect(model().attributeExists("season"));
    }
}
