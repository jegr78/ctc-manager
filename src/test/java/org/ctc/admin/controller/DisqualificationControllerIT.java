package org.ctc.admin.controller;

import java.util.UUID;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.service.RaceDisqualificationService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class DisqualificationControllerIT extends RacePenaltyFixture {
    @Autowired MockMvc mvc;
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired RaceRepository raceRows;

    UUID resultId() { return tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
            .filter(r -> r.getDriver().getId().equals(homeDriver)).findFirst().orElseThrow().getId()); }
    String path(UUID id) { return "/admin/races/" + raceId + "/results/" + id + "/disqualification"; }
    MultiValueMap<String,String> form(UUID id) {
        var view = disqualifications.view(raceId, id); var form = new LinkedMultiValueMap<String,String>();
        form.set("disqualified", "true"); form.set("expectedRevision", Integer.toString(view.revision()));
        form.set("fingerprint", view.fingerprint()); form.set("generation", view.generation());
        form.set("reason", "Test private <script>decision</script>"); return form;
    }

    @Test
    @WithMockUser("trusted-manager")
    void givenNativeForm_whenRecordingDsq_thenUseTrustedActorAndEscapeInternalHistory() throws Exception {
        // given
        var id = resultId(); mvc.perform(get(path(id))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Driver disqualification")));
        var form = form(id); form.set("actorId", "forged-manager");
        // when
        mvc.perform(post(path(id)).params(form)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path(id)));
        // then
        var view = disqualifications.view(raceId, id);
        assertThat(view.history()).hasSize(1); assertThat(view.history().getFirst().getActorId()).isEqualTo("trusted-manager");
        var html = mvc.perform(get(path(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;decision&lt;/script&gt;").doesNotContain("<script>decision</script>");
        assertScores(0, 80);
    }

    @Test
    void givenBindingAndStaleDomainErrors_whenSubmitting_thenRetainSubmittedFieldsAndContext() throws Exception {
        // given
        var id = resultId(); var invalid = form(id); invalid.set("disqualified", "invalid");
        // when / then
        mvc.perform(post(path(id)).params(invalid)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("disqualificationForm", "disqualified"))
                .andExpect(content().string(containsString("Test private &lt;script&gt;decision&lt;/script&gt;")));
        var stale = form(id); mvc.perform(post(path(id)).params(form(id))).andExpect(status().is3xxRedirection());
        stale.set("reason", "Test retained correction"); stale.set("disqualified", "false");
        mvc.perform(post(path(id)).params(stale)).andExpect(status().isConflict())
                .andExpect(model().attributeHasErrors("disqualificationForm"))
                .andExpect(content().string(containsString("DSQ history changed")))
                .andExpect(content().string(containsString("Test retained correction")));
        assertThat(disqualifications.view(raceId, id).history()).hasSize(1); assertScores(0, 80);
    }

    @Test
    void givenHistoricalByeResult_whenViewingAndSubmittingDsq_thenRenderDisabledContextAndRejectWithoutMutation() {
        // given
        var id = resultId();
        tx.executeWithoutResult(status -> { var match = matches.findById(matchId).orElseThrow(); match.setBye(true); match.setAwayTeam(null); });
        // when / then
        assertThatCode(() -> {
            var html = mvc.perform(get(path(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var document = org.jsoup.Jsoup.parse(html);
            assertThat(document.text()).contains("Bye", "Corrections require a played race");
            assertThat(document.select("#disqualification-form fieldset[disabled]")).hasSize(1);
            mvc.perform(post(path(id)).params(form(id))).andExpect(status().isConflict())
                    .andExpect(model().attributeHasErrors("disqualificationForm"))
                    .andExpect(content().string(containsString("played race")));
        }).as("historical bye results remain readable and the DSQ action is unavailable").doesNotThrowAnyException();
        assertThat(disqualifications.view(raceId, id).history()).isEmpty(); assertScores(80, 70);
    }
}
