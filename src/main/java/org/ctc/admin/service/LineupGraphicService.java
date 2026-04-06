package org.ctc.admin.service;

import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PlayoffSeed;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.service.StandingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public record DriverPairing(String homeDriver, String homeNickname, String awayDriver, String awayNickname) {}

    public String generateLineup(Race race) throws IOException {
        var homeTeam = race.getHomeTeam();
        var awayTeam = race.getAwayTeam();
        if (homeTeam == null || awayTeam == null) throw new IllegalStateException("Race has no teams assigned");

        var season = race.getMatchday().getSeason();

        var lineups = raceLineupRepository.findByRaceId(race.getId());
        if (lineups.isEmpty()) throw new IllegalStateException("No lineup entries for this race");
        var pairings = buildPairings(lineups, homeTeam, awayTeam);

        String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
        String awayCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), awayTeam.getShortName()));
        if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
        if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

        int homePosition = 0;
        int awayPosition = 0;

        if (race.getPlayoffMatchup() != null) {
            var playoff = race.getPlayoffMatchup().getRound().getPlayoff();
            var homeSeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), homeTeam.getId());
            var awaySeed = playoffSeedRepository.findByPlayoffIdAndTeamId(playoff.getId(), awayTeam.getId());
            homePosition = homeSeed.map(PlayoffSeed::getSeed).orElse(0);
            awayPosition = awaySeed.map(PlayoffSeed::getSeed).orElse(0);
        } else {
            var standings = standingsService.calculateStandings(season.getId());
            for (int i = 0; i < standings.size(); i++) {
                if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
                if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
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
                .map(RaceLineup::getDriver)
                .toList();
        var awayEntries = lineups.stream()
                .filter(lu -> isTeamOrSubTeam(lu.getTeam(), awayTeam))
                .map(RaceLineup::getDriver)
                .toList();

        int maxSize = Math.max(homeEntries.size(), awayEntries.size());
        var pairings = new ArrayList<DriverPairing>();
        for (int i = 0; i < maxSize; i++) {
            String homePsn = i < homeEntries.size() ? homeEntries.get(i).getPsnId() : "";
            String homeNick = i < homeEntries.size() ? resolveNickname(homeEntries.get(i)) : "";
            String awayPsn = i < awayEntries.size() ? awayEntries.get(i).getPsnId() : "";
            String awayNick = i < awayEntries.size() ? resolveNickname(awayEntries.get(i)) : "";
            pairings.add(new DriverPairing(homePsn, homeNick, awayPsn, awayNick));
        }
        return pairings;
    }

    private String resolveNickname(Driver driver) {
        return driver.getNickname() != null && !driver.getNickname().isBlank()
                ? driver.getNickname() : driver.getPsnId();
    }

    private boolean isTeamOrSubTeam(Team team, Team parentOrSelf) {
        if (team.getId().equals(parentOrSelf.getId())) return true;
        return team.getParentTeam() != null && team.getParentTeam().getId().equals(parentOrSelf.getId());
    }

    // Template management

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
            return new String(is.readAllBytes());
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
}
