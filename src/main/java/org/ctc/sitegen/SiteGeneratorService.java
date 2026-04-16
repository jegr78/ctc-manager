package org.ctc.sitegen;

import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.*;
import org.ctc.sitegen.model.RaceView;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.StandingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
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
    private final RaceLineupRepository raceLineupRepository;
    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final PlayoffService playoffService;
    private final PlayoffBracketViewService playoffBracketViewService;
    private final PlayoffRepository playoffRepository;

    @lombok.Setter
    @Value("${ctc.site.output-dir}")
    private String outputDir;

    @lombok.Setter
    @Value("${app.upload-dir:data/dev/uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public GenerationResult generate() {
        var result = new GenerationResult();
        Path outPath = Path.of(outputDir);

        try {
            Files.createDirectories(outPath);

            // Find active season
            var activeSeason = seasonRepository.findByActiveTrue().orElse(null);
            String activeSeasonSlug = activeSeason != null ? slugify(activeSeason.getDisplayLabel()) : "";
            var allSeasons = seasonRepository.findAll();
            var productionSeasons = allSeasons.stream()
                    .filter(s -> !s.getName().contains("Test"))
                    .toList();

            // Generate index
            generateIndex(outPath, activeSeason, productionSeasons, activeSeasonSlug, result);

            // Generate pages for each season
            for (var season : productionSeasons) {
                generateStandings(outPath, season, activeSeasonSlug, result);
                generateDriverRanking(outPath, season, activeSeasonSlug, result);
                generateMatchdays(outPath, season, activeSeasonSlug, result);
                generateTeamProfiles(outPath, season, activeSeasonSlug, result);
                generateDriverProfiles(outPath, season, activeSeasonSlug, result);
                generatePlayoffBracket(outPath, season, activeSeasonSlug, result);
            }

            // Generate archive
            generateArchive(outPath, productionSeasons, activeSeasonSlug, result);

            // Copy static assets
            copyAssets(outPath, result);

            log.info("Site generation complete: {} pages", result.getPagesGenerated());
        } catch (IOException e) {
            log.error("Site generation failed", e);
            result.addError("Generation failed: " + e.getMessage());
        }

        return result;
    }

    private void generateIndex(Path outPath, Season activeSeason, List<Season> allSeasons,
                                String activeSeasonSlug, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("allSeasons", allSeasons);

        if (activeSeason != null) {
            ctx.setVariable("season", activeSeason);
            var standings = standingsService.calculateStandings(activeSeason.getId());
            ctx.setVariable("standings", standings);
            var indexTeamSlugMap = new java.util.HashMap<java.util.UUID, String>();
            for (var s : standings) {
                indexTeamSlugMap.put(s.getTeam().getId(),
                    "./season/" + activeSeasonSlug + "/team/" + slugify(s.getTeam().getShortName()) + ".html");
            }
            ctx.setVariable("teamSlugMap", indexTeamSlugMap);

            var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(activeSeason.getId());
            if (!matchdays.isEmpty()) {
                var lastMatchday = matchdays.getLast();
                ctx.setVariable("lastMatchday", lastMatchday);
                ctx.setVariable("lastMatchdayRaces", raceRepository.findByMatchdayId(lastMatchday.getId()).stream()
                        .map(r -> toRaceView(r, activeSeason, "./season/" + activeSeasonSlug + "/driver/")).toList());
            }
        }

        writeTemplate("site/index", ctx, outPath.resolve("index.html"), activeSeasonSlug);
        result.incrementPages();
    }

    private void generateStandings(Path outPath, Season season, String activeSeasonSlug,
                                    GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        var standings = standingsService.calculateStandings(season.getId());
        var teamSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var s : standings) {
            teamSlugMap.put(s.getTeam().getId(), "team/" + slugify(s.getTeam().getShortName()) + ".html");
        }
        ctx.setVariable("standings", standings);
        ctx.setVariable("teamSlugMap", teamSlugMap);

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/standings", ctx, dir.resolve("standings.html"), activeSeasonSlug);
        result.incrementPages();
    }

    private void generateDriverRanking(Path outPath, Season season, String activeSeasonSlug,
                                        GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        var driverRanking = driverRankingService.calculateRanking(season.getId());
        var driverSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var r : driverRanking) {
            driverSlugMap.put(r.getDriver().getId(), "driver/" + slugify(r.getDriver().getPsnId()) + ".html");
        }
        ctx.setVariable("driverRanking", driverRanking);
        ctx.setVariable("driverSlugMap", driverSlugMap);

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/driver-ranking", ctx, dir.resolve("driver-ranking.html"), activeSeasonSlug);
        result.incrementPages();
    }

    private void generateMatchdays(Path outPath, Season season, String activeSeasonSlug,
                                    GenerationResult result) throws IOException {
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        for (var matchday : matchdays) {
            var ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("season", season);
            ctx.setVariable("matchday", matchday);
            var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
                    .map(r -> toRaceView(r, season, "../driver/")).toList();
            ctx.setVariable("races", raceViews);

            var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("matchday");
            Files.createDirectories(dir);
            writeTemplate("site/matchday", ctx, dir.resolve(slugify(matchday.getLabel()) + ".html"), activeSeasonSlug);
            result.incrementPages();
        }
    }

    private void generateTeamProfiles(Path outPath, Season season, String activeSeasonSlug,
                                       GenerationResult result) throws IOException {
        var teams = teamRepository.findAll();
        var standings = standingsService.calculateStandings(season.getId());

        for (var team : teams) {
            var teamStanding = standings.stream()
                    .filter(s -> s.getTeam().getId().equals(team.getId()))
                    .findFirst().orElse(null);

            if (teamStanding == null) continue;

            var ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("season", season);
            ctx.setVariable("team", team);
            ctx.setVariable("standing", teamStanding);

            // Load team drivers for Drivers section (per D-01, D-02, D-03)
            var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
            var driverEntries = seasonDrivers.stream()
                    .filter(sd -> sd.getTeam().getId().equals(team.getId()))
                    .map(sd -> {
                        var driver = sd.getDriver();
                        var driverResults = raceResultRepository.findByDriverId(driver.getId()).stream()
                                .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                                .toList();
                        int totalPoints = driverResults.stream().mapToInt(r -> r.getPointsTotal()).sum();
                        String driverProfileUrl = "../driver/" + slugify(driver.getPsnId()) + ".html";
                        return new DriverEntry(driver.getPsnId(), driverProfileUrl, totalPoints);
                    })
                    .toList();
            ctx.setVariable("drivers", driverEntries);

            // Compute assetsPath for this team profile page (same as writeTemplate does)
            Path teamDir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("team");
            String assetsPath = teamDir.relativize(outPath.resolve("assets")).toString().replace('\\', '/');

            // Copy logo and get relative path (null if no logo or file missing)
            String teamLogoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
            ctx.setVariable("teamLogoRelPath", teamLogoRelPath);

            Files.createDirectories(teamDir);
            writeTemplate("site/team-profile", ctx, teamDir.resolve(slugify(team.getShortName()) + ".html"), activeSeasonSlug);
            result.incrementPages();
        }
    }

    private void generateDriverProfiles(Path outPath, Season season, String activeSeasonSlug,
                                         GenerationResult result) throws IOException {
        var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

        for (var sd : seasonDrivers) {
            var driver = sd.getDriver();
            var team = sd.getTeam();
            var results = raceResultRepository.findByDriverId(driver.getId()).stream()
                    .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                    .toList();

            var ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("season", season);
            ctx.setVariable("driver", driver);
            ctx.setVariable("team", team);
            ctx.setVariable("results", results);
            ctx.setVariable("totalRaces", results.size());
            ctx.setVariable("totalPoints", results.stream().mapToInt(r -> r.getPointsTotal()).sum());
            ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) results.stream().mapToInt(r -> r.getPointsTotal()).sum() / results.size());
            ctx.setVariable("bestPosition", results.stream().mapToInt(r -> r.getPosition()).min().orElse(0));

            var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("driver");
            Files.createDirectories(dir);
            writeTemplate("site/driver-profile", ctx, dir.resolve(slugify(driver.getPsnId()) + ".html"), activeSeasonSlug);
            result.incrementPages();
        }
    }

    private void generatePlayoffBracket(Path outPath, Season season, String activeSeasonSlug,
                                         GenerationResult result) throws IOException {
        var playoffOpt = playoffRepository.findBySeasonId(season.getId());
        if (playoffOpt.isEmpty()) return;

        var playoff = playoffOpt.get();
        var bracket = playoffBracketViewService.getBracketView(playoff.getId());

        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        ctx.setVariable("playoff", playoff);
        ctx.setVariable("bracket", bracket);

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/playoff-bracket", ctx, dir.resolve("playoff.html"), activeSeasonSlug);
        result.incrementPages();
    }

    private void generateArchive(Path outPath, List<Season> allSeasons, String activeSeasonSlug,
                                   GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        var seasonEntries = allSeasons.stream()
                .map(s -> new SeasonEntry(s, slugify(s.getDisplayLabel())))
                .toList();
        ctx.setVariable("seasonEntries", seasonEntries);
        writeTemplate("site/archive", ctx, outPath.resolve("archive.html"), activeSeasonSlug);
        result.incrementPages();
    }

    private void writeTemplate(String templateName, Context context, Path outputFile,
                                String activeSeasonSlug) throws IOException {
        // Calculate relative paths from the output file location
        Path outRoot = Path.of(outputDir);
        Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
        Path relativeRoot = outputFile.getParent().relativize(outRoot);
        context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
        String rootStr = relativeRoot.toString().replace('\\', '/');
        context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
        context.setVariable("activeSeasonSlug", activeSeasonSlug != null ? activeSeasonSlug : "");

        String html = templateEngine.process(templateName, context);
        Files.writeString(outputFile, html);
        log.debug("Generated: {}", outputFile);
    }

    private String copyLogoToAssets(String logoUrl, Path outPath, String assetsPath) {
        if (logoUrl == null || !logoUrl.startsWith("/uploads/")) {
            return null;
        }
        try {
            Path uploadBase = Path.of(uploadDir).toAbsolutePath().normalize();
            Path logoFile = uploadBase.resolve(logoUrl.substring("/uploads/".length())).normalize();
            if (!logoFile.startsWith(uploadBase)) {
                log.warn("Path traversal attempt in logo URL: {}", logoUrl);
                return null;
            }
            if (!Files.exists(logoFile)) {
                log.warn("Logo file not found, skipping: {}", logoUrl);
                return null;
            }
            // Preserve UUID-prefixed subdirectory to avoid filename collisions
            String relativePart = logoUrl.substring("/uploads/".length());
            Path target = outPath.resolve("assets").resolve("img").resolve("logos").resolve(relativePart);
            Files.createDirectories(target.getParent());
            Files.copy(logoFile, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied logo: {} -> {}", logoFile, target);
            return assetsPath + "/img/logos/" + relativePart;
        } catch (IOException e) {
            log.warn("Failed to copy logo: {}", logoUrl, e);
            return null;
        }
    }

    private void copyAssets(Path outPath, GenerationResult result) throws IOException {
        var assetsDir = outPath.resolve("assets");
        Files.createDirectories(assetsDir);

        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:static/site/**/*");
        } catch (IOException e) {
            log.warn("No static site assets found: {}", e.getMessage());
            return;
        }

        String prefix = "static/site/";
        for (Resource resource : resources) {
            if (!resource.isReadable()) continue;

            String uri = resource.getURI().toString();
            int idx = uri.indexOf(prefix);
            if (idx < 0) continue;

            String relativePath = uri.substring(idx + prefix.length());
            if (relativePath.isEmpty()) continue;

            Path target = assetsDir.resolve(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.debug("Copied assets to {}", assetsDir);
    }

    private RaceView toRaceView(Race race, Season season, String driverUrlPrefix) {
        var homeTeam = race.getHomeTeam();
        String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";

        var results = race.getResults().stream()
                .map(r -> {
                    var teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), r.getDriver().getId())
                            .map(rl -> rl.getTeam().getShortName())
                            .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                                    .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                                    .map(sd -> sd.getTeam().getShortName())
                                    .findFirst().orElse("?"));
                    String driverSlug = slugify(r.getDriver().getPsnId());
                    String driverProfileUrl = driverUrlPrefix + driverSlug + ".html";
                    return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName,
                            r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal(),
                            driverProfileUrl);
                })
                .toList();

        String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

        int homeTotal = results.stream()
                .filter(r -> r.teamShortName().equals(homeShortName))
                .mapToInt(RaceView.ResultView::pointsTotal).sum();
        int awayTotal = results.stream()
                .filter(r -> r.teamShortName().equals(awayShortName))
                .mapToInt(RaceView.ResultView::pointsTotal).sum();

        String trackName = race.getTrack() != null ? race.getTrack().getName() : null;
        String carName = race.getCar() != null ? race.getCar().getDisplayName() : null;

        return new RaceView(homeShortName, awayShortName,
                trackName, carName, homeTotal, awayTotal, !race.getResults().isEmpty(), results);
    }

    private String slugify(String input) {
        return input.toLowerCase()
                .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    record SeasonEntry(Season season, String slug) {}

    record DriverEntry(String psnId, String driverProfileUrl, int totalPoints) {}

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
