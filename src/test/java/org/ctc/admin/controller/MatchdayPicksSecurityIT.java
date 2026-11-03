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
        "spring.datasource.url=jdbc:h2:mem:pickreviewsecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Tag("integration")
class MatchdayPicksSecurityIT {
    @Autowired MockMvc mvc;

    @Test
    void givenAnonymousUser_whenReadingManagementReview_thenRequireAuthentication() throws Exception {
        // when / then
        mvc.perform(get("/admin/matchday-picks")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser("test-manager")
    void givenAuthenticatedManager_whenReadingOverview_thenUseExistingInternalSecurityChain() throws Exception {
        // when / then
        mvc.perform(get("/admin/matchday-picks")).andExpect(status().isOk()).andExpect(view().name("admin/matchday-picks"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"decision", "correction", "correction/refresh"})
    void givenAnonymousMutationWithCsrf_whenPosting_thenRejectBeforeDomainMutation(String route) throws Exception {
        // when / then
        mvc.perform(post(path(route)).with(csrf())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"decision", "correction", "correction/refresh"})
    @WithMockUser("test-manager")
    void givenAuthenticatedMutationWithoutCsrf_whenPosting_thenRetainCsrfProtection(String route) throws Exception {
        // when / then
        mvc.perform(post(path(route))).andExpect(status().isForbidden());
    }

    String path(String route) { return "/admin/matchday-picks/" + UUID.randomUUID() + "/" + route; }
}
