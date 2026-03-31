package org.ctc.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TemplateEditorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenGetTemplateEditors_thenReturnsTemplateEditorsView() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attributeExists("teamCardTemplate", "lineupTemplate", "settingsTemplate", "activeTab"));
    }

    @Test
    void givenTabParam_whenGetTemplateEditors_thenReturnsViewWithActiveTab() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "lineup"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "lineup"));
    }

    @Test
    void whenResetTeamCardTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void whenResetLineupTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/lineup/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=lineup"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenTemplateContent_whenSaveTeamCardTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/save")
                        .param("template", "<html>Test_Template</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/reset"));
    }

    @Test
    void givenTemplateContent_whenSaveLineupTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/lineup/save")
                        .param("template", "<html>Test_Lineup_Template</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=lineup"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/lineup/reset"));
    }

    @Test
    void givenSettingsTabParam_whenGetTemplateEditors_thenReturnsSettingsTabWithTemplate() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "settings"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "settings"))
                .andExpect(model().attributeExists("settingsTemplate", "settingsIsCustom"));
    }

    @Test
    void givenTemplateContent_whenSaveSettingsTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/settings/save")
                        .param("template", "<html>Test_Settings_Template</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=settings"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/settings/reset"));
    }

    @Test
    void whenResetSettingsTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/settings/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=settings"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
