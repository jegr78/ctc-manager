package org.ctc.admin.controller;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TeamRepository;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class LobbyRulesFormIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired RaceRepository races;
    @Autowired TeamRepository teams;
    TestHelper.SeasonFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = tx.execute(status -> helper.createFullSeasonFixture("TLR" + UUID.randomUUID().toString().substring(0, 8)));
    }

    @AfterEach
    void cleanUp() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            teams.deleteAll(java.util.List.of(fixture.homeTeam(), fixture.awayTeam()));
        });
    }

    @Test
    void givenExistingSeason_whenOpeningNativeEditor_thenOfferExplicitLegacyAnd2027RulesWithoutAdoptingScreenshotValues() throws Exception {
        // given / when
        var response = mvc.perform(get("/admin/seasons/{id}/edit", fixture.season().getId()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        var select = document.selectFirst("select[name=lobbyRulesProfile]");
        assertThat(select).as("Native season rules selector").isNotNull();
        assertThat(select.select("option[value=LEGACY]")).hasSize(1);
        assertThat(select.select("option[value=CTC_2027]")).hasSize(1);
        assertThat(select.select("option[selected]").val()).isEqualTo("LEGACY");
        assertThat(document.select("label[for='" + select.id() + "']")).hasSize(1);
    }

    @Test
    void givenLegacyRace_whenOpeningNativeEditor_thenCaptureProfileAndShowHomeStartAndSlipstreamControls() throws Exception {
        // given / when
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.select("input[name=lobbyRulesProfile][value=LEGACY]")).hasSize(1);
        assertThat(document.select("select[name=startType] option[value=GRID_FALSE_START]")).hasSize(1);
        assertThat(document.select("select[name=slipstream] option[value=REAL]")).hasSize(1);
    }
    @ParameterizedTest
    @CsvSource({"GRID_FALSE_START,REAL", "GRID_FALSE_START,WEAK", "ROLLING,REAL", "ROLLING,WEAK"})
    void givenEmptySeason_whenSelecting2027AndSavingChoices_thenPersistBothChoicesWithoutJavaScript(String start, String slipstream) throws Exception {
        // given
        select2027();
        // when
        mvc.perform(post("/admin/races/save").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString()).param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("lobbyRulesProfile", "CTC_2027").param("startType", start).param("slipstream", slipstream)
                        .param("timeProgressionMode", "MULTIPLIER").param("timeProgressionMultiplier", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(saved.getSettings().getStartType()).isEqualTo(RaceStartType.valueOf(start));
            assertThat(saved.getSettings().getSlipstream()).isEqualTo(RaceSlipstream.valueOf(slipstream));
            assertThat(saved.getMatchday().getSeason().getLobbyRulesProfile()).isEqualTo(LobbyRulesProfile.CTC_2027);
        });
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId())).andReturn();
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.select("#startType option[selected]").val()).isEqualTo(start);
        assertThat(document.select("#slipstream option[selected]").val()).isEqualTo(slipstream);
        mvc.perform(post("/admin/seasons/{id}/lobby-rules", fixture.season().getId())
                        .param("expectedProfile", "CTC_2027").param("lobbyRulesProfile", "LEGACY"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        tx.executeWithoutResult(status -> assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLobbyRulesProfile())
                .isEqualTo(LobbyRulesProfile.CTC_2027));
    }

    @Test
    void givenNativeFormFromPreviousProfile_whenSavingAfterProfileChange_thenRejectBeforeCreatingSettings() throws Exception {
        // given
        select2027();
        // when
        var posted = mvc.perform(post("/admin/races/save").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString()).param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL")
                        .param("timeProgressionMode", "MULTIPLIER").param("timeProgressionMultiplier", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("reload"))).andReturn();
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings()).isNull());
        assertThat(Jsoup.parse(response.getResponse().getContentAsString()).select("input[name=lobbyRulesProfile]").val()).isEqualTo("LEGACY");
    }

    @Test
    void givenMissingOrForgedSeasonProfile_whenPosting_thenKeepAnExplicitNativeValidationErrorAndOriginalData() throws Exception {
        // given / when
        for (String value : java.util.List.of("", "UNSUPPORTED")) {
            var posted = mvc.perform(post("/admin/seasons/{id}/lobby-rules", fixture.season().getId())
                            .param("expectedProfile", "LEGACY").param("lobbyRulesProfile", value))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
            var response = mvc.perform(get("/admin/seasons/{id}/edit", fixture.season().getId()).flashAttrs(posted.getFlashMap()))
                    .andExpect(status().isOk()).andReturn();
            // then
            assertThat(Jsoup.parse(response.getResponse().getContentAsString()).text()).contains("Choose a supported rules profile")
                    .doesNotContain("Failed to convert", "java.lang");
        }
        tx.executeWithoutResult(status -> assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLobbyRulesProfile())
                .isEqualTo(LobbyRulesProfile.LEGACY));
    }

    @Test
    void givenAnOldForm_whenExplicitlyRefreshingChoices_thenCaptureTheCurrentProfileWithoutSaving() throws Exception {
        // given
        select2027();
        // when
        var posted = mvc.perform(post("/admin/races/refresh-choices").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString()).param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.select("input[name=lobbyRulesProfile]").val()).isEqualTo("CTC_2027");
        assertThat(document.select("#startType option[value=ROLLING]")).hasSize(1);
        assertThat(document.text()).contains("Flag Rules On");
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings()).isNull());
    }

    @ParameterizedTest
    @CsvSource({"LEGACY,profile", "LEGACY,start", "LEGACY,slipstream", "LEGACY,all",
            "CTC_2027,profile", "CTC_2027,start", "CTC_2027,slipstream", "CTC_2027,all"})
    void givenMissingNativeRulesParameters_whenSaving_thenRejectWithoutInventingDefaults(String profile, String missing) throws Exception {
        // given
        if ("CTC_2027".equals(profile)) select2027();
        var request = post("/admin/races/save").param("id", fixture.race().getId().toString())
                .param("matchdayId", fixture.matchday().getId().toString())
                .param("homeTeamId", fixture.homeTeam().getId().toString()).param("awayTeamId", fixture.awayTeam().getId().toString())
                .param("timeProgressionMode", "MULTIPLIER").param("timeProgressionMultiplier", "0");
        if (!missing.equals("all") && !missing.equals("profile")) request.param("lobbyRulesProfile", profile);
        if (!missing.equals("all") && !missing.equals("start")) request.param("startType", "GRID_FALSE_START");
        if (!missing.equals("all") && !missing.equals("slipstream")) request.param("slipstream", "REAL");
        // when / then
        var posted = mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        var binding = (org.springframework.validation.BindingResult) posted.getFlashMap().get(
                org.springframework.validation.BindingResult.MODEL_KEY_PREFIX + "raceForm");
        assertThat(binding).isNotNull();
        for (String field : switch (missing) {
            case "profile" -> java.util.List.of("lobbyRulesProfile");
            case "start" -> java.util.List.of("startType");
            case "slipstream" -> java.util.List.of("slipstream");
            default -> java.util.List.of("lobbyRulesProfile", "startType", "slipstream");
        }) assertThat(binding.hasFieldErrors(field)).as("Required field %s", field).isTrue();
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings()).isNull());
    }

    void select2027() throws Exception {
        mvc.perform(post("/admin/seasons/{id}/lobby-rules", fixture.season().getId())
                        .param("expectedProfile", "LEGACY").param("lobbyRulesProfile", "CTC_2027"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
    }

}
