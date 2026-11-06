package org.ctc.admin.controller;

import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dsqsecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles(value = "prod", inheritProfiles = false)
@Tag("integration")
class DisqualificationSecurityIT extends RacePenaltyFixture {
    @Autowired MockMvc mvc;
    @Autowired org.ctc.domain.service.RaceDisqualificationService disqualifications;
    @Autowired org.ctc.domain.repository.RaceRepository raceRows;
    private java.util.UUID resultId() { return tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().getFirst().getId()); }

    @Test
    void givenAnonymousUser_whenReadingOrWritingDisqualification_thenRequireAuthenticationWithoutMutation() throws Exception {
        // when / then
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(disqualifications.view(raceId, resultId()).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    @WithMockUser("manager")
    void givenAuthenticatedManager_whenPostingWithoutCsrf_thenRejectAndRenderTokenOnNativeForm() throws Exception {
        // when / then
        mvc.perform(post(path())).andExpect(status().isForbidden());
        var html = mvc.perform(get(path())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(org.jsoup.Jsoup.parse(html).select("form input[name=_csrf]")).hasSize(1);
        assertThat(disqualifications.view(raceId, resultId()).history()).isEmpty(); assertScores(80, 70);
    }

    private String path() { return "/admin/races/" + raceId + "/results/" + resultId() + "/disqualification"; }
}
