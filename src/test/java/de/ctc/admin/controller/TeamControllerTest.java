package de.ctc.admin.controller;

import de.ctc.domain.model.Team;
import de.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    void shouldListTeams() throws Exception {
        mockMvc.perform(get("/admin/teams"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/teams"))
                .andExpect(model().attributeExists("parentTeams"));
    }

    @Test
    void shouldCreateTeam() throws Exception {
        mockMvc.perform(post("/admin/teams/save")
                        .param("name", "MockMvc Racing")
                        .param("shortName", "MVR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"));

        var saved = teamRepository.findByShortName("MVR");
        assertTrue(saved.isPresent());
        assertEquals("MockMvc Racing", saved.get().getName());
    }

    @Test
    void shouldShowTeamDetail() throws Exception {
        var team = teamRepository.save(new Team("Detail Racing", "DTL"));

        mockMvc.perform(get("/admin/teams/" + team.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-detail"))
                .andExpect(model().attributeExists("team", "seasons"));
    }

    @Test
    void shouldDeleteTeam() throws Exception {
        var team = teamRepository.save(new Team("Delete Racing", "DLR"));

        mockMvc.perform(post("/admin/teams/" + team.getId() + "/delete"))
                .andExpect(status().is3xxRedirection());

        assertFalse(teamRepository.findById(team.getId()).isPresent());
    }
}
