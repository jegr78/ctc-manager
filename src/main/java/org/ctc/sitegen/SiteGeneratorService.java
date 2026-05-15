package org.ctc.sitegen;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@EnableConfigurationProperties(SiteProperties.class)
public class SiteGeneratorService {

    private final SeasonRepository seasonRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final PlayoffBracketViewService playoffBracketViewService;
    private final PlayoffRepository playoffRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final SiteProperties siteProperties;
    private final YouTubeScraperService youTubeScraperService;
    private final SeasonPhaseService seasonPhaseService;
    private final SiteSlugger siteSlugger;
    private final TemplateWriter templateWriter;
    private final StandingsPageGenerator standingsPageGenerator;
    private final DriverRankingPageGenerator driverRankingPageGenerator;
    private final MatchdaysPageGenerator matchdaysPageGenerator;
    private final TeamProfilePageGenerator teamProfilePageGenerator;
    private final DriverProfilePageGenerator driverProfilePageGenerator;

    @Value("${app.upload-dir:data/dev/uploads}")
    private String uploadDir;

    public void setOutputDir(String outputDir) {
        siteProperties.setOutputDir(outputDir);
    }

    /** Sets {@code uploadDir} on the orchestrator and forwards to {@link TeamProfilePageGenerator}. */
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
        teamProfilePageGenerator.setUploadDir(uploadDir);
    }

    @Transactional(readOnly = true)
    public GenerationResult generate() {
        var result = new GenerationResult();
        Path outPath = Path.of(siteProperties.getOutputDir());

        try {
            cleanOutputDirectory(outPath);
            Files.createDirectories(outPath);

            // Find active season
            var activeSeason = seasonRepository.findByActiveTrue().orElse(null);
            String activeSeasonSlug = activeSeason != null ? siteSlugger.slugify(activeSeason.getDisplayLabel()) : "";
            String activeSeasonName = activeSeason != null ? activeSeason.getDisplayLabel() : "";
            var allSeasons = seasonRepository.findAll();
            var productionSeasons = allSeasons.stream()
                    .filter(s -> !s.getName().contains("Test"))
                    .toList();

            // Generate index
            generateIndex(outPath, activeSeason, activeSeasonSlug, activeSeasonName, result);

            // Generate pages for each season
            for (var season : productionSeasons) {
                // Skip seasons without a REGULAR phase. Every production Season has one;
                // skipping mirrors the legacy behaviour where seasons without standings
                // simply rendered empty pages.
                if (seasonPhaseService.findByType(season.getId(), org.ctc.domain.model.PhaseType.REGULAR).isEmpty()) {
                    log.debug("Skipping season {} — no REGULAR phase", season.getName());
                    continue;
                }
                String playoffSeasonSlug = resolvePlayoffSeasonSlug(season);
                boolean hasPlayoff = playoffSeasonSlug != null;
                var ctx = new org.ctc.sitegen.model.GenerationContext(
                        outPath, season, activeSeasonSlug, activeSeasonName,
                        hasPlayoff, playoffSeasonSlug);
                standingsPageGenerator.generate(ctx, result);
                driverRankingPageGenerator.generate(ctx, result);
                matchdaysPageGenerator.generateDetails(ctx, result);
                matchdaysPageGenerator.generateIndex(ctx, result);
                teamProfilePageGenerator.generate(ctx, result);
                driverProfilePageGenerator.generate(ctx, result);
                generatePlayoffBracket(outPath, season, activeSeasonSlug, activeSeasonName, result);
            }

            // Generate archive
            generateArchive(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);

            // Generate links page
            generateLinks(outPath, siteProperties.getLinks(), activeSeasonSlug, activeSeasonName, result);

            // Generate overview pages
            generateTeamsOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);
            generateDriversOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);

            // Generate alltime pages (filtered to production seasons only)
            generateAlltimeStandings(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);
            generateAlltimeDriverRanking(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);

            // Copy static assets
            copyAssets(outPath, result);

            log.info("Site generation complete: {} pages", result.getPagesGenerated());
        } catch (IOException e) {
            log.error("Site generation failed", e);
            result.addError("Generation failed: " + e.getMessage());
        }

        return result;
    }

    private void cleanOutputDirectory(Path outPath) throws IOException {
        if (outPath.getNameCount() < 2) {
            throw new IllegalArgumentException("Refusing to clean dangerously short path: " + outPath);
        }
        if (!Files.exists(outPath)) {
            return; // Non-existent dir — createDirectories() below handles creation.
        }
        log.info("Cleaning output directory: {}", outPath);
        Files.walkFileTree(outPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                log.debug("Deleted file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                if (!dir.equals(outPath)) {  // do not delete root itself
                    Files.delete(dir);
                    log.debug("Deleted directory: {}", dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void generateIndex(Path outPath, Season activeSeason,
                                String activeSeasonSlug, String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);

        // Scrape YouTube video ID, falling back to the configured value.
        String videoId = youTubeScraperService.scrapeVideoId(
                siteProperties.getYoutubeChannelUrl(),
                siteProperties.getYoutubeVideoId());
        // Sanitise scraped videoId to prevent JS injection via a malformed scrape result.
        if (videoId != null && !videoId.matches("[a-zA-Z0-9_\\-]{1,20}")) {
            log.warn("Scraped videoId '{}' failed safety check, using fallback", videoId);
            videoId = siteProperties.getYoutubeVideoId();
        }
        ctx.setVariable("videoId", videoId);

        // No standings, teamSlugMap, lastMatchday, or lastMatchdayRaces; activeSeasonSlug
        // is passed to writeTemplate for the Standings tile conditional link.

        ctx.setVariable("currentPage", "home");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", null);
        templateWriter.write("site/index", ctx, outPath.resolve("index.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
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
        ctx.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("hasPlayoff", true);
        ctx.setVariable("breadcrumbCurrent", "Playoff");
        ctx.setVariable("pageTitle", "Playoffs " + season.getDisplayLabel());

        var dir = outPath.resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        templateWriter.write("site/playoff-bracket", ctx, dir.resolve("playoff.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateArchive(Path outPath, List<Season> allSeasons, String activeSeasonSlug,
                                   String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        var seasonEntries = allSeasons.stream()
                .sorted(java.util.Comparator
                        .comparingInt(Season::getYear).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(Season::getNumber).reversed()))
                .map(this::buildSeasonEntry)
                .toList();
        ctx.setVariable("seasonEntries", seasonEntries);
        ctx.setVariable("currentPage", "archive");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", null);
        templateWriter.write("site/archive", ctx, outPath.resolve("archive.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateLinks(Path outPath, List<SiteProperties.LinkEntry> links,
                                String activeSeasonSlug, String activeSeasonName,
                                GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("links", links);
        ctx.setVariable("currentPage", "links");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Links");
        templateWriter.write("site/links", ctx, outPath.resolve("links.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateTeamsOverview(Path outPath, List<Season> productionSeasons,
                                       String activeSeasonSlug, String activeSeasonName,
                                       GenerationResult result) throws IOException {
        var sortedSeasons = productionSeasons.stream()
                .sorted(java.util.Comparator.comparing(Season::getYear).thenComparing(Season::getNumber))
                .toList();

        // Collect teams that have at least one season with standings (profile page exists)
        var teamsWithProfiles = new java.util.HashSet<java.util.UUID>();
        var standingsBySeasonId = new java.util.HashMap<java.util.UUID, java.util.Set<java.util.UUID>>();
        for (var season : sortedSeasons) {
            // Phase-aware standings via REGULAR phase; skip seasons without one.
            var regularPhaseOpt = seasonPhaseService.findByType(season.getId(), org.ctc.domain.model.PhaseType.REGULAR);
            if (regularPhaseOpt.isEmpty()) {
                standingsBySeasonId.put(season.getId(), java.util.Set.of());
                continue;
            }
            var standings = standingsService.calculateStandings(regularPhaseOpt.get().getId(), null);
            var teamIds = standings.stream()
                    .map(s -> s.getTeam().getId())
                    .collect(java.util.stream.Collectors.toSet());
            standingsBySeasonId.put(season.getId(), teamIds);
            teamsWithProfiles.addAll(teamIds);
        }

        var teamToSeasons = new java.util.LinkedHashMap<Team, java.util.LinkedHashSet<Season>>();
        for (var season : sortedSeasons) {
            for (var st : seasonTeamRepository.findBySeasonId(season.getId())) {
                var team = st.getTeam();
                if (!team.isSubTeam()) {
                    teamToSeasons.computeIfAbsent(team, k -> new java.util.LinkedHashSet<>()).add(season);
                }
            }
        }

        String assetsPath = "assets";
        var teamEntries = teamToSeasons.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().getShortName()))
                .map(e -> {
                    var team = e.getKey();
                    var seasons = new java.util.ArrayList<>(e.getValue());
                    boolean hasProfile = teamsWithProfiles.contains(team.getId());
                    // Find the latest season where the team HAS a profile (standings exist)
                    String profileUrl = null;
                    if (hasProfile) {
                        for (int i = seasons.size() - 1; i >= 0; i--) {
                            var s = seasons.get(i);
                            if (standingsBySeasonId.getOrDefault(s.getId(), java.util.Set.of()).contains(team.getId())) {
                                profileUrl = "season/" + siteSlugger.slugify(s.getDisplayLabel())
                                        + "/team/" + siteSlugger.slugify(team.getShortName()) + ".html";
                                break;
                            }
                        }
                    }
                    String logoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
                    return new TeamOverviewEntry(
                            team.getShortName(),
                            siteSlugger.slugify(team.getShortName()),
                            logoRelPath,
                            profileUrl,
                            seasons.stream().map(s -> siteSlugger.slugify(s.getDisplayLabel())).toList(),
                            seasons.stream().map(Season::getDisplayLabel).toList()
                    );
                })
                .toList();

        var seasonEntries = sortedSeasons.stream()
                .map(this::buildSeasonEntry)
                .toList();

        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("teamEntries", teamEntries);
        ctx.setVariable("seasonEntries", seasonEntries);
        ctx.setVariable("currentPage", "teams");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Teams");
        templateWriter.write("site/teams", ctx, outPath.resolve("teams.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateDriversOverview(Path outPath, List<Season> productionSeasons,
                                         String activeSeasonSlug, String activeSeasonName,
                                         GenerationResult result) throws IOException {
        var sortedSeasons = productionSeasons.stream()
                .sorted(java.util.Comparator.comparing(Season::getYear).thenComparing(Season::getNumber))
                .toList();

        var driverToSeasonTeams = new java.util.LinkedHashMap<org.ctc.domain.model.Driver, java.util.List<SeasonDriverInfo>>();
        for (var season : sortedSeasons) {
            for (var sd : seasonDriverRepository.findBySeasonId(season.getId())) {
                driverToSeasonTeams.computeIfAbsent(sd.getDriver(), k -> new java.util.ArrayList<>())
                        .add(new SeasonDriverInfo(season, sd.getTeam()));
            }
        }

        var driverEntries = driverToSeasonTeams.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().getPsnId()))
                .map(e -> {
                    var driver = e.getKey();
                    var infos = e.getValue();
                    var latestInfo = infos.getLast();
                    String profileUrl = "season/" + siteSlugger.slugify(latestInfo.season().getDisplayLabel())
                            + "/driver/" + siteSlugger.slugify(driver.getPsnId()) + ".html";
                    String teamName = latestInfo.team().getShortName();
                    return new DriverOverviewEntry(
                            driver.getPsnId(),
                            siteSlugger.slugify(driver.getPsnId()),
                            teamName,
                            profileUrl,
                            infos.stream().map(i -> siteSlugger.slugify(i.season().getDisplayLabel())).toList(),
                            infos.stream().map(i -> i.season().getDisplayLabel()).toList()
                    );
                })
                .toList();

        var seasonEntries = sortedSeasons.stream()
                .map(this::buildSeasonEntry)
                .toList();

        var ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("driverEntries", driverEntries);
        ctx.setVariable("seasonEntries", seasonEntries);
        ctx.setVariable("currentPage", "drivers");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Drivers");
        templateWriter.write("site/drivers", ctx, outPath.resolve("drivers.html"), activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateAlltimeStandings(Path outPath, List<Season> productionSeasons,
                                           String activeSeasonSlug,
                                           String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        var standings = standingsService.calculateAlltimeStandings(productionSeasons);

        // Build teamSlugMap linking to latest season profile (root-relative paths)
        var sortedSeasons = productionSeasons.stream()
                .sorted(java.util.Comparator.comparing(Season::getYear).thenComparing(Season::getNumber).reversed())
                .toList();
        var teamSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var s : standings) {
            var teamId = s.getTeam().getId();
            for (var season : sortedSeasons) {
                // Phase-aware standings via the REGULAR phase. Seasons without a REGULAR
                // phase are skipped by the outer generate() loop, so the empty case here is
                // defensive.
                var regularPhaseOpt = seasonPhaseService.findByType(season.getId(), org.ctc.domain.model.PhaseType.REGULAR);
                if (regularPhaseOpt.isEmpty()) continue;
                var seasonStandings = standingsService.calculateStandings(regularPhaseOpt.get().getId(), null);
                if (seasonStandings.stream().anyMatch(st -> st.getTeam().getId().equals(teamId))) {
                    teamSlugMap.put(teamId, "season/" + siteSlugger.slugify(season.getDisplayLabel())
                            + "/team/" + siteSlugger.slugify(s.getTeam().getShortName()) + ".html");
                    break;
                }
            }
        }

        ctx.setVariable("standings", standings);
        ctx.setVariable("teamSlugMap", teamSlugMap);
        ctx.setVariable("currentPage", "alltime-standings");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Alltime Standings");
        templateWriter.write("site/alltime-standings", ctx, outPath.resolve("alltime-standings.html"),
                activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateAlltimeDriverRanking(Path outPath, List<Season> productionSeasons,
                                               String activeSeasonSlug,
                                               String activeSeasonName, GenerationResult result) throws IOException {
        var ctx = new Context(Locale.ENGLISH);
        var seasonIds = productionSeasons.stream().map(Season::getId).toList();
        var driverRanking = driverRankingService.calculateAlltimeRanking(seasonIds);

        // Build driverSlugMap (latest season profile) and driverTeamsMap (all teams per driver)
        var chronologicalSeasons = productionSeasons.stream()
                .sorted(java.util.Comparator.comparing(Season::getYear).thenComparing(Season::getNumber))
                .toList();
        var driverSlugMap = new java.util.HashMap<java.util.UUID, String>();
        var driverTeamsMap = new java.util.HashMap<java.util.UUID, java.util.List<String>>();
        for (var season : chronologicalSeasons) {
            var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
            for (var sd : seasonDrivers) {
                var driverId = sd.getDriver().getId();
                var teamName = sd.getTeam().getParentOrSelf().getShortName();
                driverTeamsMap.computeIfAbsent(driverId, k -> new java.util.ArrayList<>());
                if (!driverTeamsMap.get(driverId).contains(teamName)) {
                    driverTeamsMap.get(driverId).add(teamName);
                }
                // Latest season wins for the profile link
                driverSlugMap.put(driverId, "season/" + siteSlugger.slugify(season.getDisplayLabel())
                        + "/driver/" + siteSlugger.slugify(sd.getDriver().getPsnId()) + ".html");
            }
        }

        ctx.setVariable("driverRanking", driverRanking);
        ctx.setVariable("driverSlugMap", driverSlugMap);
        ctx.setVariable("driverTeamsMap", driverTeamsMap);
        ctx.setVariable("currentPage", "alltime-driver-ranking");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Alltime Driver Ranking");
        templateWriter.write("site/alltime-driver-ranking", ctx, outPath.resolve("alltime-driver-ranking.html"),
                activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private String resolvePlayoffSeasonSlug(Season season) {
        var directPlayoff = playoffRepository.findBySeasonId(season.getId());
        if (directPlayoff.isPresent()) {
            return siteSlugger.slugify(season.getDisplayLabel());
        }
        return null;
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

    /**
     * startDate/endDate live on the REGULAR SeasonPhase, not on Season; pre-computed here
     * so {@code archive.html} does not need SpEL traversal logic.
     */
    record SeasonEntry(Season season, String slug, java.time.LocalDate startDate, java.time.LocalDate endDate) {}

    /**
     * Builds a SeasonEntry, pulling startDate/endDate from the REGULAR SeasonPhase. If no
     * REGULAR phase exists, the dates default to {@code null} (the archive template guards
     * both fields with {@code th:if}).
     */
    private SeasonEntry buildSeasonEntry(Season s) {
        var regular = seasonPhaseService.findByType(s.getId(), org.ctc.domain.model.PhaseType.REGULAR);
        var startDate = regular.map(org.ctc.domain.model.SeasonPhase::getStartDate).orElse(null);
        var endDate = regular.map(org.ctc.domain.model.SeasonPhase::getEndDate).orElse(null);
        return new SeasonEntry(s, siteSlugger.slugify(s.getDisplayLabel()), startDate, endDate);
    }

    public record DriverEntry(String psnId, String driverProfileUrl, int totalPoints) {}

    record TeamOverviewEntry(String shortName, String teamSlug, String logoRelPath,
                             String profileUrl, List<String> seasonSlugs, List<String> seasonLabels) {}

    record DriverOverviewEntry(String psnId, String driverSlug, String teamName,
                               String profileUrl, List<String> seasonSlugs, List<String> seasonLabels) {}

    record SeasonDriverInfo(Season season, Team team) {}

    public static class GenerationResult {
        private int pagesGenerated;
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public void incrementPages() { pagesGenerated++; }
        public void addError(String error) { errors.add(error); }
        public int getPagesGenerated() { return pagesGenerated; }
        public java.util.List<String> getErrors() { return java.util.Collections.unmodifiableList(errors); }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
