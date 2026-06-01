package org.ctc.admin.service;

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.service.StandingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class LineupGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final String DEFAULT_TEMPLATE = "templates/admin/lineup-render.html";
	private static final String CUSTOM_TEMPLATE_FILE = "lineup-template.html";

	private final StandingsService standingsService;
	private final RaceLineupRepository raceLineupRepository;
	private final PlayoffSeedRepository playoffSeedRepository;

	public LineupGraphicService(TemplateEngine templateEngine,
	                            StandingsService standingsService,
	                            RaceLineupRepository raceLineupRepository,
	                            PlayoffSeedRepository playoffSeedRepository,
	                            @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.standingsService = standingsService;
		this.raceLineupRepository = raceLineupRepository;
		this.playoffSeedRepository = playoffSeedRepository;
	}

	public String generateLineup(Race race) throws IOException {
		var homeTeam = race.getHomeTeam();
		var awayTeam = race.getAwayTeam();
		if (homeTeam == null || awayTeam == null) {
			throw new IllegalStateException("Race has no teams assigned");
		}

		var season = race.getMatchday().getSeason();

		var match = race.getMatch();
		boolean homeIsWalkover = match != null && match.isWalkoverFor(homeTeam);
		boolean awayIsWalkover = match != null && match.isWalkoverFor(awayTeam);
		boolean isWalkover = match != null && match.getWalkoverTeam() != null;

		var lineups = raceLineupRepository.findByRaceId(race.getId());
		if (lineups.isEmpty() && !isWalkover) {
			throw new IllegalStateException("No lineup entries for this race");
		}
		var pairings = buildPairings(lineups, homeTeam, awayTeam);

		String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
		String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
		if (homeCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
		}
		if (awayCardBase64 == null) {
			throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());
		}

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
				if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) {
					homePosition = i + 1;
				}
				if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) {
					awayPosition = i + 1;
				}
			}
		}

		String seasonName = race.getPlayoffMatchup() != null
				? race.getPlayoffMatchup().getRound().getPlayoff().getName()
				: season.getName();

		var ctx = new Context();
		ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
		ctx.setVariable("matchdayName", race.getMatchday().getLabel());
		ctx.setVariable("seasonName", seasonName);
		ctx.setVariable("homeCardBase64", homeCardBase64);
		ctx.setVariable("awayCardBase64", awayCardBase64);
		ctx.setVariable("homePosition", homePosition);
		ctx.setVariable("awayPosition", awayPosition);
		ctx.setVariable("pairings", pairings);
		ctx.setVariable("homeIsWalkover", homeIsWalkover);
		ctx.setVariable("awayIsWalkover", awayIsWalkover);
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

		String html = renderTemplate(ctx);

		Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path outputFile = raceDir.resolve("lineup.png");

		renderScreenshot(html, outputFile);

		log.info("Generated lineup graphic: {}", outputFile);
		return "/uploads/races/" + race.getId() + "/lineup.png";
	}

	List<DriverPairing> buildPairings(List<RaceLineup> lineups, Team homeTeam, Team awayTeam) {
		var homeEntries = lineups.stream()
				.filter(lu -> isTeamOrSubTeam(lu.getTeam(), homeTeam))
				.toList();
		var awayEntries = lineups.stream()
				.filter(lu -> isTeamOrSubTeam(lu.getTeam(), awayTeam))
				.toList();

		var pairings = new ArrayList<DriverPairing>();
		for (int i = 0; i < TEAM_DRIVERS; i++) {
			String homePsn = i < homeEntries.size() ? homeEntries.get(i).getDriver().getPsnId() : "n/a";
			String homeNick = i < homeEntries.size() ? resolveNickname(homeEntries.get(i).getDriver()) : "";
			boolean homeIsGuest = i < homeEntries.size() && homeEntries.get(i).isGuest();
			String awayPsn = i < awayEntries.size() ? awayEntries.get(i).getDriver().getPsnId() : "n/a";
			String awayNick = i < awayEntries.size() ? resolveNickname(awayEntries.get(i).getDriver()) : "";
			boolean awayIsGuest = i < awayEntries.size() && awayEntries.get(i).isGuest();
			pairings.add(new DriverPairing(homePsn, homeNick, awayPsn, awayNick, homeIsGuest, awayIsGuest));
		}
		return pairings;
	}

	private String resolveNickname(Driver driver) {
		return hasText(driver.getNickname())
				? driver.getNickname() : driver.getPsnId();
	}

	private boolean isTeamOrSubTeam(Team team, Team parentOrSelf) {
		if (team.getId().equals(parentOrSelf.getId())) {
			return true;
		}
		return team.getParentTeam() != null && team.getParentTeam().getId().equals(parentOrSelf.getId());
	}

	private String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process("admin/lineup-render", ctx);
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
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
		log.info("Saved custom lineup template");
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
		log.info("Reset lineup template to default");
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
	}

	public record DriverPairing(String homeDriver, String homeNickname, String awayDriver, String awayNickname,
	                            boolean homeIsGuest, boolean awayIsGuest) {
	}
}
