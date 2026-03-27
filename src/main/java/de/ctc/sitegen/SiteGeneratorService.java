package de.ctc.sitegen;

import de.ctc.domain.model.Race;
import de.ctc.domain.model.Season;
import de.ctc.domain.repository.*;
import de.ctc.sitegen.model.RaceView;
import de.ctc.domain.service.DriverRankingService;
import de.ctc.domain.service.StandingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteGeneratorService {

    private final TemplateEngine templateEngine;
    private final SeasonRepository seasonRepository;
    private final MatchdayRepository matchdayRepository;
    private final RaceRepository raceRepository;
    private final TeamRepository teamRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceResultRepository raceResultRepository;
    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;

    @lombok.Setter
    @Value("${ctc.site.output-dir}")
    private String outputDir;

    @Transactional(readOnly = true)
    public GenerationResult generate() {
        var result = new GenerationResult();
        Path outPath = Path.of(outputDir);

        try {
            Files.createDirectories(outPath);

            // Find active season
            var activeSeason = seasonRepository.findByActiveTrue().orElse(null);
            var allSeasons = seasonRepository.findAll();

            // Generate index
            generateIndex(outPath, activeSeason, allSeasons, result);

            // Generate pages for each season
            for (var season : allSeasons) {
                generateStandings(outPath, season, result);
                generateDriverRanking(outPath, season, result);
                generateMatchdays(outPath, season, result);
                generateTeamProfiles(outPath, season, result);
                generateDriverProfiles(outPath, season, result);
            }

            // Generate archive
            generateArchive(outPath, allSeasons, result);

            // Copy static assets
            copyAssets(outPath, result);

            log.info("Site generation complete: {} pages", result.getPagesGenerated());
        } catch (IOException e) {
            log.error("Site generation failed", e);
            result.addError("Generierung fehlgeschlagen: " + e.getMessage());
        }

        return result;
    }

    private void generateIndex(Path outPath, Season activeSeason, List<Season> allSeasons,
                                GenerationResult result) throws IOException {
        var ctx = new Context(Locale.GERMAN);
        ctx.setVariable("allSeasons", allSeasons);

        if (activeSeason != null) {
            ctx.setVariable("season", activeSeason);
            ctx.setVariable("standings", standingsService.calculateStandings(activeSeason.getId()));

            var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(activeSeason.getId());
            if (!matchdays.isEmpty()) {
                var lastMatchday = matchdays.getLast();
                ctx.setVariable("lastMatchday", lastMatchday);
                ctx.setVariable("lastMatchdayRaces", raceRepository.findByMatchdayId(lastMatchday.getId()).stream()
                        .map(r -> toRaceView(r, activeSeason)).toList());
            }
        }

        writeTemplate("site/index", ctx, outPath.resolve("index.html"));
        result.incrementPages();
    }

    private void generateStandings(Path outPath, Season season, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.GERMAN);
        ctx.setVariable("season", season);
        ctx.setVariable("standings", standingsService.calculateStandings(season.getId()));

        var dir = outPath.resolve("season").resolve(slugify(season.getName()));
        Files.createDirectories(dir);
        writeTemplate("site/standings", ctx, dir.resolve("standings.html"));
        result.incrementPages();
    }

    private void generateDriverRanking(Path outPath, Season season, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.GERMAN);
        ctx.setVariable("season", season);
        ctx.setVariable("driverRanking", driverRankingService.calculateRanking(season.getId()));

        var dir = outPath.resolve("season").resolve(slugify(season.getName()));
        Files.createDirectories(dir);
        writeTemplate("site/driver-ranking", ctx, dir.resolve("driver-ranking.html"));
        result.incrementPages();
    }

    private void generateMatchdays(Path outPath, Season season, GenerationResult result) throws IOException {
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        for (var matchday : matchdays) {
            var ctx = new Context(Locale.GERMAN);
            ctx.setVariable("season", season);
            ctx.setVariable("matchday", matchday);
            var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
                    .map(r -> toRaceView(r, season)).toList();
            ctx.setVariable("races", raceViews);

            var dir = outPath.resolve("season").resolve(slugify(season.getName())).resolve("matchday");
            Files.createDirectories(dir);
            writeTemplate("site/matchday", ctx, dir.resolve(slugify(matchday.getLabel()) + ".html"));
            result.incrementPages();
        }
    }

    private void generateTeamProfiles(Path outPath, Season season, GenerationResult result) throws IOException {
        var teams = teamRepository.findAll();
        var standings = standingsService.calculateStandings(season.getId());

        for (var team : teams) {
            var teamStanding = standings.stream()
                    .filter(s -> s.getTeam().getId().equals(team.getId()))
                    .findFirst().orElse(null);

            if (teamStanding == null) continue;

            var ctx = new Context(Locale.GERMAN);
            ctx.setVariable("season", season);
            ctx.setVariable("team", team);
            ctx.setVariable("standing", teamStanding);

            var dir = outPath.resolve("season").resolve(slugify(season.getName())).resolve("team");
            Files.createDirectories(dir);
            writeTemplate("site/team-profile", ctx, dir.resolve(slugify(team.getShortName()) + ".html"));
            result.incrementPages();
        }
    }

    private void generateDriverProfiles(Path outPath, Season season, GenerationResult result) throws IOException {
        var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

        for (var sd : seasonDrivers) {
            var driver = sd.getDriver();
            var team = sd.getTeam();
            var results = raceResultRepository.findByDriverId(driver.getId()).stream()
                    .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                    .toList();

            var ctx = new Context(Locale.GERMAN);
            ctx.setVariable("season", season);
            ctx.setVariable("driver", driver);
            ctx.setVariable("team", team);
            ctx.setVariable("results", results);
            ctx.setVariable("totalRaces", results.size());
            ctx.setVariable("totalPoints", results.stream().mapToInt(r -> r.getPointsTotal()).sum());
            ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) results.stream().mapToInt(r -> r.getPointsTotal()).sum() / results.size());
            ctx.setVariable("bestPosition", results.stream().mapToInt(r -> r.getPosition()).min().orElse(0));

            var dir = outPath.resolve("season").resolve(slugify(season.getName())).resolve("driver");
            Files.createDirectories(dir);
            writeTemplate("site/driver-profile", ctx, dir.resolve(slugify(driver.getPsnId()) + ".html"));
            result.incrementPages();
        }
    }

    private void generateArchive(Path outPath, List<Season> allSeasons, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.GERMAN);
        ctx.setVariable("seasons", allSeasons);
        writeTemplate("site/archive", ctx, outPath.resolve("archive.html"));
        result.incrementPages();
    }

    private void writeTemplate(String templateName, Context context, Path outputFile) throws IOException {
        // Calculate relative path to assets from the output file location
        Path outRoot = Path.of(outputDir);
        Path relative = outputFile.getParent().relativize(outRoot.resolve("assets"));
        context.setVariable("assetsPath", relative.toString());

        String html = templateEngine.process(templateName, context);
        Files.writeString(outputFile, html);
        log.debug("Generated: {}", outputFile);
    }

    private void copyAssets(Path outPath, GenerationResult result) throws IOException {
        var assetsSource = new ClassPathResource("static/site");
        if (!assetsSource.exists()) {
            log.warn("No static site assets found");
            return;
        }

        var assetsDir = outPath.resolve("assets");
        Files.createDirectories(assetsDir);

        // Copy from classpath to output
        var sourceUri = assetsSource.getURI();
        if (sourceUri.getScheme().equals("file")) {
            var sourcePath = Path.of(sourceUri);
            copyDirectory(sourcePath, assetsDir);
            log.debug("Copied assets to {}", assetsDir);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private RaceView toRaceView(Race race, Season season) {
        var homeTeamId = race.getHomeTeam().getId();
        var results = race.getResults().stream()
                .map(r -> {
                    var teamShortName = r.getDriver().getSeasonDrivers().stream()
                            .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                            .map(sd -> sd.getTeam().getShortName())
                            .findFirst().orElse("?");
                    return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName,
                            r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal());
                })
                .toList();

        int homeTotal = results.stream()
                .filter(r -> r.getTeamShortName().equals(race.getHomeTeam().getShortName()))
                .mapToInt(RaceView.ResultView::getPointsTotal).sum();
        int awayTotal = results.stream()
                .filter(r -> r.getTeamShortName().equals(race.getAwayTeam().getShortName()))
                .mapToInt(RaceView.ResultView::getPointsTotal).sum();

        return new RaceView(race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName(),
                race.getTrack(), race.getCar(), homeTotal, awayTotal, !race.getResults().isEmpty(), results);
    }

    private String slugify(String input) {
        return input.toLowerCase()
                .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public static class GenerationResult {
        private int pagesGenerated;
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public void incrementPages() { pagesGenerated++; }
        public void addError(String error) { errors.add(error); }
        public int getPagesGenerated() { return pagesGenerated; }
        public java.util.List<String> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
