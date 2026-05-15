package org.ctc.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.PlayoffSeed;
import org.ctc.domain.model.Race;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.service.StandingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class SettingsGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/settings-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "settings-template.html";

	private final StandingsService standingsService;
	private final PlayoffSeedRepository playoffSeedRepository;

	public SettingsGraphicService(TemplateEngine templateEngine,
	                              StandingsService standingsService,
	                              PlayoffSeedRepository playoffSeedRepository,
	                              @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.standingsService = standingsService;
		this.playoffSeedRepository = playoffSeedRepository;
	}

	public String generateSettings(Race race) throws IOException {
		if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
			throw new IllegalStateException("Race has no teams assigned");
		}
		if (race.getCar() == null) throw new IllegalStateException("Race has no car");
		if (race.getTrack() == null) throw new IllegalStateException("Race has no track");
		if (!race.hasAllSettings()) throw new IllegalStateException("Race settings are incomplete");

		var homeTeam = race.getHomeTeam();
		var awayTeam = race.getAwayTeam();
		var season = race.getMatchday().getSeason();
		var settings = race.getSettings();

		String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
		String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
		if (homeCardBase64 == null)
			throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
		if (awayCardBase64 == null)
			throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

		int homePosition = 0;
		int awayPosition = 0;

		if (race.getPlayoffMatchup() != null) {
			var playoff = race.getPlayoffMatchup().getRound().getPlayoff();
			var homeSeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), homeTeam.getId());
			var awaySeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), awayTeam.getId());
			homePosition = homeSeed.map(PlayoffSeed::getSeed).orElse(0);
			awayPosition = awaySeed.map(PlayoffSeed::getSeed).orElse(0);
		} else {
			var phase = race.getMatchday().getPhase();
			var group = race.getMatchday().getGroup();
			var standings = standingsService.calculateStandings(
					phase.getId(),
					group != null ? group.getId() : null);
			for (int i = 0; i < standings.size(); i++) {
				if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
				if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
			}
		}

		var ctx = new Context();
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("matchdayName", race.getMatchday().getLabel());
		String seasonName = race.getPlayoffMatchup() != null
				? race.getPlayoffMatchup().getRound().getPlayoff().getName()
				: season.getName();
		ctx.setVariable("seasonName", seasonName);
		ctx.setVariable("homeCardBase64", homeCardBase64);
		ctx.setVariable("awayCardBase64", awayCardBase64);
		ctx.setVariable("homePosition", homePosition);
		ctx.setVariable("awayPosition", awayPosition);
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

		// Settings data
		ctx.setVariable("carName", race.getCar().getDisplayName());
		ctx.setVariable("trackName", race.getTrack().getName());
		ctx.setVariable("numberOfLaps", settings.getNumberOfLaps());
		ctx.setVariable("tyreWearMultiplier", settings.getTyreWearMultiplier());
		ctx.setVariable("fuelConsumptionMultiplier", settings.getFuelConsumptionMultiplier());
		ctx.setVariable("refuelingSpeed", settings.getRefuelingSpeed());
		ctx.setVariable("initialFuel", settings.getInitialFuel());
		ctx.setVariable("numberOfRequiredPitStops", settings.getNumberOfRequiredPitStops());
		ctx.setVariable("timeProgressionMultiplier", settings.getTimeProgressionMultiplier());
		ctx.setVariable("weather", settings.getWeather());
		ctx.setVariable("timeOfDay", settings.getTimeOfDay());
		ctx.setVariable("availableTyres", settings.getAvailableTyres());
		ctx.setVariable("mandatoryTyres", settings.getMandatoryTyres());

		String html = renderTemplate(ctx);

		Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path outputFile = raceDir.resolve("settings.png");

		renderScreenshot(html, outputFile);

		log.info("Generated settings graphic: {}", outputFile);
		return "/uploads/races/" + race.getId() + "/settings.png";
	}

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process("admin/settings-render", ctx);
	}

	public String loadTemplate() throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			return Files.readString(customTemplate);
		}
		return loadDefaultTemplate();
	}

	public String loadDefaultTemplate() throws IOException {
		var resource = new ClassPathResource(DEFAULT_TEMPLATE);
		try (var is = resource.getInputStream()) {
			return new String(is.readAllBytes());
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom settings template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset settings template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}
}
