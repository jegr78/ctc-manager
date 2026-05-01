package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for SeasonPhaseGroupController CRUD + bulk roster save (UI-04).
 * All tests are RED in Wave 0 — SeasonPhaseGroupController does not yet exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;
    @Autowired
    private PhaseTeamRepository phaseTeamRepository;
    @Autowired
    private SeasonPhaseService seasonPhaseService;
    @Autowired
    private TestHelper testHelper;

    // UI-04, D-19: Two-Step group create — POST save redirects to Phase-Detail with success
    @Test
    void givenGroupsPhase_whenSaveGroup_thenRedirectsToRosterStep() throws Exception {
        // given: season with a GROUPS-layout phase
        var season = testHelper.createSeason("T-Phase60-GroupSave");
        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        regular.setLayout(PhaseLayout.GROUPS);
        seasonPhaseRepository.save(regular);

        // when
        mockMvc.perform(post("/admin/seasons/{sid}/phases/{pid}/groups/save",
                        season.getId(), regular.getId())
                        .param("phaseId", regular.getId().toString())
                        .param("name", "Group A"))
                // then: redirect to phase-detail tab (step 2 = roster section)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/seasons/" + season.getId() + "/phases/" + regular.getId() + "*"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // UI-04, D-20, Pitfall 8: roster diff — insert/update/delete
    @Test
    void givenRosterDiff_whenSave_thenInsertsAndDeletesAndUpdates() throws Exception {
        // given: season with GROUPS phase and 2 pre-existing PhaseTeams
        var season = testHelper.createSeason("T-Phase60-RosterDiff");
        var team1 = testHelper.createTeam("T-Phase60-RD-Team1", "P60T1");
        var team2 = testHelper.createTeam("T-Phase60-RD-Team2", "P60T2");
        var team3 = testHelper.createTeam("T-Phase60-RD-Team3", "P60T3");
        season.addTeam(team1);
        season.addTeam(team2);
        season.addTeam(team3);
        var updatedSeason = testHelper.createSeason("T-Phase60-RosterDiff-Internal");

        var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        var phaseId = regular.getId();

        // when: POST roster with diff assignments
        // assignments[0]: team1 included=true (update or keep)
        // assignments[1]: team2 included=false (remove)
        // assignments[2]: team3 included=true (insert)
        mockMvc.perform(post("/admin/seasons/{sid}/phases/{pid}/groups/roster",
                        season.getId(), phaseId)
                        .param("phaseId", phaseId.toString())
                        .param("assignments[0].teamId", team1.getId().toString())
                        .param("assignments[0].included", "true")
                        .param("assignments[1].teamId", team2.getId().toString())
                        .param("assignments[1].included", "false")
                        .param("assignments[2].teamId", team3.getId().toString())
                        .param("assignments[2].included", "true"))
                // then: redirect with success flash
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        // Verify DB state: team2 removed, team3 inserted
        var pts = phaseTeamRepository.findByPhaseId(phaseId);
        assertThat(pts).extracting(pt -> pt.getTeam().getId())
                .doesNotContain(team2.getId())
                .contains(team3.getId());
    }
}
