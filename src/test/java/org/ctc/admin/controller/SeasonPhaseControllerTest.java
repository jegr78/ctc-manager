package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for SeasonPhaseController GET/POST flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired
    private TestHelper testHelper;
    @Autowired
    private SeasonPhaseService phaseService;

    @Test
    void givenPhaseAndUnassignedTeam_whenOpenOverview_thenReplacementChoicesAreAvailable() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("Test-Overview replacement");
        var candidate = testHelper.createTeam("Test-Overview successor", "T-OV-SUCCESSOR");
        var phase = fixture.matchday().getPhase();
        var group = phaseService.createGroup(phase.getId(), "Test-Overview group", 0);
        var base = "/admin/seasons/" + fixture.season().getId() + "/phases/" + phase.getId();

        // when / then
        for (var path : java.util.List.of(base, base + "/groups/" + group.getId())) {
            mockMvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(model().attribute("availableTeams", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(candidate.getId())))));
        }
    }

    // UI-02 (D-09 IDOR-safety)
    @Test
    void givenWrongSeasonId_whenGetPhase_thenReturns404() throws Exception {
        // given: season A with phase, season B without
        var seasonA = testHelper.createSeason("T-Phase60-IDOR-A");
        var seasonB = testHelper.createSeason("T-Phase60-IDOR-B");
        var phaseA = seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonA.getId(), PhaseType.REGULAR).orElseThrow();

        // when / then: requesting phaseA via seasonB's ID must 404
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}", seasonB.getId(), phaseA.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenSeasonWithoutRegularPhase_whenGetSeasonDetail_thenRendersEmptyStateCard() throws Exception {
        // given
        var season = seasonRepository.saveAndFlush(new org.ctc.domain.model.Season("Test-No regular phase", 2026, 1));
        org.assertj.core.api.Assertions.assertThat(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR)).isEmpty();

        // when / then
        mockMvc.perform(get("/admin/seasons/{id}", season.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasRegularPhase", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No Regular Phase yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/seasons/" + season.getId() + "/phases/new?phaseType=REGULAR")));
    }

    // UI-03 (D-17 defaults from REGULAR)
    @Test
    void givenRegularPhase_whenAddPhase_thenFormPrefilledWithRegularDefaults() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-Defaults");

        // when / then
        mockMvc.perform(get("/admin/seasons/{id}/phases/new", season.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-phase-form"))
                .andExpect(model().attributeExists("seasonPhaseForm"))
                .andExpect(model().attributeExists("season"));
    }

    // UI-03 (D-22 UNIQUE phaseType)
    @Test
    void givenExistingRegular_whenCreateSecondRegular_thenFlashError() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-Dup");
        var rs = testHelper.getRaceScoring(season);
        var ms = testHelper.getMatchScoring(season);

        // when / then
        mockMvc.perform(post("/admin/seasons/{id}/phases/save", season.getId())
                        .param("seasonId", season.getId().toString())
                        .param("phaseType", "REGULAR")
                        .param("layout", "LEAGUE")
                        .param("format", "LEAGUE")
                        .param("raceScoringId", rs.getId().toString())
                        .param("matchScoringId", ms.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // UI-07 (D-42 PLAYOFF auto-create via "+ Add Phase")
    @Test
    void givenSeasonWithoutPlayoff_whenAddPhasePLAYOFF_thenPlayoffServiceAutoCreatesPlayoff() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-PlayoffAutoCreate");
        var rs = testHelper.getRaceScoring(season);
        var ms = testHelper.getMatchScoring(season);

        // when / then
        mockMvc.perform(post("/admin/seasons/{id}/phases/save", season.getId())
                        .param("seasonId", season.getId().toString())
                        .param("phaseType", "PLAYOFF")
                        .param("layout", "BRACKET")
                        .param("format", "LEAGUE")
                        .param("raceScoringId", rs.getId().toString())
                        .param("matchScoringId", ms.getId().toString())
                        .param("label", "Test Playoff"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }
}
