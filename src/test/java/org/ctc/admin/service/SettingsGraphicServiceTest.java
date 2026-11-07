package org.ctc.admin.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SettingsGraphicServiceTest {

    @Test
    void givenLegacyRaceWithForbiddenSlipstream_whenRendering_thenRejectBeforeProducingAGraphic() throws Exception {
        // given
        var race = createReadyRace(false); race.getSettings().setSlipstream(RaceSlipstream.WEAK);
        service = spy(service);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        doAnswer(call -> { Files.writeString(call.getArgument(1), "Test graphic bytes"); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when / then
        assertThatThrownBy(() -> service.renderPublication(race)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class)
                .hasMessageContaining("season rules");
    }

	@TempDir
	Path tempDir;

	private StandingsService standingsService;
	private PlayoffSeedRepository playoffSeedRepository;
	private TemplateEngine templateEngine;
	private SettingsGraphicService service;

	@BeforeEach
	void setUp() {
		standingsService = mock(StandingsService.class);
		playoffSeedRepository = mock(PlayoffSeedRepository.class);
		templateEngine = mock(TemplateEngine.class);
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
		service = new SettingsGraphicService(templateEngine, standingsService, playoffSeedRepository, tempDir.toString());
	}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenBuiltinPublicationTemplate_whenRendering_thenFreezeProvenFieldsAndBytesOutsideTheInternalCache(boolean equal) throws Exception {
        // given
        service = spy(service);
        var race = createReadyRace(false);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        race.setPicksManaged(true);
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
        org.assertj.core.api.Assertions.assertThat(graphic.fields()).containsExactlyInAnyOrderElementsOf(org.ctc.domain.model.PickDisclosureField.all());
        org.assertj.core.api.Assertions.assertThat(graphic.templateHash()).matches("[0-9a-f]{64}");
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("Toyota", equal ? "Equal Conditions" : "0x");
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(document.select(".setting-value").eachText()).contains(
                "Nurburgring (5 Laps)", "2x / 2x / 1", "Full / 1 l/s", "Day / " + (equal ? "Equal Conditions" : "0x"), "Clear", "All / Medium");
        byte[] copy = graphic.png(); java.util.Arrays.fill(copy, (byte) 0);
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8)).contains("Toyota");
        org.assertj.core.api.Assertions.assertThat(renderedPath.get()).doesNotExist();
        org.assertj.core.api.Assertions.assertThat(tempDir.resolve("races").resolve(race.getId().toString()).resolve("settings.png")).doesNotExist();
    }

    @Test
    void givenManagedRaceAndUnknownCustomTemplate_whenPublishing_thenKeepExplicitOutputWithoutInferringPublicFields() throws Exception {
        // given
        service = spy(service);
        var race = createReadyRace(false);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        race.setPicksManaged(true); service.saveTemplate("<html><p th:text='${weather}'></p></html>");
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when / then
        var graphic = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.renderPublication(race));
        org.assertj.core.api.Assertions.assertThat(graphic.fields()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8)).contains("Clear");
    }

    @Test
    void givenUnenrolledRaceAndCustomTemplate_whenPublishing_thenPreserveLegacyOutputWithoutClaimingAnyDisclosedFields() throws Exception {
        // given
        service = spy(service);
        var race = createReadyRace(false);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"LEGACY,GRID_FALSE_START,REAL,Off", "CTC_2027,ROLLING,WEAK,On"})
    void givenVersionedRules_whenRenderingSettings_thenRenderMatchChoicesWithoutFixedSeasonDefaults(
            LobbyRulesProfile profile, RaceStartType start, RaceSlipstream slipstream, String flags) throws Exception {
        // given
        service = spy(service); var race = createReadyRace(false);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        race.getMatchday().getSeason().setLobbyRulesProfile(profile);
        race.getSettings().setStartType(start); race.getSettings().setSlipstream(slipstream);
        doAnswer(call -> { java.nio.file.Files.writeString(call.getArgument(1), call.getArgument(0)); return null; })
                .when(service).renderScreenshot(anyString(), any(Path.class));
        // when
        var graphic = service.renderPublication(race);
        var document = org.jsoup.Jsoup.parse(new String(graphic.png(), java.nio.charset.StandardCharsets.UTF_8));
        // then
        org.assertj.core.api.Assertions.assertThat(document.select(".setting-value").eachText())
                .contains(start.getLabel() + " / " + slipstream.getLabel())
                .doesNotContain(flags + " / 10 min");
        org.assertj.core.api.Assertions.assertThat(document.select(".setting-label").eachText())
                .doesNotContain("Flag Rules / Qualifying");
        org.assertj.core.api.Assertions.assertThat(graphic.fields()).contains(PickDisclosureField.START_TYPE, PickDisclosureField.SLIPSTREAM);
    }

	@Test
	void givenRaceWithoutTeams_whenGenerateSettings_thenThrowsIllegalState() {
		// given
		var season = new Season("S", 2026, 1); season.setId(UUID.randomUUID());
		var matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);

		// when / then
		assertThatThrownBy(() -> service.generateSettings(race))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void givenLeagueRace_whenGenerateSettings_thenStandingsCalledWithPhaseIdAndNullGroup() throws IOException {
		// given
		var race = createReadyRace(false);
		var phaseId = race.getMatchday().getPhase().getId();

		when(standingsService.calculateStandings(eq(phaseId), isNull()))
				.thenReturn(List.of());

		// when — renderScreenshot may throw after standings call; catch and verify standings fired
		try { service.generateSettings(race); } catch (Exception ignored) {}

		// then
		verify(standingsService).calculateStandings(eq(phaseId), isNull());
	}

	@Test
	void givenGroupsLayoutRace_whenGenerateSettings_thenStandingsCalledWithPhaseAndMatchdayGroup() throws IOException {
		// given
		var race = createReadyRace(true);
		var phaseId = race.getMatchday().getPhase().getId();
		var groupId = race.getMatchday().getGroup().getId();

		when(standingsService.calculateStandings(eq(phaseId), eq(groupId)))
				.thenReturn(List.of());

		// when — renderScreenshot may throw after standings call; catch and verify standings fired
		try { service.generateSettings(race); } catch (Exception ignored) {}

		// then
		verify(standingsService).calculateStandings(eq(phaseId), eq(groupId));
	}


    @Test
    void givenEqualConditionsAndZero_whenGenerating_thenModeAwareValueIsRendered() throws Exception {
        // given
        service = spy(service);
        doNothing().when(service).renderScreenshot(anyString(), any(Path.class));
        var race = createReadyRace(false);
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.EQUAL_CONDITIONS);
        race.getSettings().setTimeProgressionMultiplier(null);
        // when
        service.generateSettings(race);
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.MULTIPLIER);
        race.getSettings().setTimeProgressionMultiplier(0);
        service.generateSettings(race);
        // then
        var contexts = org.mockito.ArgumentCaptor.forClass(Context.class);
        verify(templateEngine, times(2)).process(anyString(), contexts.capture());
        org.assertj.core.api.Assertions.assertThat(contexts.getAllValues().get(0)
                .getVariable("timeProgressionDisplay")).isEqualTo("Equal Conditions");
        org.assertj.core.api.Assertions.assertThat(contexts.getAllValues().get(1)
                .getVariable("timeProgressionDisplay")).isEqualTo("0x");
        org.assertj.core.api.Assertions.assertThat(contexts.getAllValues().get(1)
                .getVariable("timeProgressionMultiplier")).isEqualTo(0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenFixedOrCategoryRace_whenGenerating_thenKeepConcreteCarAndOptionalSavedEntryLabel(boolean category) throws Exception {
        // given
        service = spy(service);
        doNothing().when(service).renderScreenshot(anyString(), any(Path.class));
        var race = createReadyRace(false);
        var car = new Car("Test Honda", "NSX Gr.3 MR");
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.MR);
        race.setCar(car);
        if (category) {
            var entry = new SeasonCarPoolEntry();
            entry.setLabel("Gr.3 MR + Gr.3 RR");
            race.setCarPoolEntry(entry);
        }
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        // when
        service.generateSettings(race);
        // then
        var context = org.mockito.ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("admin/settings-render"), context.capture());
        org.assertj.core.api.Assertions.assertThat(context.getValue().getVariable("carName"))
                .isEqualTo("Test Honda — NSX Gr.3 MR");
        org.assertj.core.api.Assertions.assertThat(context.getValue().getVariable("poolEntryLabel"))
                .isEqualTo(category ? "Gr.3 MR + Gr.3 RR" : null);
        String label = "Gr.3 MR + RR <img src=x onerror='window.injected=1'>";
        context.getValue().setVariable("poolEntryLabel", category ? label : null);
        var document = org.jsoup.Jsoup.parse(service.processStringTemplate(service.loadDefaultTemplate(), context.getValue()));
        if (category) org.assertj.core.api.Assertions.assertThat(document.select(".pool-entry-label").text()).contains(label);
        else org.assertj.core.api.Assertions.assertThat(document.select(".pool-entry-label")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(document.select("img[src=x], [onerror]")).isEmpty();
    }

    @Test
    void givenCategoryEntryWithoutConcreteCar_whenGenerating_thenRejectBeforeRendering() throws Exception {
        // given
        service = spy(service);
        var race = createReadyRace(false);
        var entry = new SeasonCarPoolEntry();
        entry.setLabel("Gr.3 MR + Gr.3 RR");
        race.setCarPoolEntry(entry);
        race.setCar(null);
        // when / then
        assertThatThrownBy(() -> service.generateSettings(race)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Race has no car");
        verify(templateEngine, never()).process(anyString(), any(Context.class));
        verify(service, never()).renderScreenshot(anyString(), any(Path.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenCustomSettingsTemplate_whenRenderingSavedSelection_thenKeepLegacyCarVariableAndEscapeOptionalEntry(boolean category) throws Exception {
        // given
        service = spy(service);
        doNothing().when(service).renderScreenshot(anyString(), any(Path.class));
        var race = createReadyRace(false);
        String label = "Gr.3 MR + RR <img src=x onerror='window.injected=1'>";
        if (category) {
            var entry = new SeasonCarPoolEntry();
            entry.setLabel(label);
            race.setCarPoolEntry(entry);
        }
        service.saveTemplate("<html><p id='car' th:text='${carName}'></p><p id='pool' th:if='${poolEntryLabel != null}' th:text='${poolEntryLabel}'></p></html>");
        when(standingsService.calculateStandings(any(), any())).thenReturn(List.of());
        // when
        service.generateSettings(race);
        // then
        var html = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(service).renderScreenshot(html.capture(), any(Path.class));
        var document = org.jsoup.Jsoup.parse(html.getValue());
        org.assertj.core.api.Assertions.assertThat(document.select("#car").text()).isEqualTo("Toyota — GR86");
        if (category) org.assertj.core.api.Assertions.assertThat(document.select("#pool").text()).isEqualTo(label);
        else org.assertj.core.api.Assertions.assertThat(document.select("#pool")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(document.select("img[src=x], [onerror]")).isEmpty();
    }

	private Race createReadyRace(boolean groupAttached) throws IOException {
		var season = new Season("S", 2026, 1); season.setId(UUID.randomUUID());
		var home = new Team("H", "H"); home.setId(UUID.randomUUID());
		var away = new Team("A", "A"); away.setId(UUID.randomUUID());
		var matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());

		if (groupAttached) {
			var group = new SeasonPhaseGroup(matchday.getPhase(), "Group A", 0);
			group.setId(UUID.randomUUID());
			matchday.setGroup(group);
		}

		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setHomeTeamOverride(home);
		race.setAwayTeamOverride(away);

		var car = new Car("Toyota", "GR86");
		race.setCar(car);

		var track = new Track("Nurburgring");
		race.setTrack(track);

		var settings = new RaceSettings(race);
		settings.setNumberOfLaps(5);
		settings.setTyreWearMultiplier(2);
		settings.setFuelConsumptionMultiplier(2);
		settings.setRefuelingSpeed(1);
		settings.setInitialFuel("Full");
		settings.setNumberOfRequiredPitStops(1);
		settings.setTimeProgressionMultiplier(1);
		settings.setWeather("Clear");
		settings.setTimeOfDay("Day");
		settings.setAvailableTyres("All");
		settings.setMandatoryTyres("Medium");
		race.setSettings(settings);

		// create team-card files so encodeCardBase64 does not return null before the standings call
		Path cardDir = tempDir.resolve("team-cards").resolve(season.getId().toString());
		Files.createDirectories(cardDir);
		var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		ImageIO.write(img, "png", cardDir.resolve("H.png").toFile());
		ImageIO.write(img, "png", cardDir.resolve("A.png").toFile());

		return race;
	}
}
