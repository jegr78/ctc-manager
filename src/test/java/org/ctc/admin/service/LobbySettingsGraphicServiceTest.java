package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.service.PhaseTestFixtures;
import java.util.UUID;
import org.ctc.domain.model.TimeProgressionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LobbySettingsGraphicServiceTest {
    @TempDir Path directory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Custom S01; S01; ?; R03; R03; C06", "Custom ?; S01; S01; ?; R08; R08; C06; ?"})
    void givenOrderedCustomSelection_whenPreparingLobbyWeather_thenKeepEverySlotIncludingRepeatsAndRandom(String weather) {
        // given / when
        var display = LobbySettingsGraphicService.parseWeather(weather);
        // then
        assertThat(display.weatherMethod()).isEqualTo("Custom Weather");
        assertThat(display.customWeather()).isEqualTo(weather.substring(7));
        assertThat(display.presetWeather()).isEqualTo("—");
    }
    @Test
    void givenOnePresetSelection_whenPreparingLobbyWeather_thenRenderOnlyThatPreset() {
        // given / when
        var display = LobbySettingsGraphicService.parseWeather("Preset R03");
        // then
        assertThat(display.weatherMethod()).isEqualTo("Preset Weather");
        assertThat(display.presetWeather()).isEqualTo("R03"); assertThat(display.customWeather()).isEqualTo("—");
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"'SS, SM, SH',SM,Sports", "'RS, RM, RH, I, W',RM,Racing"})
    void givenSavedOneMakeTyres_whenRenderingLobby_thenShowActualFamilyAndKeepExactCompoundText(String available, String mandatory, String family) throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = oneMakeRace(); race.getSettings().setAvailableTyres(available); race.getSettings().setMandatoryTyres(mandatory);
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = service.renderPublication(race);
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        var values = document.select(".row").stream().collect(java.util.stream.Collectors.toMap(
                row -> row.select(".set-label").text(), row -> row.select(".set-value").text(), (first, next) -> first));
        // then
        assertThat(values).containsEntry("Usable Tyres", family).containsEntry("Usable Tyre & Types", available)
                .containsEntry("Required Tyre Type", mandatory);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"'RS, I',I,false", "'RS, W',W,false", "'RS, SS',None,false", "SS,None,true"})
    void givenInvalidTyreSettings_whenRenderingLobby_thenRejectBeforeAnyGraphic(String available, String mandatory, boolean category) throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = oneMakeRace(); race.getSettings().setAvailableTyres(available); race.getSettings().setMandatoryTyres(mandatory);
        if (category) race.getCarPoolEntry().setOneMakeCar(null);
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.renderPublication(race))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        verify(service, never()).renderScreenshot(anyString(), any(Path.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"GR_1,Gr.1", "GR_2,Gr.2", "GR_3,Gr.3", "GR_4,Gr.4", "GR_B,Gr.B", "GR_N,—", "GR_X,—", ",—"})
    void givenSelectedCarMetadata_whenRenderingLobby_thenUseOnlySupportedGt7CategoryRegardlessOfPoolName(
            CarCategory category, String expected) throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = oneMakeRace(); race.getCar().setCategory(category); race.getCar().setName("Test misleading Gr.1 name");
        race.getCarPoolEntry().setLabel("Test misleading Gr.B pool");
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = service.renderPublication(race);
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        // then
        var row = document.select(".row").stream().filter(value -> value.select(".set-label").text().equals("Filter by Category"))
                .findFirst().orElseThrow();
        assertThat(row.select(".set-value").text()).isEqualTo(expected);
        assertThat(document.text()).doesNotContain("Test misleading Gr.1 name", "Test misleading Gr.B pool");
        assertThat(graphic.fields()).doesNotContain(PickDisclosureField.CAR);
    }

    private Race oneMakeRace() {
        var race = readyRace(); var season = race.getMatchday().getSeason();
        season.setId(UUID.randomUUID()); season.setLobbyRulesProfile(LobbyRulesProfile.CTC_2027);
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL); season.setCarPoolState(CarPoolState.FROZEN);
        var car = new Car("Test tyre car", "Test manufacturer"); car.setId(UUID.randomUUID()); race.setCar(car);
        var entry = new SeasonCarPoolEntry(); entry.setId(UUID.randomUUID()); entry.setSeason(season); entry.setOneMakeCar(car);
        race.setCarPoolEntry(entry); return race;
    }

    @Test
    void givenLegacyRaceWithForbiddenStart_whenRendering_thenRejectBeforeProducingAGraphic() throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), "Test graphic bytes"); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        var race = readyRace(); race.getSettings().setStartType(RaceStartType.ROLLING);
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.renderPublication(race))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("season rules");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenBuiltinPublicationTemplate_whenRendering_thenFreezeProvenFieldsAndBytesOutsideTheInternalCache(boolean equal) throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = readyRace();
        race.setPicksManaged(true);
        race.setCar(new Car("Test private lobby car", "Must stay omitted"));
        race.getSettings().setTimeProgressionMode(equal ? TimeProgressionMode.EQUAL_CONDITIONS : TimeProgressionMode.MULTIPLIER);
        race.getSettings().setTimeProgressionMultiplier(equal ? null : 0);
        var renderedPath = new java.util.concurrent.atomic.AtomicReference<Path>();
        doAnswer(call -> {
            String html = call.getArgument(0); Path output = call.getArgument(1);
            java.nio.file.Files.writeString(output, html); renderedPath.set(output); return null;
        }).when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.renderPublication(race));
        // then
        org.assertj.core.api.Assertions.assertThat(graphic).isNotNull();
        org.assertj.core.api.Assertions.assertThat(graphic.fields()).containsExactlyInAnyOrderElementsOf(org.ctc.domain.model.PickDisclosureField.lobby());
        org.assertj.core.api.Assertions.assertThat(graphic.templateHash()).matches("[0-9a-f]{64}");
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("Test Track", equal ? "Equal Conditions" : "0x").doesNotContain("Test private lobby car", "Must stay omitted");
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        var values = document.select(".row").stream().collect(java.util.stream.Collectors.toMap(
                row -> row.select(".set-label").text(), row -> row.select(".set-value").text(), (first, next) -> first));
        assertThat(values).containsEntry("No. of Laps", "5").containsEntry("Tyre Wear Rate", "2×")
                .containsEntry("Fuel Consumption Rate", "2×").containsEntry("Refuelling Speed", "1 Litre/sec")
                .containsEntry("Initial Fuel", "Full").containsEntry("Preset Weather", "Clear").containsEntry("Time of the Day", "Day")
                .containsEntry("Usable Tyre & Types", "RH").containsEntry("Required Tyre Type", "RH");
        byte[] copy = graphic.png(); java.util.Arrays.fill(copy, (byte) 0);
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8)).contains("Test Track");
        org.assertj.core.api.Assertions.assertThat(renderedPath.get()).doesNotExist();
        org.assertj.core.api.Assertions.assertThat(directory.resolve("races").resolve(race.getId().toString()).resolve("lobby-settings.png")).doesNotExist();
    }

    @Test
    void givenManagedRaceAndUnknownCustomTemplate_whenPublishing_thenKeepExplicitOutputWithoutInferringPublicFields() throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = readyRace();
        race.setPicksManaged(true); service.saveTemplate("<html><p th:text='${v.weather}'></p></html>");
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when / then
        var graphic = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.renderPublication(race));
        assertThat(graphic.fields()).isEmpty();
        assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8)).contains("Clear");
    }

    @Test
    void givenUnenrolledRaceAndCustomTemplate_whenPublishing_thenPreserveLegacyOutputWithoutClaimingAnyDisclosedFields() throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = readyRace();
        service.saveTemplate("<html><p>Test legacy custom output</p></html>");
        doAnswer(call -> {
            java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null;
        }).when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.renderPublication(race));
        // then
        org.assertj.core.api.Assertions.assertThat(graphic).isNotNull();
        org.assertj.core.api.Assertions.assertThat(graphic.fields()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8)).contains("Test legacy custom output");
    }

    @Test
    void givenEqualAndZero_whenRendering_thenTemplateReceivesDistinctValues() throws Exception {
        // given
        var engine = mock(TemplateEngine.class);
        var service = spy(new LobbySettingsGraphicService(engine, directory.toString()));
        doNothing().when(service).renderScreenshot(anyString(), any(Path.class));
        when(engine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
        var race = readyRace();
        // when
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.EQUAL_CONDITIONS);
        race.getSettings().setTimeProgressionMultiplier(null);
        service.generateLobbySettings(race);
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.MULTIPLIER);
        race.getSettings().setTimeProgressionMultiplier(0);
        service.generateLobbySettings(race);
        // then
        var contexts = ArgumentCaptor.forClass(Context.class);
        verify(engine, times(2)).process(anyString(), contexts.capture());
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(0).getVariable("v")).get("equalConditionsMode")).isEqualTo("On");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(1).getVariable("v")).get("equalConditionsMode")).isEqualTo("Off");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(0).getVariable("v")).get("timeSpeed")).isEqualTo("Equal Conditions");
        assertThat(((java.util.Map<?, ?>) contexts.getAllValues().get(1).getVariable("v")).get("timeSpeed")).isEqualTo("0x");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"LEGACY,GRID_FALSE_START,REAL,Off", "CTC_2027,ROLLING,WEAK,On"})
    void givenVersionedRaceRules_whenRenderingLobby_thenShowCapturedChoicesAndPreserveAllOtherLeagueSettings(
            LobbyRulesProfile profile, RaceStartType start, RaceSlipstream slipstream, String flags) throws Exception {
        // given
        var service = spy(new LobbySettingsGraphicService(mock(TemplateEngine.class), directory.toString()));
        var race = readyRace(); race.getMatchday().getSeason().setLobbyRulesProfile(profile);
        race.getSettings().setStartType(start); race.getSettings().setSlipstream(slipstream);
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = service.renderPublication(race);
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        var values = document.select(".row").stream().collect(java.util.stream.Collectors.toMap(
                row -> row.select(".set-label").text(), row -> row.select(".set-value").text(), (first, next) -> first));
        // then
        assertThat(values).containsEntry("Start Type", start.getLabel()).containsEntry("Slipstream Strength", slipstream.getLabel())
                .containsEntry("Flag Rules", flags).containsEntry("Mechanical Damage", "Light")
                .containsEntry("Race Finish Delay", "180 second(s)").containsEntry("Qualifying Continuation Time", "60 Seconds")
                .containsEntry("Slipstream Strength (Qualifier)", "Off").containsEntry("Engine Swap", "Prohibited")
                .containsEntry("Pit Lane Line Cutting Penalty", "On");
        var qualifier = document.select("section").stream().filter(section -> section.select(".sec-title").text().equals("Qualifier Settings"))
                .findFirst().orElseThrow();
        assertThat(qualifier.text()).contains("10 Minute(s)");
        assertThat(graphic.fields()).contains(PickDisclosureField.START_TYPE, PickDisclosureField.SLIPSTREAM);
    }

    private org.ctc.domain.model.Race readyRace() {
        var season = new Season("Test", 2026, 1);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(PhaseTestFixtures.matchdayInRegularPhase(season, "Test-MD1", 1));
        race.setHomeTeamOverride(new Team("Test Home", "TH"));
        race.setAwayTeamOverride(new Team("Test Away", "TA"));
        race.setTrack(new Track("Test Track"));
        var settings = new RaceSettings(race);
        settings.setNumberOfLaps(5);
        settings.setTimeProgressionMultiplier(0);
        settings.setTyreWearMultiplier(2);
        settings.setFuelConsumptionMultiplier(2);
        settings.setRefuelingSpeed(1);
        settings.setInitialFuel("Full");
        settings.setNumberOfRequiredPitStops(0);
        settings.setWeather("Clear");
        settings.setTimeOfDay("Day");
        settings.setAvailableTyres("RH");
        settings.setMandatoryTyres("RH");
        race.setSettings(settings);
        return race;
    }

}
