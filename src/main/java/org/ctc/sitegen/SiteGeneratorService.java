package org.ctc.sitegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.SiteSlugs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

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
    private final SiteSlugService siteSlugService;
    private static final String STAGING_INFIX = ".generating-";

    private final ReentrantLock generationLock = new ReentrantLock();

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

    /**
     * Generates the whole site into a staging directory next to the output directory and
     * replaces the output only when generation and validation succeeded; a failure leaves the
     * previous site in place. A generation that starts while another one runs, in this or another
     * process, is rejected.
     */
    @Transactional(readOnly = true)
    public GenerationResult generate() {
        if (!generationLock.tryLock()) {
            return busy();
        }
        try {
            Path target = resolveTarget();
            Files.createDirectories(Objects.requireNonNull(target.getParent(), "output directory has no parent"));
            Path lockFile = target.resolveSibling(target.getFileName() + ".lock");
            try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var processLock = channel.tryLock()) {
                return processLock == null ? busy() : generateAndPublish(target);
            } catch (OverlappingFileLockException e) {
                return busy();
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("Site generation could not start", e);
            var result = new GenerationResult();
            result.addError("Generation failed: " + e.getMessage());
            return result;
        } finally {
            generationLock.unlock();
        }
    }

    private static GenerationResult busy() {
        var result = new GenerationResult();
        result.addError("A site generation is already running. Try again when it has finished.");
        return result;
    }

    private Path resolveTarget() {
        Path target = Path.of(siteProperties.getOutputDir()).toAbsolutePath().normalize();
        if (target.getNameCount() < 2) {
            throw new IllegalArgumentException("Refusing to publish to dangerously short path: " + target);
        }
        var protectedDirs = new java.util.ArrayList<>(List.of(Path.of("").toAbsolutePath().normalize()));
        if (uploadDir != null) {
            protectedDirs.add(Path.of(uploadDir).toAbsolutePath().normalize());
        }
        for (Path protectedDir : protectedDirs) {
            if (protectedDir.startsWith(target)) {
                throw new IllegalArgumentException(
                        "Refusing to publish to " + target + ", which contains " + protectedDir);
            }
        }
        return target;
    }

    private GenerationResult generateAndPublish(Path target) {
        var result = new GenerationResult();
        Path staging = null;
        try {
            removeStaleStaging(target);
            restorePrevious(target);
            staging = Files.createDirectory(target.resolveSibling(target.getFileName() + STAGING_INFIX + UUID.randomUUID()));
            generateInto(staging, result);
            if (!result.hasErrors()) {
                validate(staging);
                publish(staging, target);
                log.info("Site generation complete: {} pages", result.getPagesGenerated());
            }
        } catch (IOException | RuntimeException e) {
            log.error("Site generation failed; the previous site stays published", e);
            result.addError(failureMessage(e));
        } finally {
            if (staging != null) {
                try {
                    deleteTree(staging);
                } catch (IOException e) {
                    log.warn("Could not remove the staging directory {}", staging, e);
                }
            }
        }
        return result;
    }

    private static String failureMessage(Exception e) {
        if (e instanceof DataIntegrityViolationException) {
            return "Generation failed: another generation stored profile URLs at the same time. Try again.";
        }
        if (e instanceof SiteSlugs.MissingSlugException) {
            return "Generation failed: " + e.getMessage() + ". A team or driver was added meanwhile; try again.";
        }
        return "Generation failed: " + e.getMessage();
    }

    private void generateInto(Path outPath, GenerationResult result) throws IOException {
        var slugs = siteSlugService.allocate();
        slugs.shared().forEach(shared -> result.addWarning("Profiles shared the URL " + shared.slug()
                + ".html, which now lists them (" + shared.kind().name().toLowerCase(Locale.ENGLISH) + ")"));

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
                    hasPlayoff, playoffSeasonSlug, slugs);
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
        generateTeamsOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, slugs, result);
        generateDriversOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, slugs, result);

        // Generate alltime pages (filtered to production seasons only)
        generateAlltimeStandings(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, slugs, result);
        generateAlltimeDriverRanking(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, slugs, result);

        // Copy static assets
        copyAssets(outPath, result);
    }

    private static void validate(Path staging) throws IOException {
        for (String required : List.of("index.html", "archive.html", "assets")) {
            if (!Files.exists(staging.resolve(required))) {
                throw new IOException("Generated site is incomplete: " + required + " is missing");
            }
        }
    }

    /**
     * Swaps the staged site in by renaming directories. An output directory on another file
     * store than its parent (a mount point) cannot be renamed; it gets the new files copied in,
     * and the files the new site no longer has are removed afterwards.
     */
    private static void publish(Path staging, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        if (!Files.getFileStore(target).equals(Files.getFileStore(staging))) {
            copyInto(staging, target);
            return;
        }
        Path previous = previousOf(target);
        deleteTree(previous);
        Files.move(target, previous, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(previous, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException restoreFailure) {
                e.addSuppressed(restoreFailure);
            }
            throw e;
        }
        deleteTree(previous);
    }

    /** Copies the staged site over {@code target}, then deletes what the staged site does not contain. */
    static void copyInto(Path staging, Path target) throws IOException {
        try (var paths = Files.walk(staging)) {
            for (Path source : paths.toList()) {
                Path destination = target.resolve(staging.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        try (var paths = Files.walk(target)) {
            for (Path existing : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!existing.equals(target) && !Files.exists(staging.resolve(target.relativize(existing)))) {
                    deleteTree(existing);
                }
            }
        }
    }

    private static Path previousOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".previous");
    }

    /** Puts back the previous site when an earlier publish stopped between its two renames. */
    private static void restorePrevious(Path target) throws IOException {
        Path previous = previousOf(target);
        if (!Files.exists(target) && Files.isDirectory(previous)) {
            log.warn("Restoring the previous site from {}", previous);
            Files.move(previous, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static void removeStaleStaging(Path target) throws IOException {
        String prefix = target.getFileName() + STAGING_INFIX;
        try (var siblings = Files.list(Objects.requireNonNull(target.getParent()))) {
            for (Path sibling : siblings.filter(p -> String.valueOf(p.getFileName()).startsWith(prefix)).toList()) {
                deleteTree(sibling);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
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
        templateWriter.write("site/index", ctx, outPath.resolve("index.html"), outPath, activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generatePlayoffBracket(Path outPath, Season season, String activeSeasonSlug,
                                         String activeSeasonName, GenerationResult result) throws IOException {
        var playoffOpt = playoffRepository.findBySeasonId(season.getId());
		if (playoffOpt.isEmpty()) {
			return;
		}

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
        templateWriter.write("site/playoff-bracket", ctx, dir.resolve("playoff.html"), outPath, activeSeasonSlug, activeSeasonName);
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
        templateWriter.write("site/archive", ctx, outPath.resolve("archive.html"), outPath, activeSeasonSlug, activeSeasonName);
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
        templateWriter.write("site/links", ctx, outPath.resolve("links.html"), outPath, activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateTeamsOverview(Path outPath, List<Season> productionSeasons,
                                       String activeSeasonSlug, String activeSeasonName,
                                       SiteSlugs slugs, GenerationResult result) throws IOException {
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
                                        + "/team/" + slugs.team(team.getId()) + ".html";
                                break;
                            }
                        }
                    }
                    String logoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
                    return new TeamOverviewEntry(
                            team.getShortName(),
                            slugs.team(team.getId()),
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
        templateWriter.write("site/teams", ctx, outPath.resolve("teams.html"), outPath, activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateDriversOverview(Path outPath, List<Season> productionSeasons,
                                         String activeSeasonSlug, String activeSeasonName,
                                         SiteSlugs slugs, GenerationResult result) throws IOException {
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
                            + "/driver/" + slugs.driver(driver.getId()) + ".html";
                    String teamName = latestInfo.team().getShortName();
                    return new DriverOverviewEntry(
                            driver.getPsnId(),
                            slugs.driver(driver.getId()),
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
        templateWriter.write("site/drivers", ctx, outPath.resolve("drivers.html"), outPath, activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateAlltimeStandings(Path outPath, List<Season> productionSeasons,
                                           String activeSeasonSlug, String activeSeasonName,
                                           SiteSlugs slugs, GenerationResult result) throws IOException {
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
				if (regularPhaseOpt.isEmpty()) {
					continue;
				}
                var seasonStandings = standingsService.calculateStandings(regularPhaseOpt.get().getId(), null);
                if (seasonStandings.stream().anyMatch(st -> st.getTeam().getId().equals(teamId))) {
                    teamSlugMap.put(teamId, "season/" + siteSlugger.slugify(season.getDisplayLabel())
                            + "/team/" + slugs.team(s.getTeam().getId()) + ".html");
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
        templateWriter.write("site/alltime-standings", ctx, outPath.resolve("alltime-standings.html"), outPath,
                activeSeasonSlug, activeSeasonName);
        result.incrementPages();
    }

    private void generateAlltimeDriverRanking(Path outPath, List<Season> productionSeasons,
                                               String activeSeasonSlug, String activeSeasonName,
                                               SiteSlugs slugs, GenerationResult result) throws IOException {
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
                        + "/driver/" + slugs.driver(sd.getDriver().getId()) + ".html");
            }
        }

        ctx.setVariable("driverRanking", driverRanking);
        ctx.setVariable("driverSlugMap", driverSlugMap);
        ctx.setVariable("driverTeamsMap", driverTeamsMap);
        ctx.setVariable("currentPage", "alltime-driver-ranking");
        ctx.setVariable("seasonSlug", null);
        ctx.setVariable("seasonName", null);
        ctx.setVariable("breadcrumbCurrent", "Alltime Driver Ranking");
        templateWriter.write("site/alltime-driver-ranking", ctx, outPath.resolve("alltime-driver-ranking.html"), outPath,
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
            // NP: target has at least 4 path components — parent is guaranteed non-null.
            // See config/spotbugs-exclude.xml SiteGeneratorService.copyLogoToAssets NP_NULL entry.
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
			if (!resource.isReadable()) {
				continue;
			}

            String uri = resource.getURI().toString();
            int idx = uri.indexOf(prefix);
			if (idx < 0) {
				continue;
			}

            String relativePath = uri.substring(idx + prefix.length());
			if (relativePath.isEmpty()) {
				continue;
			}

            Path target = assetsDir.resolve(relativePath);
            // NP: target = assetsDir.resolve(non-empty-path) — always multi-component, parent non-null.
            // See config/spotbugs-exclude.xml SiteGeneratorService.copyAssets NP_NULL entry.
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
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void incrementPages() { pagesGenerated++; }
        public void addError(String error) { errors.add(error); }
        public int getPagesGenerated() { return pagesGenerated; }
        public java.util.List<String> getErrors() { return java.util.Collections.unmodifiableList(errors); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public void addWarning(String warning) { warnings.add(warning); }
        public java.util.List<String> getWarnings() { return java.util.Collections.unmodifiableList(warnings); }
    }
}
