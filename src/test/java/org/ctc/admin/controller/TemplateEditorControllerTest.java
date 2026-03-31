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
    void shouldShowTemplateEditorsPage() throws Exception {
        mockMvc.perform(get("/admin/tools/template-editors"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attributeExists("teamCardTemplate", "lineupTemplate", "settingsTemplate", "activeTab"));
    }

    @Test
    void shouldShowTemplateEditorsWithTabParam() throws Exception {
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "lineup"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "lineup"));
    }

    @Test
    void shouldResetTeamCardTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldResetLineupTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/lineup/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=lineup"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shouldSaveTeamCardTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/save")
                        .param("template", "<html>Test_Template</html>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/reset"));
    }

    @Test
    void shouldSaveLineupTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/lineup/save")
                        .param("template", "<html>Test_Lineup_Template</html>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=lineup"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/lineup/reset"));
    }

    @Test
    void shouldShowSettingsTab() throws Exception {
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "settings"))
                .andExpect(model().attributeExists("settingsTemplate", "settingsIsCustom"));
    }

    @Test
    void shouldSaveSettingsTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/settings/save")
                        .param("template", "<html>Test_Settings_Template</html>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=settings"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/settings/reset"));
    }

    @Test
    void shouldResetSettingsTemplateAndRedirect() throws Exception {
        mockMvc.perform(post("/admin/tools/template-editors/settings/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=settings"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
