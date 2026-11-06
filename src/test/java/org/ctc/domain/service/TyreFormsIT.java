package org.ctc.domain.service;

import java.util.Set;
import java.util.stream.Collectors;
import org.ctc.domain.model.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_tyre_forms;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class TyreFormsIT extends TyrePersistenceIT {
    @Autowired MockMvc mvc;

    @Test
    void given2027ExplicitOneMake_whenOpeningNativeEditor_thenOfferBothFamiliesAndOnlyDryMandatoryCompounds() throws Exception {
        // given / when
        var page = editor();
        // then
        assertThat(options(page, "availableTyres")).containsExactlyInAnyOrder("RS", "RM", "RH", "SS", "SM", "SH");
        assertThat(options(page, "mandatoryTyres")).containsExactly("None");
        assertThat(Jsoup.parse(page).select("select#availableTyres[multiple]")).hasSize(1);
        assertThat(Jsoup.parse(page).select("select#mandatoryTyres[multiple]")).hasSize(1);
    }

    @Test
    void givenSavedSingletonCategoryWithOneMakeInItsLabel_whenOpening_thenOfferOnlyRacing() throws Exception {
        // given
        tx.executeWithoutResult(status -> {
            var entry = entityManager.find(SeasonCarPoolEntry.class, payload.carPoolEntryId());
            var car = cars.findById(payload.carId()).orElseThrow(); car.setCategory(CarCategory.values()[0]);
            entry.setOneMakeCar(null);
            var clause = new SeasonCarPoolClause(); clause.setEntry(entry); clause.setCategory(car.getCategory()); entry.getClauses().add(clause);
            assertThat(entry.getMembers()).hasSize(1);
        });
        // when
        var page = editor();
        // then
        assertThat(options(page, "availableTyres")).containsExactlyInAnyOrder("RS", "RM", "RH");
        assertThat(options(page, "mandatoryTyres")).containsExactly("None");
    }

    @Test
    void givenPreviousRulesAndExplicitOneMake_whenOpening_thenKeepRacingOnly() throws Exception {
        // given
        rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.CTC_2027, LobbyRulesProfile.LEGACY);
        // when
        var page = editor();
        // then
        assertThat(options(page, "availableTyres")).containsExactlyInAnyOrder("RS", "RM", "RH");
        assertThat(options(page, "mandatoryTyres")).containsExactly("None");
    }

    @Test
    void givenOnlyRacingHardSelected_whenSavingNativeInput_thenPersistAutomaticWetTyresAndLimitMandatoryChoices() throws Exception {
        // given
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).setPicksManaged(false));
        // when
        mvc.perform(save(new String[]{"RH"}, "RH")).andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
        // then
        tx.executeWithoutResult(status -> {
            var settings = entityManager.find(Race.class, fixture.race().getId()).getSettings();
            assertThat(settings.getAvailableTyres()).isEqualTo("RH, I, W");
            assertThat(settings.getMandatoryTyres()).isEqualTo("RH");
        });
        var page = editor();
        assertThat(options(page, "mandatoryTyres")).containsExactlyInAnyOrder("None", "RH");
        assertThat(Jsoup.parse(page).select("#availableTyres option[selected]").eachAttr("value")).containsExactly("RH");
    }

    @Test
    void givenRacingAvailabilityAndSportsMandatory_whenSaving_thenRejectAndRetainTheInvalidValue() throws Exception {
        // given / when
        var response = mvc.perform(save(new String[]{"RS", "RM", "RH"}, "SS"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        // then
        var page = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(response.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(page).select("#mandatoryTyres option[selected]").eachAttr("value")).containsExactly("SS");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @Test
    void givenRepeatedNativeFormValues_whenSavingSports_thenBindEveryCompoundAndReloadItsSelection() throws Exception {
        // given
        tx.executeWithoutResult(status -> entityManager.find(Race.class, fixture.race().getId()).setPicksManaged(false));
        // when
        mvc.perform(save(new String[]{"SS", "SM", "SH"}, "SM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?matchdayId=" + fixture.matchday().getId()))
                .andExpect(flash().attributeExists("successMessage"));
        // then
        tx.executeWithoutResult(status -> {
            var settings = entityManager.find(Race.class, fixture.race().getId()).getSettings();
            assertThat(settings.getAvailableTyres()).isEqualTo("SS, SM, SH");
            assertThat(settings.getMandatoryTyres()).isEqualTo("SM");
        });
        var page = editor();
        assertThat(Jsoup.parse(page).select("#availableTyres option[selected]").eachAttr("value"))
                .containsExactlyInAnyOrder("SS", "SM", "SH");
    }

    @Test
    void givenMixedNativeFamilyValues_whenSaving_thenRetainRejectedChoicesAndLeaveSettingsUnchanged() throws Exception {
        // given / when
        var response = mvc.perform(save(new String[]{"RS", "SS"}, "None"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/edit"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("family")))
                .andReturn();
        // then
        var page = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(response.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(page).select("#availableTyres option[selected]").eachAttr("value"))
                .containsExactlyInAnyOrder("RS", "SS");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @Test
    void givenUnknownMarkupInNativeValues_whenSaving_thenEscapeAndRetainTheRejectedValueWithoutCreatingSettings() throws Exception {
        // given
        String submitted = "<img src=x onerror=alert(1)>";
        // when
        var response = mvc.perform(save(new String[]{submitted}, "None"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + fixture.race().getId() + "/edit"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("tyre")))
                .andReturn();
        // then
        var page = mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId()).flashAttrs(response.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = Jsoup.parse(page);
        assertThat(document.select("#availableTyres option[selected]").eachAttr("value")).containsExactly(submitted);
        assertThat(document.select("img[onerror]")).isEmpty();
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull());
    }

    @Test
    void givenApprovedOneMake_whenPreparingCorrection_thenOfferBothAvailableFamiliesAndOnlyDryMandatoryValues() throws Exception {
        // given
        approveForCorrection();
        // when
        var page = mvc.perform(get("/admin/matchday-picks/{id}/correction", fixture.race().getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(Jsoup.parse(page).select("input[name=availableTyres]").eachAttr("value"))
                .containsExactlyInAnyOrder("RS", "RM", "RH", "SS", "SM", "SH");
        assertThat(Jsoup.parse(page).select("input[name=mandatoryTyres]").eachAttr("value"))
                .containsExactlyInAnyOrder("RS", "RM");
    }

    @Test
    void givenApprovedRacingAndSavedSingletonCategory_whenPreparingCorrection_thenOfferOnlyRacing() throws Exception {
        // given
        approveForCorrection();
        tx.executeWithoutResult(status -> {
            var entry = entityManager.find(SeasonCarPoolEntry.class, payload.carPoolEntryId());
            var car = cars.findById(payload.carId()).orElseThrow(); car.setCategory(CarCategory.values()[0]);
            entry.setOneMakeCar(null);
            var clause = new SeasonCarPoolClause(); clause.setEntry(entry); clause.setCategory(car.getCategory()); entry.getClauses().add(clause);
            assertThat(entry.getMembers()).hasSize(1);
        });
        // when
        var page = mvc.perform(get("/admin/matchday-picks/{id}/correction", fixture.race().getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(Jsoup.parse(page).select("input[name=availableTyres]").eachAttr("value"))
                .containsExactlyInAnyOrder("RS", "RM", "RH");
        assertThat(Jsoup.parse(page).select("input[name=mandatoryTyres]").eachAttr("value"))
                .containsExactlyInAnyOrder("RS", "RM");
    }

    private void approveForCorrection() {
        submissions.submit(fixture.race().getId(), 0, java.util.UUID.randomUUID(), actor, payload);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test correction form strategy");
    }

    @ParameterizedTest
    @CsvSource({"availableTyres,duplicate", "mandatoryTyres,duplicate", "availableTyres,blank", "mandatoryTyres,blank",
            "availableTyres,indexed-empty", "mandatoryTyres,indexed-empty", "availableTyres,index-gap", "mandatoryTyres,index-gap",
            "availableTyres,indexed-empty-refresh", "mandatoryTyres,indexed-empty-refresh", "availableTyres,index-gap-refresh", "mandatoryTyres,index-gap-refresh"})
    void givenMalformedRawCorrectionValues_whenPosting_thenRenderErrorsWithoutChangingTheApprovedSetup(String field, String kind) throws Exception {
        // given
        approveForCorrection();
        String page = mvc.perform(get("/admin/matchday-picks/{id}/correction", fixture.race().getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var form = Jsoup.parse(page).selectFirst("form[action$='/correction']");
        assertThat(form).isNotNull();
        boolean indexed = kind.startsWith("indexed-") || kind.startsWith("index-gap");
        boolean refresh = kind.endsWith("-refresh");
        var request = post("/admin/matchday-picks/{id}/correction" + (refresh ? "/refresh" : ""), fixture.race().getId()).with(csrf());
        for (var input : form.select("input[name],select[name],textarea[name]")) {
            String name = input.attr("name");
            if (name.equals("_csrf") || name.equals("reason") || name.equals(field)
                    || indexed && name.equals("_" + field) || input.hasAttr("disabled")) continue;
            if (input.tagName().equals("input") && (input.attr("type").equals("checkbox") || input.attr("type").equals("radio"))
                    && !input.hasAttr("checked")) continue;
            request.param(name, input.val());
        }
        request.param("reason", "Test invalid raw tyre values");
        if (indexed) request.param(field + (kind.startsWith("index-gap") ? "[2]" : "[0]"), kind.startsWith("index-gap") ? "RS" : "");
        else request.param(field, kind.equals("duplicate") ? new String[]{"RM", "RM"} : new String[]{""});
        // when / then
        assertThatCode(() -> {
            var response = mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeExists("form"));
            if (!refresh) response.andExpect(model().attributeHasErrors("form"));
        })
                .doesNotThrowAnyException();
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(2);
            assertThat(PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).matches(payload)).isTrue();
        });
    }

    private MockHttpServletRequestBuilder save(String[] available, String... mandatory) {
        return post("/admin/races/save").with(csrf()).param("id", fixture.race().getId().toString())
                .param("matchdayId", fixture.matchday().getId().toString()).param("homeTeamId", fixture.homeTeam().getId().toString())
                .param("awayTeamId", fixture.awayTeam().getId().toString()).param("trackId", payload.trackId().toString())
                .param("carId", payload.carId().toString()).param("carPoolEntryId", payload.carPoolEntryId().toString())
                .param("lobbyRulesProfile", payload.lobbyRulesProfile().name()).param("startType", payload.startType().name())
                .param("slipstream", payload.slipstream().name()).param("numberOfLaps", payload.laps().toString())
                .param("tyreWearMultiplier", payload.tyreWear().toString()).param("fuelConsumptionMultiplier", payload.fuelConsumption().toString())
                .param("refuelingSpeed", payload.refuelingSpeed().toString()).param("initialFuel", payload.initialFuel().settingsValue())
                .param("numberOfRequiredPitStops", payload.requiredPitStops().toString()).param("timeProgressionMode", payload.timeProgressionMode().name())
                .param("timeProgressionMultiplier", payload.timeProgressionMultiplier() == null ? "" : payload.timeProgressionMultiplier().toString())
                .param("weather", payload.weather()).param("timeOfDay", payload.timeOfDay()).param("availableTyres", available)
                .param("mandatoryTyres", mandatory).param("dateTime", fixture.race().getDateTime().toString());
    }

    private String editor() throws Exception {
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            race.setCar(cars.findById(payload.carId()).orElseThrow());
            race.setCarPoolEntry(entityManager.find(SeasonCarPoolEntry.class, payload.carPoolEntryId()));
        });
        return mvc.perform(get("/admin/races/{id}/edit", fixture.race().getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static Set<String> options(String page, String field) {
        return Jsoup.parse(page).select("select#" + field + " option").stream().map(option -> option.attr("value"))
                .filter(value -> !value.isEmpty()).collect(Collectors.toSet());
    }
}
