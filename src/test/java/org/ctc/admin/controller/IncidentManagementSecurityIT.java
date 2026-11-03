package org.ctc.admin.controller;

import java.util.UUID;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentsecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles(value = "prod", inheritProfiles = false)
@Tag("integration")
class IncidentManagementSecurityIT extends IncidentIntegrationFixture {
    @Autowired MockMvc mvc;
    @Autowired org.ctc.domain.repository.IncidentDeliveryRepository deliveryRows;
    @MockitoBean org.ctc.discord.DiscordRestClient discord;

    @ParameterizedTest
    @ValueSource(strings = {"/admin/incident-contexts/context", "/admin/matches/match/incidents", "/admin/playoffs/matchup/unknown/incidents", "/admin/incident-contexts/context/deliveries", "/admin/incident-deliveries/unknown/"})
    void anonymousReadsRequireManagementAuthentication(String route) throws Exception {
        mvc.perform(get(path(route))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/incident-contexts/context/window/open", "/admin/incident-contexts/context/window/deadline", "/admin/matches/match/incidents/bind", "/admin/playoffs/matchup/unknown/incidents/bind", "/admin/incident-contexts/context/deliveries/resume", "/admin/incident-deliveries/unknown/"})
    void anonymousMutationWithCsrfIsRejectedBeforeManagementServices(String route) throws Exception {
        mvc.perform(post(path(route)).with(csrf())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/incident-contexts/context/window/open", "/admin/incident-contexts/context/window/deadline", "/admin/matches/match/incidents/bind", "/admin/playoffs/matchup/unknown/incidents/bind", "/admin/incident-contexts/context/deliveries/resume", "/admin/incident-deliveries/unknown/"})
    @WithMockUser("test-manager")
    void authenticatedMutationsRequireCsrf(String route) throws Exception {
        mvc.perform(post(path(route))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser("test-manager")
    void authenticatedNativeFormsContainCsrfAndPreserveValidationErrors() throws Exception {
        var html = mvc.perform(get(path("/admin/incident-contexts/context"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).select("form[action='/admin/incident-contexts/" + contextId + "/window/open'] input[name=_csrf]")).isNotEmpty();
        mvc.perform(post(path("/admin/incident-contexts/context/window/open")).with(csrf()).param("deadline", "invalid").param("offset", "+01:00").param("expectedVersion", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("incidentDeadlineForm", "deadline"));
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @Test
    @WithMockUser("test-manager")
    void confirmedOpeningRecordsTheAuthenticatedManagementPrincipal() throws Exception {
        mvc.perform(post(path("/admin/incident-contexts/context/window/open")).with(csrf()).param("deadline", "2035-01-02T20:00").param("offset", "+01:00").param("expectedVersion", "0").param("openedBy", "untrusted-form-actor"))
                .andExpect(status().is3xxRedirection());
        assertThat(windowRows.findById(windowId).orElseThrow().getOpenedBy()).isEqualTo("test-manager");
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    @Test
    @WithMockUser("test-manager")
    void unboundNativeBindingAlsoContainsCsrfAndDoesNotCreateStateOnGet() throws Exception {
        tx.executeWithoutResult(status -> { windowRows.deleteById(windowId); windowRows.flush(); contextRows.deleteById(contextId); contextRows.flush(); });
        long count = contextRows.count();
        var html = mvc.perform(get(path("/admin/matches/match/incidents"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).select("form[action='/admin/matches/" + fixture.match().getId() + "/incidents/bind'] input[name=_csrf]")).isNotEmpty();
        assertThat(contextRows.count()).isEqualTo(count);
    }

    @Test
    @WithMockUser("test-manager")
    void authenticatedDeliveryFormsContainCsrfWithoutChangingQueueState() throws Exception {
        windows.open(contextId,NOW.plusSeconds(3600),0,manager);
        var saved=reports.submit(windowId,fixture.race().getId(),1,UUID.randomUUID(),actor,payload);
        UUID id=tx.execute(status -> deliveryRows.findByOperationKey(saved.reportId()+":RECEIPT").orElseThrow().getId());
        String review=mvc.perform(get("/admin/incident-deliveries/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(review).select("form input[name=_csrf]")).hasSize(1);
        tx.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));
        String overview=mvc.perform(get("/admin/incident-contexts/"+contextId+"/deliveries")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(overview).select("form input[name=_csrf]")).hasSize(1);
        tx.executeWithoutResult(status -> assertThat(deliveryRows.findById(id).orElseThrow().getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.PENDING));
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    private String path(String route) {
        return route.replace("/context", "/" + contextId).replace("/match/", "/" + fixture.match().getId() + "/").replace("/unknown/", "/" + UUID.randomUUID() + "/");
    }
}
