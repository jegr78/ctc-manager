package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.admin.dto.MatchdayForm;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.repository.MatchdayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

    @MockitoBean private MatchdayOverviewGraphicService overviewGraphicService;
    @MockitoBean private MatchdayScheduleGraphicService scheduleGraphicService;
    @MockitoBean private MatchdayResultsGraphicService resultsGraphicService;
    @MockitoBean private MatchResultsGraphicService matchResultsGraphicService;

    @Test
    void givenExistingMatchday_whenGetMatchdayDetail_thenReturnsDetailView() throws Exception {
        // given
        var season = testHelper.createSeason("MD Detail Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Test Matchday", 1));

        // when
        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-detail"))
                .andExpect(model().attributeExists("matchday"));
    }

    @Test
    void whenGetMatchdays_thenReturnsMatchdaysView() throws Exception {
        // when
        mockMvc.perform(get("/admin/matchdays"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchdays"))
                .andExpect(model().attributeExists("matchdays", "seasons"));
    }

    @Test
    void givenSeasonId_whenGetMatchdaysBySeasonId_thenReturnsFilteredMatchdays() throws Exception {
        // given
        var season = testHelper.createSeason("MD List Season");
        matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "List MD1", 1));

        // when
        mockMvc.perform(get("/admin/matchdays").param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchdays"))
                .andExpect(model().attributeExists("matchdays", "selectedSeasonId"));
    }

    @Test
    void whenGetNewMatchdayForm_thenReturnsMatchdayForm() throws Exception {
        // when
        mockMvc.perform(get("/admin/matchdays/new"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeExists("form", "seasons"));
    }

    @Test
    void givenSeasonId_whenGetNewMatchdayFormWithSeasonId_thenReturnsPrefilledForm() throws Exception {
        // given
        var season = testHelper.createSeason("MD Create Season");

        // when
        mockMvc.perform(get("/admin/matchdays/new").param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeExists("form", "season", "seasons"))
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("seasonId", org.hamcrest.Matchers.equalTo(season.getId()))));
    }

    @Test
    void givenExistingMatchday_whenGetEditForm_thenReturnsMatchdayForm() throws Exception {
        // given
        var season = testHelper.createSeason("MD Edit Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Edit MD", 1));

        // when
        mockMvc.perform(get("/admin/matchdays/" + matchday.getId() + "/edit"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeExists("form", "season", "seasons"));
    }

    @Test
    void givenValidMatchdayForm_whenSaveNewMatchday_thenRedirectsWithSuccess() throws Exception {
        // given
        var season = testHelper.createSeason("MD Save Season");

        // when
        mockMvc.perform(post("/admin/matchdays/save")
                        .param("label", "New Test Matchday")
                        .param("sortIndex", "1")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenExistingMatchday_whenSaveUpdatedMatchday_thenRedirectsAndUpdates() throws Exception {
        // given
        var season = testHelper.createSeason("MD Update Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Original", 1));

        // when
        mockMvc.perform(post("/admin/matchdays/save")
                        .param("id", matchday.getId().toString())
                        .param("label", "Updated Label")
                        .param("sortIndex", "2")
                        .param("seasonId", season.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        // then
        var updated = matchdayRepository.findById(matchday.getId()).orElseThrow();
        assertEquals("Updated Label", updated.getLabel());
    }

    @Test
    void givenBlankLabel_whenSaveMatchday_thenReturnsFormWithErrors() throws Exception {
        // given
        var season = testHelper.createSeason("MD Validation Season");

        // when
        mockMvc.perform(post("/admin/matchdays/save")
                        .param("label", "")
                        .param("sortIndex", "1")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/matchday-form"))
                .andExpect(model().attributeHasFieldErrors("form", "label"));
    }

    @Test
    void givenExistingMatchday_whenDeleteMatchday_thenRedirectsAndRemoves() throws Exception {
        // given
        var season = testHelper.createSeason("MD Delete Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Delete MD", 1));

        // when
        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        // then
        assertFalse(matchdayRepository.findById(matchday.getId()).isPresent());
    }

    @Test
    void givenSeasonWithMatchday_whenGetMatchdaysBySeasonJson_thenReturnsJsonArray() throws Exception {
        // given
        var season = testHelper.createSeason("MD JSON Season");
        matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "JSON MD1", 1));

        // when
        mockMvc.perform(get("/admin/matchdays/by-season")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].label").value("JSON MD1"));
    }

    @Test
    void givenUnknownSeasonId_whenGetMatchdaysBySeasonJson_thenReturnsEmptyArray() throws Exception {
        // when
        mockMvc.perform(get("/admin/matchdays/by-season")
                        .param("seasonId", java.util.UUID.randomUUID().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void givenValidRequest_whenCreateInlineMatchday_thenReturnsCreatedJson() throws Exception {
        // given
        var season = testHelper.createSeason("MD Inline Season");

        // when
        mockMvc.perform(post("/admin/matchdays/create-inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seasonId\":\"" + season.getId() + "\",\"label\":\"Inline MD\"}"))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Inline MD"));
    }

    // --- Graphics endpoint tests ---

    @Test
    void givenMatchdayWithMatches_whenGetDetail_thenGraphicFlagsPresent() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("GfxFlags");
        var matchday = fixture.matchday();

        // when
        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("hasMatches", "hasSchedule", "hasResults"));
    }

    @Test
    void givenMatchdayWithNoMatches_whenGetDetail_thenHasMatchesFalse() throws Exception {
        // given
        var season = testHelper.createSeason("GfxEmpty Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "GfxEmpty MD", 1));

        // when
        mockMvc.perform(get("/admin/matchdays/" + matchday.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasMatches", false));
    }

    @Test
    void givenDuplicateLabel_whenCreateInlineMatchday_thenReturnsConflict() throws Exception {
        // given
        var season = testHelper.createSeason("MD Dup Inline Season");
        matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Existing MD", 1));

        // when
        mockMvc.perform(post("/admin/matchdays/create-inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seasonId\":\"" + season.getId() + "\",\"label\":\"Existing MD\"}"))
                // then
                .andExpect(status().isConflict());
    }

    // --- Exception handling tests for graphic endpoints ---

    @Test
    void givenRuntimeException_whenGenerateOverviewGraphic_thenReturns500() throws Exception {
        // given
        var season = testHelper.createSeason("MD OvEx Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "OvEx MD", 1));
        when(overviewGraphicService.generateOverview(any())).thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/download-overview"))
                // then
                .andExpect(status().isInternalServerError());
    }

    @Test
    void givenRuntimeException_whenGenerateScheduleGraphic_thenReturns500() throws Exception {
        // given
        var season = testHelper.createSeason("MD SchEx Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "SchEx MD", 1));
        when(scheduleGraphicService.generateSchedule(any())).thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/download-schedule"))
                // then
                .andExpect(status().isInternalServerError());
    }

    @Test
    void givenRuntimeException_whenGenerateResultsGraphic_thenReturns500() throws Exception {
        // given
        var season = testHelper.createSeason("MD ResEx Season");
        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "ResEx MD", 1));
        when(resultsGraphicService.generateResults(any())).thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/download-results"))
                // then
                .andExpect(status().isInternalServerError());
    }

    @Test
    void givenRuntimeException_whenGenerateMatchResultsGraphic_thenReturns500() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("MdMREx");
        var matchday = fixture.matchday();
        var match = fixture.match();
        when(matchResultsGraphicService.generateMatchResults(any())).thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/matchdays/" + matchday.getId() + "/matches/" + match.getId() + "/download-match-results"))
                // then
                .andExpect(status().isInternalServerError());
    }
}
