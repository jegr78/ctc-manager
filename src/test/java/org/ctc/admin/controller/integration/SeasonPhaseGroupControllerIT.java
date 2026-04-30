package org.ctc.admin.controller.integration;

import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.repository.SeasonPhaseRepository;
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
 * Integration tests for Group CRUD + roster bulk save (UI-04).
 * All tests are RED in Wave 0 — SeasonPhaseGroupController does not yet exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseGroupControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired
    private SeasonPhaseService seasonPhaseService;
    @Autowired
    private TestHelper testHelper;

    // UI-04: GET new group form renders correctly
    @Test
    void givenGroupsPhase_whenGetNewGroupForm_thenReturnsGroupForm() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-NewGroupForm");
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        regular.setLayout(PhaseLayout.GROUPS);
        seasonPhaseRepository.save(regular);

        // when
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}/groups/new",
                        season.getId(), regular.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-phase-group-form"))
                .andExpect(model().attributeExists("form"));
    }

    // UI-04: GET edit group form with existing group
    @Test
    void givenExistingGroup_whenGetEditGroupForm_thenReturnsGroupFormWithData() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-EditGroupForm");
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        var group = seasonPhaseService.createGroup(regular.getId(), "T-Phase60-Group-A", 0);

        // when
        mockMvc.perform(get("/admin/seasons/{sid}/phases/{pid}/groups/{gid}/edit",
                        season.getId(), regular.getId(), group.getId()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-phase-group-form"))
                .andExpect(model().attributeExists("form"));
    }

    // UI-04 (D-28 strict guard): delete group with teams must flash error
    @Test
    void givenGroupWithTeams_whenDeleteGroup_thenFlashError() throws Exception {
        // given
        var season = testHelper.createSeason("T-Phase60-DeleteGroupGuard");
        var team = testHelper.createTeam("T-Phase60-DG-Team", "P60DG");
        season.addTeam(team);
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        var group = seasonPhaseService.createGroup(regular.getId(), "T-Phase60-Group-B", 0);
        // Assign team to group via roster diff (assignTeamsToPhase)
        seasonPhaseService.assignTeamToPhase(regular.getId(), team.getId(), group.getId());

        // when
        mockMvc.perform(post("/admin/seasons/{sid}/phases/{pid}/groups/{gid}/delete",
                        season.getId(), regular.getId(), group.getId()))
                // then: strict guard fires, flash error shown
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
