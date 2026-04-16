package org.ctc.sitegen;

import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
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
            String activeSeasonName = activeSeason != null ? activeSeason.getDisplayLabel() : "";
            var allSeasons = seasonRepository.findAll();
            var productionSeasons = allSeasons.stream()
                    .filter(s -> !s.getName().contains("Test"))
                    .toList();

            // Generate index
            generateIndex(outPath, activeSeason, productionSeasons, activeSeasonSlug, activeSeasonName, result);

            // Generate pages for each season
            for (var season : productionSeasons) {
                generateStandings(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generateDriverRanking(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generateMatchdays(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generateMatchdayIndex(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generateTeamProfiles(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generateDriverProfiles(outPath, season, activeSeasonSlug, activeSeasonName, result);
                generatePlayoffBracket(outPath, season, activeSeasonSlug, activeSeasonName, result);
            }

            // Generate archive
            generateArchive(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);

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
                                String activeSeasonSlug, String activeSeasonName, GenerationResult result) throws IOException {
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
                var indexLineups = raceLineupRepository.findByRaceMatchdaySeasonId(activeSeason.getId());
                ctx.setVariable("lastMatchday", lastMatchday);
                ctx.setVariable("lastMatchdayRaces", raceRepository.findByMatchdayId(lastMatchday.getId()).stream()
                        .map(r -> toRaceView(r, activeSeason, "./season/" + activeSeasonSlug + "/driver/", indexLineups)).toList());
            }
        }

        ctx.setVariable("currentPage", null);
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", null);
        writeTemplate("site/index", ctx, outPath.resolve("index.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateStandings(Path outPath, Season season, String activeSeasonSlug,
                                    String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        var standings = standingsService.calculateStandings(season.getId());
        var teamSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var s : standings) {
            teamSlugMap.put(s.getTeam().getId(), "team/" + slugify(s.getTeam().getShortName()) + ".html");
        }
        ctx.setVariable("standings", standings);
        ctx.setVariable("teamSlugMap", teamSlugMap);

        ctx.setVariable("currentPage", "standings");
        ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("breadcrumbCurrent", "Standings");

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/standings", ctx, dir.resolve("standings.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateDriverRanking(Path outPath, Season season, String activeSeasonSlug,
                                        String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        var driverRanking = driverRankingService.calculateRanking(season.getId());
        var driverSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var r : driverRanking) {
            driverSlugMap.put(r.getDriver().getId(), "driver/" + slugify(r.getDriver().getPsnId()) + ".html");
        }
        ctx.setVariable("driverRanking", driverRanking);
        ctx.setVariable("driverSlugMap", driverSlugMap);

        ctx.setVariable("currentPage", "driver-ranking");
        ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("breadcrumbCurrent", "Driver Ranking");

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/driver-ranking", ctx, dir.resolve("driver-ranking.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateMatchdays(Path outPath, Season season, String activeSeasonSlug,
                                    String activeSeasonName, GenerationResult result) throws IOException {
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
        // Pre-fetch all lineups for the season to avoid per-result repository queries in toRaceView
        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());

        for (var matchday : matchdays) {
            var ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("season", season);
            ctx.setVariable("matchday", matchday);
            var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
                    .map(r -> toRaceView(r, season, "../driver/", allLineups)).toList();
            ctx.setVariable("races", raceViews);

            ctx.setVariable("currentPage", "matchdays");
            ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
            ctx.setVariable("seasonName", season.getName());
            ctx.setVariable("breadcrumbCurrent", matchday.getLabel());

            var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("matchday");
            Files.createDirectories(dir);
            writeTemplate("site/matchday", ctx, dir.resolve(slugify(matchday.getLabel()) + ".html"), activeSeasonSlug, activeSeasonName);
            result.incrementPages();
        }
    }

    private void generateTeamProfiles(Path outPath, Season season, String activeSeasonSlug,
                                       String activeSeasonName, GenerationResult result) throws IOException {
        var teams = teamRepository.findAll();
        var standings = standingsService.calculateStandings(season.getId());

        // Pre-fetch all lineup entries and season drivers for the season to avoid N+1 queries
        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());
        var allSeasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

        for (var team : teams) {
            var teamStanding = standings.stream()
                    .filter(s -> s.getTeam().getId().equals(team.getId()))
                    .findFirst().orElse(null);

            if (teamStanding == null) continue;

            var ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("season", season);
            ctx.setVariable("team", team);
            ctx.setVariable("standing", teamStanding);

            // Load team drivers for Drivers section via RaceLineup (source of truth per CLAUDE.md).
            // A driver belongs to this team if their lineup entry references the team directly
            // or references a sub-team whose parent is this team.
            // Fall back to SeasonDriver only when no lineup entries exist for the season.
            var lineupDrivers = allLineups.stream()
                    .filter(rl -> {
                        var rlTeam = rl.getTeam();
                        return rlTeam.getId().equals(team.getId())
                                || (rlTeam.getParentTeam() != null
                                    && rlTeam.getParentTeam().getId().equals(team.getId()));
                    })
                    .map(rl -> rl.getDriver())
                    .distinct()
                    .toList();

            var driversToShow = lineupDrivers.isEmpty()
                    ? allSeasonDrivers.stream()
                            .filter(sd -> sd.getTeam().getId().equals(team.getId()))
                            .map(sd -> sd.getDriver())
                            .distinct()
                            .toList()
                    : lineupDrivers;

            var driverEntries = driversToShow.stream()
                    .map(driver -> {
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

            ctx.setVariable("currentPage", "team");
            ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
            ctx.setVariable("seasonName", season.getName());
            ctx.setVariable("breadcrumbCurrent", team.getShortName());

            Files.createDirectories(teamDir);
            writeTemplate("site/team-profile", ctx, teamDir.resolve(slugify(team.getShortName()) + ".html"), activeSeasonSlug, activeSeasonName);
            result.incrementPages();
        }
    }

    private void generateDriverProfiles(Path outPath, Season season, String activeSeasonSlug,
                                         String activeSeasonName, GenerationResult result) throws IOException {
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
            int total = results.stream().mapToInt(r -> r.getPointsTotal()).sum();
            ctx.setVariable("totalRaces", results.size());
            ctx.setVariable("totalPoints", total);
            ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) total / results.size());
            ctx.setVariable("bestPosition", results.stream().mapToInt(r -> r.getPosition()).min().orElse(0));

            ctx.setVariable("currentPage", "driver");
            ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
            ctx.setVariable("seasonName", season.getName());
            ctx.setVariable("breadcrumbCurrent", driver.getPsnId());

            var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("driver");
            Files.createDirectories(dir);
            writeTemplate("site/driver-profile", ctx, dir.resolve(slugify(driver.getPsnId()) + ".html"), activeSeasonSlug, activeSeasonName);
            result.incrementPages();
        }
    }

    private void generatePlayoffBracket(Path outPath, Season season, String activeSeasonSlug,
                                         String activeSeasonName, GenerationResult result) throws IOException {
        var playoffOpt = playoffRepository.findBySeasonId(season.getId());
        if (playoffOpt.isEmpty()) return;

        var playoff = playoffOpt.get();
        var bracket = playoffBracketViewService.getBracketView(playoff.getId());

        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        ctx.setVariable("playoff", playoff);
        ctx.setVariable("bracket", bracket);

        ctx.setVariable("currentPage", "playoff");
        ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("breadcrumbCurrent", "Playoff");

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/playoff-bracket", ctx, dir.resolve("playoff.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateArchive(Path outPath, List<Season> allSeasons, String activeSeasonSlug,
                                   String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        var seasonEntries = allSeasons.stream()
                .map(s -> new SeasonEntry(s, slugify(s.getDisplayLabel())))
                .toList();
        ctx.setVariable("seasonEntries", seasonEntries);
        ctx.setVariable("currentPage", null);
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", null);
        writeTemplate("site/archive", ctx, outPath.resolve("archive.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateMatchdayIndex(Path outPath, Season season, String activeSeasonSlug,
                                        String activeSeasonName, GenerationResult result) throws IOException {
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("season", season);
        ctx.setVariable("matchdays", matchdays);

        // Pre-compute relative links from season/{slug}/ level
        var matchdayLinkMap = new java.util.LinkedHashMap<java.util.UUID, String>();
        for (var md : matchdays) {
            matchdayLinkMap.put(md.getId(), "matchday/" + slugify(md.getLabel()) + ".html");
        }
        ctx.setVariable("matchdayLinkMap", matchdayLinkMap);
        ctx.setVariable("currentPage", "matchdays");
        ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("breadcrumbCurrent", "Matchdays");

        var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        writeTemplate("site/matchdays", ctx, dir.resolve("matchdays.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void writeTemplate(String templateName, Context context, Path outputFile,
                                String activeSeasonSlug, String activeSeasonName) throws IOException {
        // Calculate relative paths from the output file location
        Path outRoot = Path.of(outputDir);
        Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
        Path relativeRoot = outputFile.getParent().relativize(outRoot);
        context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
        String rootStr = relativeRoot.toString().replace('\\', '/');
        context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
        context.setVariable("activeSeasonSlug", activeSeasonSlug != null ? activeSeasonSlug : "");
        context.setVariable("activeSeasonName", activeSeasonName != null ? activeSeasonName : "");

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

    private RaceView toRaceView(Race race, Season season, String driverUrlPrefix,
                                List<RaceLineup> seasonLineups) {
        var homeTeam = race.getHomeTeam();
        String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";

        var results = race.getResults().stream()
                .map(r -> {
                    // teamShortName: sub-team name for display (from RaceLineup, falls back to SeasonDriver)
                    // scoringTeamShortName: parent-resolved name for home/away aggregation
                    var lineupOpt = seasonLineups.stream()
                            .filter(rl -> rl.getRace().getId().equals(race.getId())
                                    && rl.getDriver().getId().equals(r.getDriver().getId()))
                            .findFirst();
                    String teamShortName = lineupOpt
                            .map(rl -> rl.getTeam().getShortName())
                            .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                                    .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                                    .map(sd -> sd.getTeam().getShortName())
                                    .findFirst().orElse("?"));
                    String scoringTeamShortName = lineupOpt
                            .map(rl -> rl.getTeam().getParentOrSelf().getShortName())
                            .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                                    .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                                    .map(sd -> sd.getTeam().getParentOrSelf().getShortName())
                                    .findFirst().orElse("?"));
                    String driverSlug = slugify(r.getDriver().getPsnId());
                    String driverProfileUrl = driverUrlPrefix + driverSlug + ".html";
                    return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName, scoringTeamShortName,
                            r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal(),
                            driverProfileUrl);
                })
                .toList();

        String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

        int homeTotal = results.stream()
                .filter(r -> r.scoringTeamShortName().equals(homeShortName))
                .mapToInt(RaceView.ResultView::pointsTotal).sum();
        int awayTotal = results.stream()
                .filter(r -> r.scoringTeamShortName().equals(awayShortName))
                .mapToInt(RaceView.ResultView::pointsTotal).sum();

        String trackName = race.getTrack() != null ? race.getTrack().getName() : null;
        String carName = race.getCar() != null ? race.getCar().getDisplayName() : null;

        boolean hasResults = !race.getResults().isEmpty();
        boolean homeTeamWon = hasResults && homeTotal > awayTotal;
        boolean awayTeamWon = hasResults && awayTotal > homeTotal;
        return new RaceView(homeShortName, awayShortName,
                trackName, carName, homeTotal, awayTotal, hasResults,
                homeTeamWon, awayTeamWon, results);
    }

    String slugify(String input) {
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
