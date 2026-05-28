package org.ctc.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.ScoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class ProvisionalScoresGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE_PATH = "templates/admin/provisional-scores-render.html";
	private static final String DEFAULT_TEMPLATE_VIEW = "admin/provisional-scores-render";
	private static final String CUSTOM_TEMPLATE_FILE = "provisional-scores-template.html";

	private final ScoringService scoringService;
	private final PlaywrightScreenshotter screenshotter;

	public ProvisionalScoresGraphicService(TemplateEngine templateEngine,
	                                       ScoringService scoringService,
	                                       @Value("${app.upload-dir:uploads}") String uploadDir,
	                                       PlaywrightScreenshotter screenshotter) {
		super(templateEngine, uploadDir);
		this.scoringService = scoringService;
		this.screenshotter = screenshotter;
	}

	public byte[] generateProvisional(Race race, int raceIndex) throws IOException {
		if (race.getResults().isEmpty()) {
			throw new IllegalStateException("No results for this race");
		}
		Team homeTeam = race.getHomeTeam();
		Team awayTeam = race.getAwayTeam();
		if (homeTeam == null) {
			throw new IllegalStateException("Race has no home team");
		}
		if (awayTeam == null) {
			throw new IllegalStateException("Race has no away team");
		}

		Context ctx = buildContext(race, raceIndex, homeTeam, awayTeam);
		String html = renderTemplate(ctx);
		byte[] png = screenshotter.apply(html);
		log.info("Generated provisional scores graphic for race {} (raceIndex={})", race.getId(), raceIndex);
		return png;
	}

	Context buildContext(Race race, int raceIndex, Team homeTeam, Team awayTeam) {
		UUID homeTeamId = homeTeam.getId();
		UUID raceId = race.getId();

		List<ProvisionalRow> homeRows = new ArrayList<>();
		List<ProvisionalRow> awayRows = new ArrayList<>();
		for (RaceResult result : race.getResults()) {
			ProvisionalRow row = toRow(result);
			if (scoringService.isDriverInTeam(result, raceId, homeTeamId)) {
				homeRows.add(row);
			} else {
				awayRows.add(row);
			}
		}

		var season = race.getMatchday().getSeason();
		String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
		String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
		if (homeCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
		}
		if (awayCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());
		}

		int homeOverallPtsRace = homeRows.stream().mapToInt(ProvisionalRow::ptsRace).sum();
		int homeOverallPtsQuali = homeRows.stream().mapToInt(ProvisionalRow::ptsQuali).sum();
		int homeOverallPtsFl = homeRows.stream().mapToInt(ProvisionalRow::ptsFl).sum();
		int homeOverallTotal = homeRows.stream().mapToInt(ProvisionalRow::total).sum();
		int awayOverallPtsRace = awayRows.stream().mapToInt(ProvisionalRow::ptsRace).sum();
		int awayOverallPtsQuali = awayRows.stream().mapToInt(ProvisionalRow::ptsQuali).sum();
		int awayOverallPtsFl = awayRows.stream().mapToInt(ProvisionalRow::ptsFl).sum();
		int awayOverallTotal = awayRows.stream().mapToInt(ProvisionalRow::total).sum();

		Context ctx = new Context();
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("seasonName", season.getName());
		ctx.setVariable("matchdayName", race.getMatchday().getLabel());
		ctx.setVariable("raceLabel", "Race " + raceIndex);
		ctx.setVariable("homeTeamName", homeTeam.getName());
		ctx.setVariable("awayTeamName", awayTeam.getName());
		ctx.setVariable("homeCardBase64", homeCardBase64);
		ctx.setVariable("awayCardBase64", awayCardBase64);
		ctx.setVariable("homeRows", homeRows);
		ctx.setVariable("awayRows", awayRows);
		ctx.setVariable("homeOverallPtsRace", homeOverallPtsRace);
		ctx.setVariable("homeOverallPtsQuali", homeOverallPtsQuali);
		ctx.setVariable("homeOverallPtsFl", homeOverallPtsFl);
		ctx.setVariable("homeOverallTotal", homeOverallTotal);
		ctx.setVariable("awayOverallPtsRace", awayOverallPtsRace);
		ctx.setVariable("awayOverallPtsQuali", awayOverallPtsQuali);
		ctx.setVariable("awayOverallPtsFl", awayOverallPtsFl);
		ctx.setVariable("awayOverallTotal", awayOverallTotal);
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
		return ctx;
	}

	private ProvisionalRow toRow(RaceResult result) {
		String driverName = result.getDriver() != null ? result.getDriver().getPsnId() : "?";
		return new ProvisionalRow(
				driverName,
				result.getPosition(),
				result.getQualiPosition(),
				result.isFastestLap(),
				result.getPointsRace(),
				result.getPointsQuali(),
				result.getPointsFl(),
				result.getPointsTotal());
	}

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process(DEFAULT_TEMPLATE_VIEW, ctx);
	}

	public String loadTemplate() throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			return Files.readString(customTemplate);
		}
		return loadDefaultTemplate();
	}

	public String loadDefaultTemplate() throws IOException {
		var resource = new org.springframework.core.io.ClassPathResource(DEFAULT_TEMPLATE_PATH);
		try (var is = resource.getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom provisional scores template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset provisional scores template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}

	public record ProvisionalRow(String driverName, int position, int qualiPosition, boolean fastestLap,
	                              int ptsRace, int ptsQuali, int ptsFl, int total) {
	}
}
