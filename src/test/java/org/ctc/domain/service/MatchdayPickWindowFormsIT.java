package org.ctc.domain.service;

import org.ctc.domain.model.Matchday;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@AutoConfigureMockMvc
class MatchdayPickWindowFormsIT extends PickPersistenceIT {
    @Autowired MockMvc mvc;
    @Autowired PickWindowService windows;

    private String dayPath() { return "/admin/matchdays/" + fixture.matchday().getId(); }
    private Document page(String path) throws Exception { return Jsoup.parse(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private MockHttpServletRequestBuilder tokens(String path, Document doc) {
        var request = post(path).principal(() -> "test-window-manager");
        for (String name : new String[]{"expectedVersion", "expectedScope", "expectedGeneration"})
            request.param(name, doc.selectFirst("input[name=" + name + "]").val());
        return request;
    }

    @Test
    void givenPreparedClosedMatchday_whenOpeningViaNativeSwitch_thenChangeOnlyItsCentralWindowAndRejectAStalePost() throws Exception {
        tx.executeWithoutResult(status -> entityManager.find(Matchday.class, fixture.matchday().getId()).setPicksOpen(false));
        var doc = page(dayPath() + "/edit-pairings");
        assertThat(doc.selectFirst("input[name=picksOpen]").hasAttr("checked")).isFalse();
        assertThat(doc.selectFirst("input[name=picksOpen]").attr("role")).isEqualTo("switch");
        mvc.perform(tokens(dayPath() + "/save-pairings", doc).param("id", fixture.matchday().getId().toString())
                        .param("picksOpen", "true").param("pickDeadline", "2035-01-01T12:00").param("scheduledWeekend", "Test event"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(dayPath()));
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId())).isEmpty();
        mvc.perform(tokens(dayPath() + "/save-pairings", doc).param("id", fixture.matchday().getId().toString())
                        .param("scheduledWeekend", "Stale test value"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
        var overview = page(dayPath());
        assertThat(overview.selectFirst("[data-testid=matchday-picks-state]").text()).isEqualTo("Open");
        assertThat(overview.selectFirst("[data-testid=edit-pairings]").text()).isEqualTo("Manage picks and deadline");
    }

    @Test
    void givenMatchDeadlineForm_whenSavingAndClearingAnException_thenKeepItsMatchdayDefaultAndBothLegsScope() throws Exception {
        String path = "/admin/pick-windows/matches/" + fixture.match().getId();
        var doc = page(path);
        assertThat(doc.text()).contains("both legs", "does not open a closed matchday", "Matchday default");
        mvc.perform(tokens(path, doc).param("pickDeadlineOverride", "2036-01-01T20:00"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/matches/" + fixture.match().getId()));
        assertThat(windows.readPairing(fixture.match().getId(), false).override()).isEqualTo(java.time.LocalDateTime.of(2036, 1, 1, 20, 0));
        assertThat(windows.readMatchday(fixture.matchday().getId()).deadline()).isEqualTo(fixture.matchday().getPickDeadline());
        doc = page(path);
        assertThat(doc.selectFirst("input[name=pickDeadlineOverride]").val()).startsWith("2036-01-01T20:00");
        mvc.perform(tokens(path, doc).param("pickDeadlineOverride", "")).andExpect(status().is3xxRedirection());
        assertThat(windows.readPairing(fixture.match().getId(), false).override()).isNull();
    }

    @Test
    void givenInvalidDeadlineOrMissingTokens_whenPosting_thenPreserveTheSubmittedFieldsAndLeaveTheWindowUnchanged() throws Exception {
        var doc = page(dayPath() + "/edit-pairings");
        mvc.perform(tokens(dayPath() + "/save-pairings", doc).param("id", fixture.matchday().getId().toString())
                        .param("pickDeadline", "2027-03-28T02:30").param("scheduledWeekend", "Test retained value"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test retained value")));
        mvc.perform(post(dayPath() + "/save-pairings").param("id", fixture.matchday().getId().toString()).param("picksOpen", "false"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "expectedVersion", "expectedScope", "expectedGeneration"));
        assertThat(windows.readMatchday(fixture.matchday().getId()).open()).isTrue();
    }
}
