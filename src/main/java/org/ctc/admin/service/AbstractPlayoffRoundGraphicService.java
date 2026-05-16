package org.ctc.admin.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.dto.MatchdayGraphicData.MatchGraphicRow;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
public abstract class AbstractPlayoffRoundGraphicService extends AbstractGraphicService implements TemplateManageable {

	private static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");
	private static final DateTimeFormatter SCHEDULE_FORMAT =
			DateTimeFormatter.ofPattern("EEE, dd MMM. HH:mm z", Locale.ENGLISH);

	protected final PlayoffSeedRepository playoffSeedRepository;
	protected final SeasonTeamRepository seasonTeamRepository;

	protected AbstractPlayoffRoundGraphicService(TemplateEngine templateEngine,
	                                             PlayoffSeedRepository playoffSeedRepository,
	                                             SeasonTeamRepository seasonTeamRepository,
	                                             String uploadDir) {
		super(templateEngine, uploadDir);
		this.playoffSeedRepository = playoffSeedRepository;
		this.seasonTeamRepository = seasonTeamRepository;
	}

	protected abstract String getTemplateFileName();

	protected abstract String getDefaultTemplatePath();

	public MatchdayGraphicData prepareBaseContext(PlayoffRound round) {
		var playoff = round.getPlayoff();
		var season = playoff.getSeason();

		Map<UUID, Integer> seedMap = new HashMap<>();
		for (var seed : playoffSeedRepository.findByPlayoffId(playoff.getId())) {
			seedMap.put(seed.getTeam().getId(), seed.getSeed());
		}

		Map<UUID, SeasonTeam> seasonTeamMap = new HashMap<>();
		for (var st : seasonTeamRepository.findBySeasonId(season.getId())) {
			seasonTeamMap.put(st.getTeam().getId(), st);
		}

		var rows = round.getMatchups().stream()
				.filter(m -> m.getTeam1() != null && m.getTeam2() != null)
				.sorted(Comparator.comparing(
						this::getEarliestDateTime,
						Comparator.nullsLast(Comparator.naturalOrder())))
				.map(m -> buildMatchupRow(m, seedMap, seasonTeamMap))
				.toList();

		return new MatchdayGraphicData(
				round.getLabel(),
				playoff.getName(),
				String.valueOf(season.getYear()),
				encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"),
				encodeClasspathResource(FONT_CLASSPATH, "font/woff2"),
				rows
		);
	}

	private MatchGraphicRow buildMatchupRow(PlayoffMatchup matchup, Map<UUID, Integer> seedMap,
	                                        Map<UUID, SeasonTeam> seasonTeamMap) {
		var home = matchup.getTeam1();
		var away = matchup.getTeam2();
		var homeSt = seasonTeamMap.get(home.getId());
		var awaySt = seasonTeamMap.get(away.getId());

		return new MatchGraphicRow(
				home.getName(), home.getShortName(),
				encodeLogoBase64(home, homeSt),
				homeSt != null ? homeSt.getEffectivePrimaryColor() : home.getPrimaryColor(),
				homeSt != null ? homeSt.getEffectiveSecondaryColor() : home.getSecondaryColor(),
				homeSt != null ? homeSt.getEffectiveAccentColor() : home.getAccentColor(),
				seedMap.getOrDefault(home.getId(), 0),
				"—",
				away.getName(), away.getShortName(),
				encodeLogoBase64(away, awaySt),
				awaySt != null ? awaySt.getEffectivePrimaryColor() : away.getPrimaryColor(),
				awaySt != null ? awaySt.getEffectiveSecondaryColor() : away.getSecondaryColor(),
				awaySt != null ? awaySt.getEffectiveAccentColor() : away.getAccentColor(),
				seedMap.getOrDefault(away.getId(), 0),
				"—",
				formatScheduledDateTime(matchup),
				matchup.getHomeScore(),
				matchup.getAwayScore()
		);
	}

	private String encodeLogoBase64(Team team, SeasonTeam seasonTeam) {
		String logoUrl = seasonTeam != null ? seasonTeam.getEffectiveLogoUrl() : team.getLogoUrl();
		if (logoUrl == null) {
			return null;
		}
		return encodeCardBase64(logoUrl);
	}

	private LocalDateTime getEarliestDateTime(PlayoffMatchup matchup) {
		return matchup.getRaces().stream()
				.map(Race::getDateTime)
				.filter(Objects::nonNull)
				.min(LocalDateTime::compareTo)
				.orElse(null);
	}

	private String formatScheduledDateTime(PlayoffMatchup matchup) {
		var earliest = getEarliestDateTime(matchup);
		if (earliest == null) {
			return null;
		}
		var sourceTime = earliest.atZone(ZoneId.systemDefault());
		var londonTime = sourceTime.withZoneSameInstant(LONDON_ZONE);
		return londonTime.format(SCHEDULE_FORMAT);
	}

	protected String renderTemplate(Context ctx) throws IOException {
		Path customTemplate = uploadDir.resolve(getTemplateFileName());
		if (Files.exists(customTemplate)) {
			String template = Files.readString(customTemplate);
			return processStringTemplate(template, ctx);
		}
		return templateEngine.process(getDefaultTemplatePath(), ctx);
	}

	protected byte[] renderToBytes(String html) throws IOException {
		Path tempFile = Files.createTempFile("playoff-round-graphic-", ".png");
		try {
			renderScreenshot(html, tempFile);
			return Files.readAllBytes(tempFile);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	public String loadTemplate() throws IOException {
		Path customTemplate = uploadDir.resolve(getTemplateFileName());
		if (Files.exists(customTemplate)) {
			return Files.readString(customTemplate);
		}
		return loadDefaultTemplate();
	}

	public String loadDefaultTemplate() throws IOException {
		var resource = new ClassPathResource("templates/" + getDefaultTemplatePath() + ".html");
		try (var is = resource.getInputStream()) {
			return new String(is.readAllBytes());
		}
	}

	public void saveTemplate(String content) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(getTemplateFileName()), content);
		log.info("Saved custom {} template", getTemplateFileName());
	}

	public void resetTemplate() throws IOException {
		Files.deleteIfExists(uploadDir.resolve(getTemplateFileName()));
		log.info("Reset {} template to default", getTemplateFileName());
	}

	public boolean hasCustomTemplate() {
		return Files.exists(uploadDir.resolve(getTemplateFileName()));
	}
}
