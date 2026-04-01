package org.ctc.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.dto.MatchdayGraphicData.MatchGraphicRow;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.TeamStanding;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public abstract class AbstractMatchdayGraphicService extends AbstractGraphicService {

    private static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM. HH:mm z", Locale.ENGLISH);

    protected final StandingsService standingsService;

    protected AbstractMatchdayGraphicService(TemplateEngine templateEngine,
                                             StandingsService standingsService,
                                             String uploadDir) {
        super(templateEngine, uploadDir);
        this.standingsService = standingsService;
    }

    protected abstract String getTemplateFileName();

    protected abstract String getDefaultTemplatePath();

    public MatchdayGraphicData prepareBaseContext(Matchday matchday) {
        var season = matchday.getSeason();
        var standings = standingsService.calculateStandings(season.getId());

        Map<UUID, Integer> seedMap = new HashMap<>();
        Map<UUID, TeamStanding> standingMap = new HashMap<>();
        for (int i = 0; i < standings.size(); i++) {
            var standing = standings.get(i);
            seedMap.put(standing.getTeam().getId(), i + 1);
            standingMap.put(standing.getTeam().getId(), standing);
        }

        var rows = matchday.getMatches().stream()
                .filter(m -> !m.isBye())
                .sorted(Comparator.comparing(
                        this::getEarliestDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> buildMatchRow(m, seedMap, standingMap, season.getId().toString()))
                .toList();

        return new MatchdayGraphicData(
                matchday.getLabel(),
                season.getName(),
                String.valueOf(season.getYear()),
                encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"),
                encodeClasspathResource(FONT_CLASSPATH, "font/woff2"),
                rows
        );
    }

    private MatchGraphicRow buildMatchRow(Match match, Map<UUID, Integer> seedMap,
                                          Map<UUID, TeamStanding> standingMap, String seasonId) {
        var home = match.getHomeTeam();
        var away = match.getAwayTeam();

        return new MatchGraphicRow(
                home.getName(), home.getShortName(),
                encodeLogoBase64(home),
                home.getPrimaryColor(), home.getSecondaryColor(), home.getAccentColor(),
                seedMap.getOrDefault(home.getId(), 0),
                formatRecord(standingMap.get(home.getId())),
                away.getName(), away.getShortName(),
                encodeLogoBase64(away),
                away.getPrimaryColor(), away.getSecondaryColor(), away.getAccentColor(),
                seedMap.getOrDefault(away.getId(), 0),
                formatRecord(standingMap.get(away.getId())),
                formatScheduledDateTime(match),
                match.getHomeScore(),
                match.getAwayScore()
        );
    }

    private String encodeLogoBase64(Team team) {
        if (team.getLogoUrl() == null) return null;
        return encodeCardBase64(team.getLogoUrl());
    }

    private String formatRecord(TeamStanding standing) {
        if (standing == null) return "0-0-0";
        return standing.getWins() + "-" + standing.getLosses() + "-" + standing.getDraws();
    }

    private LocalDateTime getEarliestDateTime(Match match) {
        return match.getRaces().stream()
                .map(Race::getDateTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private String formatScheduledDateTime(Match match) {
        var earliest = getEarliestDateTime(match);
        if (earliest == null) return null;
        var sourceTime = earliest.atZone(ZoneId.systemDefault());
        var londonTime = sourceTime.withZoneSameInstant(LONDON_ZONE);
        return londonTime.format(SCHEDULE_FORMAT);
    }

    // Template management

    protected String renderTemplate(Context ctx) throws IOException {
        Path customTemplate = uploadDir.resolve(getTemplateFileName());
        if (Files.exists(customTemplate)) {
            String template = Files.readString(customTemplate);
            return processStringTemplate(template, ctx);
        }
        return templateEngine.process(getDefaultTemplatePath(), ctx);
    }

    protected byte[] renderToBytes(String html) throws IOException {
        Path tempFile = Files.createTempFile("matchday-graphic-", ".png");
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
