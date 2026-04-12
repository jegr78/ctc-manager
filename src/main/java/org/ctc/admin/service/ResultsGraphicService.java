package org.ctc.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.service.ScoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ResultsGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/results-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "results-template.html";

	private final ScoringService scoringService;

	public ResultsGraphicService(TemplateEngine templateEngine,
	                             ScoringService scoringService,
	                             @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.scoringService = scoringService;
	}

	public String generateResults(Race race) throws IOException {
		if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
			throw new IllegalStateException("Race has no teams assigned");
		}
		if (race.getResults().isEmpty()) throw new IllegalStateException("No results for this race");

		var homeTeam = race.getHomeTeam();
		var awayTeam = race.getAwayTeam();
		var season = race.getMatchday().getSeason();

		var resultRows = buildResultRows(race);

		String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
		String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
		if (homeCardBase64 == null)
			throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
		if (awayCardBase64 == null)
			throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

		int homeTotal = resultRows.stream().mapToInt(DriverResultRow::homePoints).sum();
		int awayTotal = resultRows.stream().mapToInt(DriverResultRow::awayPoints).sum();

		String seasonName = race.getPlayoffMatchup() != null
				? race.getPlayoffMatchup().getRound().getPlayoff().getName()
				: season.getName();

		var ctx = new Context();
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("matchdayName", race.getMatchday().getLabel());
		ctx.setVariable("seasonName", seasonName);
		ctx.setVariable("homeCardBase64", homeCardBase64);
		ctx.setVariable("awayCardBase64", awayCardBase64);
		ctx.setVariable("homeTotal", homeTotal);
		ctx.setVariable("awayTotal", awayTotal);
		ctx.setVariable("homeIsWinner", homeTotal > awayTotal);
		ctx.setVariable("awayIsWinner", awayTotal > homeTotal);
		ctx.setVariable("resultRows", resultRows);
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

		String html = renderTemplate(ctx);

		Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path outputFile = raceDir.resolve("results.png");

		renderScreenshot(html, outputFile);

		log.info("Generated results graphic: {}", outputFile);
		return "/uploads/races/" + race.getId() + "/results.png";
	}

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process("admin/results-render", ctx);
	}

	// Template management

	public String loadTemplate() throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			return Files.readString(customTemplate);
		}
		return loadDefaultTemplate();
	}

	public String loadDefaultTemplate() throws IOException {
		var resource = new org.springframework.core.io.ClassPathResource(DEFAULT_TEMPLATE);
		try (var is = resource.getInputStream()) {
			return new String(is.readAllBytes());
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom results template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset results template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}

	List<DriverResultRow> buildResultRows(Race race) {
		var homeTeamId = race.getHomeTeam().getId();
		var raceId = race.getId();

		var byPoints = Comparator.comparingInt(RaceResult::getPointsTotal).reversed()
				.thenComparingInt(RaceResult::getPosition);

		var homeResults = race.getResults().stream()
				.filter(r -> scoringService.isDriverInTeam(r, raceId, homeTeamId))
				.sorted(byPoints)
				.toList();
		var awayResults = race.getResults().stream()
				.filter(r -> !scoringService.isDriverInTeam(r, raceId, homeTeamId))
				.sorted(byPoints)
				.toList();

		int maxSize = Math.max(homeResults.size(), awayResults.size());
		var rows = new ArrayList<DriverResultRow>();
		for (int i = 0; i < maxSize; i++) {
			String homeName = i < homeResults.size() ? homeResults.get(i).getDriver().getPsnId() : "";
			String homeNick = i < homeResults.size() ? resolveNickname(homeResults.get(i).getDriver()) : "";
			int homePoints = i < homeResults.size() ? homeResults.get(i).getPointsTotal() : 0;
			String awayName = i < awayResults.size() ? awayResults.get(i).getDriver().getPsnId() : "";
			String awayNick = i < awayResults.size() ? resolveNickname(awayResults.get(i).getDriver()) : "";
			int awayPoints = i < awayResults.size() ? awayResults.get(i).getPointsTotal() : 0;
			rows.add(new DriverResultRow(homeName, homeNick, homePoints, awayPoints, awayName, awayNick));
		}
		return rows;
	}

	private String resolveNickname(Driver driver) {
		return driver.getNickname() != null && !driver.getNickname().isBlank()
				? driver.getNickname() : driver.getPsnId();
	}

	public record DriverResultRow(String homeDriver, String homeNickname, int homePoints,
	                              int awayPoints, String awayDriver, String awayNickname) {
	}
}
