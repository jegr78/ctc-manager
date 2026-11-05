package org.ctc.admin.controller;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.dto.RaceForm;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonCarPoolService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class RaceCategoryFormIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired RaceRepository races;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService raceService;
    TestHelper.SeasonFixture fixture;
    UUID entry;
    UUID mr;
    UUID rr;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            fixture = helper.createFullSeasonFixture("TFC" + suffix);
            mr = car(suffix, "MR");
            rr = car(suffix, "RR");
            pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            entry = pools.saveEntry(fixture.season().getId(), null, "Test MR/RR <quoted>", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9896)));
            pools.freeze(fixture.season().getId());
        });
    }

    @AfterEach
    void cleanUp() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr));
            teams.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
        });
    }

    @Test
    void givenNativeEntryAndMember_whenSaving_thenPersistConcreteCarAndEntryWithoutChangingUrls() throws Exception {
        // given / when
        mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString())
                        .param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("carPoolEntryId", entry.toString()).param("carId", mr.toString())
                        .param("numberOfLaps", "12").param("timeProgressionMode", "MULTIPLIER").param("timeProgressionMultiplier", "0"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(saved.getCar().getId()).isEqualTo(mr);
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(12);
        });
    }

    @Test
    void givenAnotherRaceConsumesCombinedEntry_whenEditingBlankRace_thenDisableWholeEntryAndExplainWhy() throws Exception {
        // given
        UUID configured = addLeg(false);
        select(configured, fixture.homeTeam().getId(), fixture.awayTeam().getId());
        // when
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId())).andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        var option = document.selectFirst("#carPoolEntryId option[value='" + entry + "']");
        assertThat(option).isNotNull();
        assertThat(option.hasAttr("disabled")).isTrue();
        assertThat(option.text()).contains("already used");
        assertThat(document.text()).contains("Test MR/RR <quoted>");
        assertThat(document.select("quoted")).isEmpty();
    }

    @Test
    void givenInvalidNumericSubmissionAndChangedHome_whenRedirectingBack_thenKeepBindingValuesEntryAndSubmittedAvailability() throws Exception {
        // given
        UUID configured = addLeg(true);
        select(configured, fixture.awayTeam().getId(), fixture.homeTeam().getId());
        var posted = mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.awayTeam().getId().toString())
                        .param("awayTeamId", fixture.homeTeam().getId().toString())
                        .param("carPoolEntryId", entry.toString()).param("carId", rr.toString())
                        .param("numberOfLaps", "bad-laps").param("weather", "Test <rain> \"quoted\"")
                        .param("timeOfDay", "Night").param("availableTyres", "RS, RM"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        // when
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        var model = response.getModelAndView().getModel();
        assertThat(model.get(BindingResult.MODEL_KEY_PREFIX + "raceForm")).isInstanceOf(BindingResult.class);
        var binding = (BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "raceForm");
        assertThat(binding.hasFieldErrors("numberOfLaps")).isTrue();
        var form = (RaceForm) model.get("raceForm");
        assertThat(form.getCarPoolEntryId()).isEqualTo(entry);
        assertThat(form.getCarId()).isEqualTo(rr);
        assertThat(form.getHomeTeamId()).isEqualTo(fixture.awayTeam().getId());
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.selectFirst("#numberOfLaps").attr("value")).isEqualTo("bad-laps");
        assertThat(document.selectFirst("#weather").attr("value")).isEqualTo("Test <rain> \"quoted\"");
        assertThat(document.selectFirst("#carPoolEntryId option[value='" + entry + "']").hasAttr("disabled")).isTrue();
        assertThat(document.selectFirst("#carId option[value='" + rr + "']").hasAttr("selected")).isTrue();
        assertThat(document.selectFirst(".form-validation-errors").text()).contains("Enter a whole number of laps.")
                .doesNotContain("java.lang", "Failed to convert");
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getCarPoolEntry()).isNull());
    }

    @Test
    void givenInvalidDateTime_whenReturningToEditor_thenKeepRawInputWithAnEditableFieldAndHumanError() throws Exception {
        // given
        var posted = mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString())
                        .param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("dateTime", "not-a-date").param("numberOfLaps", "12"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        // when
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.selectFirst("#dateTime").attr("value")).isEqualTo("not-a-date");
        assertThat(document.selectFirst("#dateTime").attr("type")).isEqualTo("text");
        assertThat(document.selectFirst(".form-validation-errors").text()).contains("Enter a valid date and time.")
                .doesNotContain("java.time", "Failed to convert");
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getDateTime()).isNull());
    }

    @Test
    void givenNoJavascript_whenRefreshingEntryChoices_thenRetainSettingsWithoutSavingAndShowMemberCars() throws Exception {
        // given / when
        var posted = mvc.perform(post("/admin/races/refresh-choices").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString())
                        .param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("carPoolEntryId", entry.toString()).param("numberOfLaps", "33"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.select("#carId option").eachAttr("value")).contains(mr.toString(), rr.toString());
        assertThat(document.selectFirst("#numberOfLaps").attr("value")).isEqualTo("33");
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings()).isNull());
    }

    @Test
    void givenDisabledSelectionFields_whenDefaultsAreSubmitted_thenRejectReuseAndAllowExplicitClearing() throws Exception {
        // given
        select(fixture.race().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId());
        UUID independent = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var md = helper.createMatchdayInRegularPhase(season, "Test independent return", 2);
            var match = helper.createMatch(md, fixture.awayTeam(), fixture.homeTeam());
            return helper.createRace(md, match).getId();
        });
        UUID independentMatchday = tx.execute(status -> races.findById(independent).orElseThrow().getMatchday().getId());
        assertThat(raceService.saveRace(independent, independentMatchday, fixture.awayTeam().getId(), fixture.homeTeam().getId(),
                null, mr, null, 12, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0,
                null, null, null, null, entry).success()).isTrue();
        // when
        mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.awayTeam().getId().toString())
                        .param("awayTeamId", fixture.homeTeam().getId().toString())
                        .param("!carPoolEntryId", entry.toString()).param("!carId", rr.toString()).param("numberOfLaps", "99"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("errorMessage", containsString("already")));
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(saved.getHomeTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            assertThat(saved.getCar().getId()).isEqualTo(mr);
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(12);
        });
        // when
        mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString())
                        .param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("carPoolEntryId", "").param("carId", "")
                        .param("!carPoolEntryId", entry.toString()).param("!carId", mr.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(saved.getCar()).isNull();
            assertThat(saved.getCarPoolEntry()).isNull();
        });
    }

    @Test
    void givenUnknownTrack_whenSaveIsRejected_thenKeepSubmittedChoiceAndPersistedSelection() throws Exception {
        // given
        select(fixture.race().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId());
        UUID unknownTrack = UUID.randomUUID();
        // when
        var posted = mvc.perform(post("/admin/races/save").param("lobbyRulesProfile", "LEGACY").param("startType", "GRID_FALSE_START").param("slipstream", "REAL").param("id", fixture.race().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString())
                        .param("awayTeamId", fixture.awayTeam().getId().toString())
                        .param("carPoolEntryId", entry.toString()).param("carId", rr.toString())
                        .param("trackId", unknownTrack.toString()).param("numberOfLaps", "99"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        var response = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        var option = document.selectFirst("#trackId option[value='" + unknownTrack + "']");
        assertThat(option).isNotNull();
        assertThat(option.hasAttr("selected")).isTrue();
        assertThat(option.text()).contains("Unavailable submitted track");
        assertThat(document.selectFirst("#carId option[value='" + rr + "']").hasAttr("selected")).isTrue();
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(saved.getTrack()).isNull();
            assertThat(saved.getCar().getId()).isEqualTo(mr);
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(12);
        });
    }

    @Test
    void givenNoHomeYet_whenReadingAvailability_thenReturnEntriesAndExistingKeysWithoutConsumption() throws Exception {
        // given / when / then
        mvc.perform(get("/admin/races/used-selections").param("seasonId", fixture.season().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.carPoolMode").value("CATEGORY_POOL"))
                .andExpect(jsonPath("$.usedCarIds").isEmpty()).andExpect(jsonPath("$.usedTrackIds").isEmpty())
                .andExpect(jsonPath("$.consumedEntryIds").isEmpty()).andExpect(jsonPath("$.entries[0].id").value(entry.toString()));
    }

    @Test
    void givenUnknownSeason_whenReadingAvailability_thenReturnDomainNotFoundViewAndMessage() throws Exception {
        // given / when / then
        mvc.perform(get("/admin/races/used-selections").param("seasonId", UUID.randomUUID().toString())
                        .param("homeTeamId", fixture.homeTeam().getId().toString()))
                .andExpect(status().isNotFound()).andExpect(view().name("admin/error"))
                .andExpect(model().attribute("message", containsString("Season")));
    }

    private UUID addLeg(boolean reversed) {
        return tx.execute(status -> {
            var original = races.findById(fixture.race().getId()).orElseThrow();
            var race = new Race();
            race.setMatchday(original.getMatchday());
            race.setMatch(original.getMatch());
            if (reversed) {
                race.setHomeTeamOverride(original.getAwayTeam());
                race.setAwayTeamOverride(original.getHomeTeam());
            }
            return races.saveAndFlush(race).getId();
        });
    }

    private void select(UUID race, UUID home, UUID away) {
        assertThat(raceService.saveRace(race, fixture.matchday().getId(), home, away, null, mr, null, 12,
                null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, entry).success()).isTrue();
    }

    private UUID car(String suffix, String name) {
        var car = new Car("Test form " + suffix, name);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.valueOf(name));
        car.setModelYear(9896);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car).getId();
    }
}
