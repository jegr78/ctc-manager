package org.ctc.admin.controller;

import org.ctc.admin.service.TeamCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TemplateEditorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamCardService teamCardService;

    @BeforeEach
    void setUpTeamCardServiceDefaults() throws IOException {
        // Default stub: loadTemplate returns non-null so model attribute exists in happy-path tests
        when(teamCardService.loadTemplate()).thenReturn("<html>default</html>");
        when(teamCardService.hasCustomTemplate()).thenReturn(false);
    }

    @Test
    void whenGetTemplateEditors_thenReturnsTemplateEditorsView() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attributeExists("teamCardTemplate", "lineupTemplate", "settingsTemplate",
                        "raceResultsTemplate", "overlayTemplate", "activeTab"));
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

    @Test
    void givenRaceResultsTabParam_whenGetTemplateEditors_thenReturnsRaceResultsTabWithTemplate() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "race-results"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "race-results"))
                .andExpect(model().attributeExists("raceResultsTemplate", "raceResultsIsCustom"));
    }

    @Test
    void givenTemplateContent_whenSaveRaceResultsTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/race-results/save")
                        .param("template", "<html>Test_Race_Results_Template</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=race-results"))
                .andExpect(flash().attributeExists("successMessage"));
        // Cleanup: reset to avoid leftover custom template on disk
        mockMvc.perform(post("/admin/tools/template-editors/race-results/reset"));
    }

    @Test
    void whenResetRaceResultsTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/race-results/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=race-results"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenTeamCardTemplate_whenPreview_thenReturnsRenderedHtml() throws Exception {
        // given
        String template = "<html><body><span th:text=\"${teamName}\"></span></body></html>";

        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/preview")
                        .param("template", template))
                // then
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Team Alpha")));
    }

    @Test
    void givenRaceResultsTemplate_whenPreview_thenReturnsRenderedHtml() throws Exception {
        // given
        String template = "<html><body><span th:text=\"${homeTotal}\"></span></body></html>";

        // when
        mockMvc.perform(post("/admin/tools/template-editors/race-results/preview")
                        .param("template", template))
                // then
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("95")));
    }

    @Test
    void givenOverlayTabParam_whenGetTemplateEditors_thenReturnsOverlayTab() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "overlay"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attribute("activeTab", "overlay"))
                .andExpect(model().attributeExists("overlayTemplate", "overlayIsCustom"));
    }

    @Test
    void whenResetOverlayTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/overlay/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=overlay"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void givenTemplateContent_whenSaveOverlayTemplate_thenRedirectsWithSuccess() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/overlay/save")
                        .param("template", "<html>test overlay</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=overlay"))
                .andExpect(flash().attributeExists("successMessage"));

        // cleanup
        mockMvc.perform(post("/admin/tools/template-editors/overlay/reset"));
    }

    @Test
    void givenOverlayTemplate_whenPreview_thenReturnsRenderedHtml() throws Exception {
        // given
        String template = "<html><body><span th:text=\"${homeTeamName}\"></span></body></html>";

        // when
        mockMvc.perform(post("/admin/tools/template-editors/overlay/preview")
                        .param("template", template))
                // then
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Team Alpha")));
    }

    @Test
    void givenInvalidTemplateType_whenPreview_thenReturnsBadRequest() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/invalid-type/preview")
                        .param("template", "<html></html>"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("Unknown template type"));
    }

    @Test
    void givenMaliciousTemplate_whenPreview_thenReturnsBadRequest() throws Exception {
        // given
        String maliciousTemplate = "<span th:text=\"${T(java.lang.Runtime).getRuntime()}\"></span>";

        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/preview")
                        .param("template", maliciousTemplate))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("Template contains unsafe expressions"));
    }

    @Test
    void givenInvalidTemplateType_whenSave_thenRedirectsWithErrorMessage() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/invalid-type/save")
                        .param("template", "<html>test</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors"))
                .andExpect(flash().attribute("errorMessage", "Unknown template type"));
    }

    @Test
    void givenInvalidTemplateType_whenReset_thenRedirectsWithErrorMessage() throws Exception {
        // when
        mockMvc.perform(post("/admin/tools/template-editors/invalid-type/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors"))
                .andExpect(flash().attribute("errorMessage", "Unknown template type"));
    }

    @Test
    void givenMaliciousTemplate_whenSave_thenRedirectsWithError() throws Exception {
        // given
        String maliciousTemplate = "<span th:text=\"${T(java.lang.Runtime).getRuntime()}\"></span>";

        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/save")
                        .param("template", maliciousTemplate))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attribute("errorMessage", "Template contains unsafe expressions"));
    }

    @Test
    void givenErrorResponseOnPreview_thenDoesNotExposeExceptionDetails() throws Exception {
        // given — template that causes a rendering error (undefined variable used with strict mode syntax)
        String badTemplate = "<span th:text=\"${#undefined.method()}\"></span>";

        // when
        var result = mockMvc.perform(post("/admin/tools/template-editors/team-cards/preview")
                        .param("template", badTemplate))
                // then
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn();

        // Error message must not contain exception class names or stack traces
        String body = result.getResponse().getContentAsString();
        assertThat(body).isEqualTo("Preview failed");
    }


    @Test
    void givenIoException_whenLoadTemplate_thenShowsErrorMessage() throws Exception {
        // given
        when(teamCardService.loadTemplate()).thenThrow(new IOException("disk full"));

        // when
        mockMvc.perform(get("/admin/tools/template-editors")
                        .param("tab", "team-cards"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/template-editors"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void givenIoException_whenSaveTemplate_thenRedirectsWithError() throws Exception {
        // given
        doThrow(new IOException("disk full")).when(teamCardService).saveTemplate(org.mockito.ArgumentMatchers.anyString());

        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/save")
                        .param("template", "<html>test</html>"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenIoException_whenResetTemplate_thenRedirectsWithError() throws Exception {
        // given
        doThrow(new IOException("disk full")).when(teamCardService).resetTemplate();

        // when
        mockMvc.perform(post("/admin/tools/template-editors/team-cards/reset"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/template-editors?tab=team-cards"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
