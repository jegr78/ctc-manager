package org.ctc.admin.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Tag("integration")
class IncidentManagementIT extends IncidentIntegrationFixture {
    @Autowired MockMvc mvc;
    @MockitoBean org.ctc.discord.DiscordRestClient discord;

    @Test
    void contextPreviewShowsPlannedStartAndDefaultWithoutOpeningReporting() throws Exception {
        var response = mvc.perform(get(path())).andExpect(status().isOk()).andExpect(view().name("admin/incident-context"))
                .andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        assertThat(html.text()).contains("Incident reporting", "Europe/Berlin", "24 elapsed hours");
        assertThat(html.selectFirst("input[name=deadline]").val()).startsWith("2035-01-02T20:00");
        assertThat(html.selectFirst("input[name=offset]").val()).isEqualTo("+01:00");
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    @Test
    void regularMatchAliasResolvesTheCanonicalContext() throws Exception {
        mvc.perform(get("/admin/matches/" + fixture.match().getId() + "/incidents"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
    }

    @Test
    void nativeOpeningConfirmsTheChosenDeadlineWithoutSendingDiscordMessages() throws Exception {
        mvc.perform(post(path() + "/window/open").param("deadline", "2035-01-02T20:00").param("offset", "+01:00").param("expectedVersion", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        tx.executeWithoutResult(status -> {
            var saved = windowRows.findById(windowId).orElseThrow();
            assertThat(saved.getDeadline()).isEqualTo(Instant.parse("2035-01-02T19:00:00Z"));
            assertThat(saved.getState()).isEqualTo(IncidentWindowState.OPEN); assertThat(saved.getVersion()).isEqualTo(1);
        });
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    @Test
    void invalidDateRetainsRawInputAndDoesNotOpenTheWindow() throws Exception {
        var response = mvc.perform(post(path() + "/window/open").param("deadline", "not-a-date").param("offset", "+01:00").param("expectedVersion", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("incidentDeadlineForm", "deadline"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(response).selectFirst("input[name=deadline]").val()).isEqualTo("not-a-date");
        assertThat(Jsoup.parse(response).text()).contains("Enter a valid date and time.").doesNotContain("java.time", "java.lang");
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @Test
    void invalidBerlinOffsetKeepsTheEnteredValuesAndReportsTheDomainError() throws Exception {
        var response = mvc.perform(post(path() + "/window/open").param("deadline", "2035-01-02T20:00").param("offset", "+02:00").param("expectedVersion", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(response).text()).contains("UTC offset");
        assertThat(Jsoup.parse(response).selectFirst("input[name=offset]").val()).isEqualTo("+02:00");
        assertThat(windowRows.findById(windowId).orElseThrow().getVersion()).isZero();
    }

    @Test
    void deadlineChangeRequiresAReasonAndRetainsTheSubmittedDeadline() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var response = mvc.perform(post(path() + "/window/deadline").param("deadline", "2035-01-03T20:00").param("offset", "+01:00")
                        .param("expectedVersion", "1").param("reason", ""))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm")).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(response).text()).contains("reason");
        assertThat(Jsoup.parse(response).selectFirst("input[name=deadline]").val()).startsWith("2035-01-03T20:00");
        assertThat(windowRows.findById(windowId).orElseThrow().getDeadline()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void staleDeadlineChangeCannotOverwriteTheCurrentWindow() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var response = mvc.perform(post(path() + "/window/deadline").param("deadline", "2035-01-03T20:00").param("offset", "+01:00")
                        .param("expectedVersion", "0").param("reason", "Test stale change"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm")).andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        assertThat(html.text()).contains("changed"); assertThat(html.selectFirst("input[name=expectedVersion]").val()).isEqualTo("0");
        assertThat(html.selectFirst("textarea[name=reason]").text()).isEqualTo("Test stale change");
        assertThat(windowRows.findById(windowId).orElseThrow().getDeadline()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void savedActiveAndWithdrawnCasesAreEscapedAndRemainVisible() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var receipt = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.withdraw(receipt.reportId(), 1, 1, UUID.randomUUID(), actor);
        var active = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        var response = mvc.perform(get(path())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("<private>");
        var html = Jsoup.parse(response); assertThat(html.text()).contains(receipt.caseReference(), "Withdrawn", "Active", active.caseReference(), payload.getDescription(), "00:00");
        assertThat(html.select("a[href='/admin/races/" + fixture.race().getId() + "']")).hasSize(2);
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    @Test
    void standalonePlayoffHasAnExplicitNativeBindingAndKeepsItsOwnIdentity() throws Exception {
        var target = playoffTarget(); var route = "/admin/playoffs/matchup/" + target.get(0) + "/incidents";
        mvc.perform(get(route)).andExpect(status().isOk()).andExpect(view().name("admin/incident-context"));
        mvc.perform(post(route + "/bind").param("guildId", GUILD).param("channelId", "200000000000000099").param("expectedVersion", "0")
                        .param("match", fixture.match().getId().toString()))
                .andExpect(status().is3xxRedirection());
        tx.executeWithoutResult(status -> {
            var context = contextRows.findByPlayoffMatchupId(target.get(0)).orElseThrow();
            assertThat(context.getMatch()).isNull(); assertThat(context.getPlayoffMatchup().getId()).isEqualTo(target.get(0));
            assertThat(windowRows.findByContextId(context.getId()).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
            assertThat(matches.findByMatchdayId(target.get(1))).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "fold", "past"})
    void unavailableOrPastSuggestionNeverCreatesAnInventedDeadline(String start) throws Exception {
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setDateTime(switch (start) {
            case "fold" -> LocalDateTime.of(2026, 10, 25, 2, 30);
            case "past" -> LocalDateTime.of(2026, 1, 1, 20, 0);
            default -> null;
        }));
        var response = mvc.perform(get(path())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        if (start.equals("past")) {
            assertThat(html.text()).contains("already passed"); assertThat(html.selectFirst("input[name=deadline]").val()).startsWith("2026-01-02T20:00");
        } else {
            assertThat(html.text()).contains("explicit future deadline"); assertThat(html.selectFirst("input[name=deadline]").val()).isEmpty();
        }
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @Test
    void nonexistentBerlinDeadlineIsRejectedBeforePersistence() throws Exception {
        var result = mvc.perform(post(path() + "/window/open").param("deadline", "2027-03-28T02:30").param("offset", "+01:00").param("expectedVersion", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm")).andReturn();
        var errors = (org.springframework.validation.BindingResult) result.getModelAndView().getModel().get("org.springframework.validation.BindingResult.incidentDeadlineForm");
        assertThat(errors.getGlobalError().getDefaultMessage()).isEqualTo("The chosen UTC offset is not valid for that Berlin deadline");
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @Test
    void regularWindowAliasesUseTheSameConfirmationHistoryAndRetainThePlannedSnapshot() throws Exception {
        String alias = "/admin/matches/" + fixture.match().getId() + "/incidents/window/";
        mvc.perform(post(alias + "open").param("deadline", "2035-01-02T20:00").param("offset", "+01:00").param("expectedVersion", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setDateTime(LocalDateTime.of(2040, 1, 1, 20, 0)));
        mvc.perform(post(alias + "deadline").param("deadline", "2035-01-03T20:00").param("offset", "+01:00").param("expectedVersion", "1").param("reason", "Test management adjustment"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        tx.executeWithoutResult(status -> {
            var window = windowRows.findById(windowId).orElseThrow();
            assertThat(window.getDeadline()).isEqualTo(Instant.parse("2035-01-03T19:00:00Z"));
            assertThat(window.getPlannedStartLocal()).isEqualTo(LocalDateTime.of(2035, 1, 1, 20, 0));
            assertThat(window.getVersion()).isEqualTo(2);
            assertThat(eventRows.findByWindowIdOrderByAcceptedAtAscIdAsc(windowId)).anySatisfy(event -> assertThat(event.getReason()).isEqualTo("Test management adjustment"));
        });
    }

    @Test
    void expiredReportingRejectsNativeDeadlineChangesAndRetainsTheirReason() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager); clock.set(NOW.plusSeconds(3600));
        var response = mvc.perform(post(path() + "/window/deadline").param("deadline", "2035-01-03T20:00").param("offset", "+01:00")
                        .param("expectedVersion", "1").param("reason", "Test expired change"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm")).andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        assertThat(html.text()).contains("closed", "Expired"); assertThat(html.selectFirst("textarea[name=reason]").text()).isEqualTo("Test expired change");
        assertThat(windowRows.findById(windowId).orElseThrow().getDeadline()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void closedReportingCannotBeReopenedThroughTheNativeForm() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager); clock.set(NOW.plusSeconds(3600)); windows.closeDue(windowId, NOW);
        mvc.perform(post(path() + "/window/open").param("deadline", "2035-01-03T20:00").param("offset", "+01:00").param("expectedVersion", "2"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("incidentDeadlineForm"));
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.CLOSED);
    }

    @Test
    void existingPairingDetailPagesLinkToTheIncidentWorkspace() throws Exception {
        var match = mvc.perform(get("/admin/matches/" + fixture.match().getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(match).select("a[href='/admin/matches/" + fixture.match().getId() + "/incidents']")).hasSize(1);
        var target = playoffTarget();
        var playoff = mvc.perform(get("/admin/playoffs/matchup/" + target.get(0))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(playoff).select("a[href='/admin/playoffs/matchup/" + target.get(0) + "/incidents']")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"open", "deadline"})
    void nativeWindowRequiresAnExplicitVersion(String operation) throws Exception {
        if (operation.equals("deadline")) windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        mvc.perform(post(path() + "/window/" + operation).param("deadline", "2035-01-03T20:00").param("offset", "+01:00").param("reason", "Test missing version"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("incidentDeadlineForm", "expectedVersion"));
        assertThat(windowRows.findById(windowId).orElseThrow().getVersion()).isEqualTo(operation.equals("open") ? 0 : 1);
    }

    @Test
    void nativeBindingRequiresAnExplicitVersion() throws Exception {
        var target = playoffTarget();
        mvc.perform(post("/admin/playoffs/matchup/" + target.get(0) + "/incidents/bind").param("guildId", GUILD).param("channelId", "200000000000000099"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("incidentBindingForm", "expectedVersion"));
        assertThat(contextRows.findByPlayoffMatchupId(target.get(0))).isEmpty();
    }

    private List<UUID> playoffTarget() {
        return tx.execute(status -> {
            var playoff = helper.createPlayoffInPhase(seasons.findById(fixture.season().getId()).orElseThrow(), "Test incident native playoff", 2);
            var matchup = playoff.getRounds().getFirst().getMatchups().getFirst();
            matchup.setTeam1(teams.findById(fixture.homeTeam().getId()).orElseThrow()); matchup.setTeam2(teams.findById(fixture.awayTeam().getId()).orElseThrow());
            var day = new Matchday(playoff.getPhase(), "Test incident native round", 1); em.persist(day);
            var race = new Race(); race.setMatchday(day); race.setPlayoffMatchup(matchup); race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); em.persist(race); em.flush();
            return List.of(matchup.getId(), day.getId());
        });
    }
    private String path() { return "/admin/incident-contexts/" + contextId; }
}
