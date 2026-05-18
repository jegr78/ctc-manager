package org.ctc.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.service.ScoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class MatchResultsGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/match-results-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "match-results-template.html";

	private final ScoringService scoringService;

	public MatchResultsGraphicService(TemplateEngine templateEngine,
	                                  ScoringService scoringService,
	                                  @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.scoringService = scoringService;
	}

	public byte[] generateMatchResults(Match match) throws IOException {
		if (match.getHomeTeam() == null) {
			throw new IllegalStateException("Match has no home team");
		}
		if (match.getAwayTeam() == null) {
			throw new IllegalStateException("Match has no away team");
		}
		if (match.getRaces().isEmpty()) {
			throw new IllegalStateException("Match has no races");
		}

		var homeTeam = match.getHomeTeam();
		var awayTeam = match.getAwayTeam();
		var season = match.getMatchday().getSeason();

		var raceRows = buildRaceRows(match);

		String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
		String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
		if (homeCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
		}
		if (awayCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());
		}

		int homeTotal = raceRows.stream().mapToInt(RaceResultRow::homePoints).sum();
		int awayTotal = raceRows.stream().mapToInt(RaceResultRow::awayPoints).sum();

		var ctx = new Context();
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("matchdayName", match.getMatchday().getLabel());
		ctx.setVariable("seasonName", season.getName());
		ctx.setVariable("homeCardBase64", homeCardBase64);
		ctx.setVariable("awayCardBase64", awayCardBase64);
		ctx.setVariable("homeTotal", homeTotal);
		ctx.setVariable("awayTotal", awayTotal);
		ctx.setVariable("homeIsWinner", homeTotal > awayTotal);
		ctx.setVariable("awayIsWinner", awayTotal > homeTotal);
		ctx.setVariable("raceRows", raceRows);
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

		String html = renderTemplate(ctx);

		Path tempFile = Files.createTempFile("match-results-", ".png");
		try {
			renderScreenshot(html, tempFile);
			log.info("Generated match results graphic for match {}", match.getId());
			return Files.readAllBytes(tempFile);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	List<RaceResultRow> buildRaceRows(Match match) {
		UUID homeTeamId = match.getHomeTeam().getId();
		var rows = new ArrayList<RaceResultRow>();
		int raceNumber = 0;

		for (Race race : match.getRaces()) {
			if (race.getResults().isEmpty()) {
				continue;
			}
			raceNumber++;

			int homePoints = race.getResults().stream()
					.filter(r -> scoringService.isDriverInTeam(r, race.getId(), homeTeamId))
					.mapToInt(RaceResult::getPointsTotal)
					.sum();
			int awayPoints = race.getResults().stream()
					.filter(r -> !scoringService.isDriverInTeam(r, race.getId(), homeTeamId))
					.mapToInt(RaceResult::getPointsTotal)
					.sum();

			rows.add(new RaceResultRow("Race " + raceNumber, homePoints, awayPoints));
		}
		return rows;
	}

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process("admin/match-results-render", ctx);
	}

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
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom match results template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset match results template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}

	public record RaceResultRow(String label, int homePoints, int awayPoints) {
	}
}
