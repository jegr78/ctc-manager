package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsGraphicServiceTest {

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
