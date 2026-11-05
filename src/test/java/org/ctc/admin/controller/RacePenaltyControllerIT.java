package org.ctc.admin.controller;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class RacePenaltyControllerIT extends RacePenaltyFixture {
    @Autowired MockMvc mvc;

    @Test
    @WithMockUser("trusted-manager")
    void givenNativeCorrectionForm_whenSaving_thenUseTrustedActorAndShowEscapedHistory() throws Exception {
        // given
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(content().string(containsString("Team penalty points")));
        var form = form();
        form.set("reason", "<script>private-reason</script>");
        form.set("actorId", "forged-form-actor");
        // when
        mvc.perform(post(path()).params(form))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        // then
        assertScores(70, 70);
        assertThat(penalties.view(raceId).history().getFirst().getActorId()).isEqualTo("trusted-manager");
        var html = mvc.perform(get(path())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;private-reason&lt;/script&gt;").doesNotContain("<script>private-reason</script>");
    }

    @Test
    void givenNegativeValueOrMissingRevision_whenSubmitting_thenKeepFormErrorsWithoutMutation() throws Exception {
        // given
        var negative = form(); negative.set("homePenalty", "-1");
        // when / then
        mvc.perform(post(path()).params(negative)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("penaltyForm", "homePenalty"))
                .andExpect(content().string(containsString("value=\"-1\"")));
        var missing = form(); missing.remove("expectedRevision");
        mvc.perform(post(path()).params(missing)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("penaltyForm", "expectedRevision"));
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    @Test
    void givenChangedRevision_whenPostingOldForm_thenReturnConflictAndPreserveSubmittedValues() throws Exception {
        // given
        var stale = form();
        mvc.perform(post(path()).params(form())).andExpect(status().is3xxRedirection());
        stale.set("homePenalty", "100");
        // when / then
        mvc.perform(post(path()).params(stale)).andExpect(status().isConflict())
                .andExpect(model().attributeHasErrors("penaltyForm"))
                .andExpect(content().string(containsString("Penalty history changed")))
                .andExpect(content().string(containsString("value=\"100\"")));
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(70, 70);
    }

    private String path() { return "/admin/races/" + raceId + "/penalties"; }

    private MultiValueMap<String, String> form() {
        var view = penalties.view(raceId);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("homeTeamId", homeId.toString()); form.add("awayTeamId", awayId.toString());
        form.add("homePenalty", "20"); form.add("awayPenalty", "10");
        form.add("expectedRevision", Integer.toString(view.revision()));
        form.add("fingerprint", view.fingerprint()); form.add("generation", view.generation());
        form.add("reason", "Confirmed steward correction");
        return form;
    }
}
