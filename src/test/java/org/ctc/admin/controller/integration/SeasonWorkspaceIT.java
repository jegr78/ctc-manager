package org.ctc.admin.controller.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.ctc.TestHelper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class SeasonWorkspaceIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;

    @Test
    void givenSeasonResources_whenOpenDirectLinks_thenSidebarKeepsTheirSeason() throws Exception {
        // given
        var fixture = helper.createFullSeasonFixture("Test-Workspace");
        var seasonId = fixture.season().getId();

        // when
        for (var path : List.of("/admin/matchdays/" + fixture.matchday().getId(),
                "/admin/matches/" + fixture.match().getId(),
                "/admin/races/" + fixture.race().getId(),
                "/admin/races/" + fixture.race().getId() + "/edit",
                "/admin/races/" + fixture.race().getId() + "/results")) {
            var response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();
            var page = Jsoup.parse(response.getContentAsString());

            // then
            assertThat(page.select("#workspace-season option[selected]").attr("value"))
                    .as(path).isEqualTo(seasonId.toString());
            for (var section : List.of("matchdays", "races", "playoffs", "standings")) {
                assertThat(page.select(".sidebar a[href='/admin/" + section + "?seasonId=" + seasonId + "']"))
                        .as(path + " -> " + section).hasSize(1);
            }
        }
    }

    @Test
    void givenSeason_whenSwitchSection_thenRedirectToExistingFilteredRoute() throws Exception {
        // given
        var season = helper.createSeason("Test-Workspace switch");

        // when / then
        mvc.perform(get("/admin/workspace").param("seasonId", season.getId().toString()).param("section", "races"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?seasonId=" + season.getId()));
        mvc.perform(get("/admin/workspace").param("seasonId", season.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons/" + season.getId()));
    }

    @Test
    void givenGlobalManagementPage_whenOpen_thenNoSeasonContextIsShown() throws Exception {
        // when
        var response = mvc.perform(get("/admin/teams")).andExpect(status().isOk()).andReturn().getResponse();

        // then
        assertThat(Jsoup.parse(response.getContentAsString()).select(".season-context")).isEmpty();
    }
}
