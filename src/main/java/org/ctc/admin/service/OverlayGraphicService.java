package org.ctc.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.TeamStanding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OverlayGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/overlay-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "overlay-template.html";
	private static final String COMMENTATOR_CLASSPATH = "static/admin/img/commentator.png";
	private static final String VS_BADGE_CLASSPATH = "static/admin/img/vs-badge.svg";

	private final StandingsService standingsService;
	private final SeasonTeamRepository seasonTeamRepository;

	public OverlayGraphicService(TemplateEngine templateEngine,
	                             StandingsService standingsService,
	                             SeasonTeamRepository seasonTeamRepository,
	                             @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.standingsService = standingsService;
		this.seasonTeamRepository = seasonTeamRepository;
	}

	public String generateOverlay(Race race) throws IOException {
		if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
			throw new IllegalStateException("Race has no teams assigned");
		}

		var homeTeam = race.getHomeTeam();
		var awayTeam = race.getAwayTeam();
		var season = race.getMatchday().getSeason();

		Map<UUID, SeasonTeam> seasonTeamMap = new HashMap<>();
		for (var st : seasonTeamRepository.findBySeasonId(season.getId())) {
			seasonTeamMap.put(st.getTeam().getId(), st);
		}

		var standings = standingsService.calculateStandings(season.getId());
		Map<UUID, TeamStanding> standingMap = new HashMap<>();
		for (var standing : standings) {
			standingMap.put(standing.getTeam().getId(), standing);
		}

		var homeSt = seasonTeamMap.get(homeTeam.getId());
		var awaySt = seasonTeamMap.get(awayTeam.getId());

		var ctx = new Context();
		ctx.setVariable("homeTeamName", homeTeam.getName());
		ctx.setVariable("homeTeamNameHtml", formatTeamNameHtml(homeTeam.getName()));
		ctx.setVariable("homeTeamShortName", homeTeam.getShortName());
		ctx.setVariable("homeLogoBase64", encodeLogoBase64(homeTeam, homeSt));
		ctx.setVariable("homePrimaryColor", homeSt != null ? homeSt.getEffectivePrimaryColor() : homeTeam.getPrimaryColor());
		ctx.setVariable("homeSecondaryColor", homeSt != null ? homeSt.getEffectiveSecondaryColor() : homeTeam.getSecondaryColor());
		ctx.setVariable("homeRecord", formatRecord(standingMap.get(homeTeam.getId())));
		ctx.setVariable("awayTeamName", awayTeam.getName());
		ctx.setVariable("awayTeamNameHtml", formatTeamNameHtml(awayTeam.getName()));
		ctx.setVariable("awayTeamShortName", awayTeam.getShortName());
		ctx.setVariable("awayLogoBase64", encodeLogoBase64(awayTeam, awaySt));
		ctx.setVariable("awayPrimaryColor", awaySt != null ? awaySt.getEffectivePrimaryColor() : awayTeam.getPrimaryColor());
		ctx.setVariable("awaySecondaryColor", awaySt != null ? awaySt.getEffectiveSecondaryColor() : awayTeam.getSecondaryColor());
		ctx.setVariable("awayRecord", formatRecord(standingMap.get(awayTeam.getId())));
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("matchdayName", race.getMatchday().getLabel());
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("vsBadgeBase64", encodeClasspathResource(VS_BADGE_CLASSPATH, "image/svg+xml"));
		ctx.setVariable("commentatorBase64", encodeClasspathResource(COMMENTATOR_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

		String html = renderTemplate(ctx);

		Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path outputFile = raceDir.resolve("overlay.png");

		renderScreenshotTransparent(html, outputFile);

		log.info("Generated overlay graphic: {}", outputFile);
		return "/uploads/races/" + race.getId() + "/overlay.png";
	}

	private String encodeLogoBase64(Team team, SeasonTeam seasonTeam) {
		String logoUrl = seasonTeam != null ? seasonTeam.getEffectiveLogoUrl() : team.getLogoUrl();
		if (logoUrl == null) return null;
		return encodeCardBase64(logoUrl);
	}

	private String formatRecord(TeamStanding standing) {
		if (standing == null) return "0-0-0";
		return standing.getWins() + "-" + standing.getLosses() + "-" + standing.getDraws();
	}

	String formatTeamNameHtml(String name) {
		if (name == null) return "";
		String[] words = name.split("\\s+");
		if (words.length <= 3) {
			return String.join("<br>", words);
		}
		return words[0] + "<br>" + words[1] + "<br>" + String.join(" ", java.util.Arrays.copyOfRange(words, 2, words.length));
	}

	// Template management

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process("admin/overlay-render", ctx);
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
		log.info("Saved custom overlay template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset overlay template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}
}
