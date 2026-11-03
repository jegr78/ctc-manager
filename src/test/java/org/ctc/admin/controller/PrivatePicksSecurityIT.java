package org.ctc.admin.controller;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:privatepickssecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Tag("integration")
class PrivatePicksSecurityIT {
    @Autowired MockMvc mvc;

    @Test
    void givenAnonymousUser_whenReadingPrivateSetup_thenRequireExistingManagementAuthentication() throws Exception {
        // when / then
        mvc.perform(get("/admin/discord-config/picks")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void givenAuthenticatedManager_whenReadingSetup_thenRenderDisabledConsumerWithoutExternalActivity() throws Exception {
        // when / then
        mvc.perform(get("/admin/discord-config/picks")).andExpect(status().isOk())
                .andExpect(view().name("admin/private-picks"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bindings/save", "bindings/id/delete", "register-command"})
    void givenAnonymousMutationWithCsrf_whenPosting_thenRejectBeforeManagementService(String route) throws Exception {
        // when / then
        mvc.perform(post(path(route)).with(csrf())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bindings/save", "bindings/id/delete", "register-command"})
    @WithMockUser
    void givenAuthenticatedMutationWithoutCsrf_whenPosting_thenPreserveCsrfProtection(String route) throws Exception {
        // when / then
        mvc.perform(post(path(route))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void givenAuthenticatedInvalidBindingWithCsrf_whenPosting_thenRetainNativeFieldValidation() throws Exception {
        // when / then
        mvc.perform(post(path("bindings/save")).with(csrf()).param("channelId", "invalid"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "seasonId", "teamId", "channelId"))
                .andExpect(view().name("admin/private-picks"));
    }

    @Test
    @WithMockUser
    void givenAuthenticatedRegistrationWithoutGuild_whenPostingWithCsrf_thenRequireConfigurationAtRealService() throws Exception {
        // when / then
        mvc.perform(post(path("register-command")).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Configure a Discord guild first."));
    }

    String path(String route) { return "/admin/discord-config/picks/" + route.replace("/id/", "/" + UUID.randomUUID() + "/"); }
}
