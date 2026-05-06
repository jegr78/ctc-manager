package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/team-profile.html} page generation.
 *
 * <p>Phase-62 Plan-0 extraction: body lifted verbatim from
 * {@code SiteGeneratorService.generateTeamProfiles} (lines 269-346 pre-extraction). Behavior
 * is byte-identical to the legacy private method (SC4 invariant).
 *
 * <p>Critical preservation: the RaceLineup-as-Source-of-Truth pattern (lines 296-313 of the
 * legacy code) is lifted verbatim per {@code feedback_racelineup_source_of_truth}.
 *
 * <p>{@code copyLogoToAssets} is duplicated from {@code SiteGeneratorService} per the
 * RESEARCH.md interfaces note (choice b): the orchestrator's
 * {@code generateTeamsOverview} still uses its own copy, so the helper has its own copy too.
 * The two copies are 27 LOC each and decouple the helpers cleanly without circular deps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamProfilePageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final TeamRepository teamRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceResultRepository raceResultRepository;
    private final StandingsService standingsService;
    private final SeasonPhaseService seasonPhaseService;

    @lombok.Setter
    @Value("${app.upload-dir:data/dev/uploads}")
    private String uploadDir;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var outPath = ctx.outPath();
        var teams = teamRepository.findAll();
        // Phase-aware standings (REGULAR phase combined-view).
        var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
        var standings = standingsService.calculateStandings(regularPhase.getId(), null);

        // Pre-fetch all lineup entries and season drivers for the season to avoid N+1 queries
        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());
        var allSeasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

        for (var team : teams) {
            var teamStanding = standings.stream()
                    .filter(s -> s.getTeam().getId().equals(team.getId()))
                    .findFirst().orElse(null);

            if (teamStanding == null) continue;

            var context = new Context(Locale.ENGLISH);
            context.setVariable("season", season);
            context.setVariable("team", team);
            context.setVariable("standing", teamStanding);

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
                        String driverProfileUrl = "../driver/" + siteSlugger.slugify(driver.getPsnId()) + ".html";
                        return new SiteGeneratorService.DriverEntry(driver.getPsnId(), driverProfileUrl, totalPoints);
                    })
                    .toList();
            context.setVariable("drivers", driverEntries);

            // Compute assetsPath for this team profile page (same as writeTemplate does)
            Path teamDir = outPath.resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel())).resolve("team");
            String assetsPath = teamDir.relativize(outPath.resolve("assets")).toString().replace('\\', '/');

            // Copy logo and get relative path (null if no logo or file missing)
            String teamLogoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
            context.setVariable("teamLogoRelPath", teamLogoRelPath);

            context.setVariable("currentPage", "team");
            context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
            context.setVariable("seasonName", season.getName());
            context.setVariable("hasPlayoff", ctx.hasPlayoff());
            context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
            context.setVariable("breadcrumbCurrent", team.getShortName());

            Files.createDirectories(teamDir);
            templateWriter.write("site/team-profile", context, teamDir.resolve(siteSlugger.slugify(team.getShortName()) + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
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
}
