package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.PhaseBreakdownEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/team-profile.html} page generation.
 *
 * <p>Uses {@code RaceLineup} as the source of truth for driver-team assignments.
 * {@code copyLogoToAssets} is kept local to avoid circular dependencies with
 * {@code SiteGeneratorService}.
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
    private final PhaseTeamRepository phaseTeamRepository;

    @lombok.Setter
    @Value("${app.upload-dir:data/dev/uploads}")
    private String uploadDir;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var outPath = ctx.outPath();
        var teams = teamRepository.findAll();
        // Phase-aware standings (REGULAR phase combined-view).
        var allPhases = seasonPhaseService.findAllPhases(season.getId());
        var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
        var standings = standingsService.calculateStandings(regularPhase.getId(), null);

        // Pre-fetch all lineup entries and season drivers for the season to avoid N+1 queries
        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());
        var allSeasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());

        // Pre-compute per-phase standings lists once per season (avoids N x M calculateStandings
        // calls when there are many teams). The map is keyed by phase id; each value is the
        // standings list for that phase's combined view (groupId=null).
        boolean seasonHasMultiplePhases = allPhases.size() >= 2;
        java.util.Map<java.util.UUID, List<StandingsService.TeamStanding>> phaseStandingsMap =
                new java.util.HashMap<>();
        if (seasonHasMultiplePhases) {
            for (SeasonPhase p : allPhases) {
                phaseStandingsMap.put(p.getId(),
                        standingsService.calculateStandings(p.getId(), null));
            }
        }

        for (var team : teams) {
            var teamStanding = standings.stream()
                    .filter(s -> s.getTeam().getId().equals(team.getId()))
                    .findFirst().orElse(null);

			if (teamStanding == null) {
				continue;
			}

            var context = new Context(Locale.ENGLISH);
            context.setVariable("season", season);
            context.setVariable("team", team);
            context.setVariable("standing", teamStanding);

            // Build the Phase Breakdown entries for this team. Visible only when (a) the season has
            // >=2 phases AND (b) the team participated in >=2 of them. Otherwise showPhaseBreakdown
            // is false and the section emits zero bytes (preserves byte-identity of generated HTML).
            boolean showPhaseBreakdown = seasonHasMultiplePhases;
            List<PhaseBreakdownEntry> phaseBreakdown = new ArrayList<>();
            if (showPhaseBreakdown) {
                for (SeasonPhase p : allPhases) {
                    String label = p.getLabel() != null && !p.getLabel().isBlank()
                            ? p.getLabel()
                            : capitalize(p.getPhaseType().name());
                    var phaseStandings = phaseStandingsMap.get(p.getId());
                    int idx = -1;
                    for (int i = 0; i < phaseStandings.size(); i++) {
                        var s = phaseStandings.get(i);
                        if (s.getTeam().getId().equals(team.getId())
                                || (s.getTeam().getParentTeam() != null
                                    && s.getTeam().getParentTeam().getId().equals(team.getId()))) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx >= 0) {
                        var phaseStanding = phaseStandings.get(idx);
                        int rank = idx + 1;
                        String summary = String.format(Locale.ENGLISH, "%d%s place, %d pts",
                                rank, ordinalSuffix(rank), phaseStanding.getPoints());
                        phaseBreakdown.add(new PhaseBreakdownEntry(label, summary));
                    } else if (p.getPhaseType() == PhaseType.PLAYOFF) {
                        // PLAYOFF phase: standings are usually empty (no Match data; bracket-based).
                        // Fall back to PhaseTeam-roster check — PlayoffSeedingService.autoSeedBracket
                        // creates PhaseTeam rows for the seeded teams. If the team participated,
                        // emit a "Top {N}" summary based on the playoff bracket size.
                        // Open Question for Plan 7: derive bracket-result strings ("SF exit",
                        // "F exit", "Champion") from PlayoffMatchup outcomes.
                        var playoffRoster = phaseTeamRepository.findByPhaseId(p.getId());
                        boolean participated = playoffRoster.stream()
                                .anyMatch(pt -> pt.getTeam().getId().equals(team.getId())
                                        || (pt.getTeam().getParentTeam() != null
                                            && pt.getTeam().getParentTeam().getId().equals(team.getId())));
                        if (participated) {
                            String summary = "Top " + playoffRoster.size();
                            phaseBreakdown.add(new PhaseBreakdownEntry(label, summary));
                        }
                    }
                }
                // Edge case: team only participated in one phase even though season has >=2.
                // Fall back to showPhaseBreakdown=false to keep SC4 byte-identity for that team.
                if (phaseBreakdown.size() < 2) {
                    showPhaseBreakdown = false;
                    phaseBreakdown = new ArrayList<>();
                }
            }
            context.setVariable("showPhaseBreakdown", showPhaseBreakdown);
            context.setVariable("phaseBreakdown", phaseBreakdown);

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
                    .map(RaceLineup::getDriver)
                    .distinct()
                    .toList();

            var driversToShow = lineupDrivers.isEmpty()
                    ? allSeasonDrivers.stream()
                            .filter(sd -> sd.getTeam().getId().equals(team.getId()))
                            .map(SeasonDriver::getDriver)
                            .distinct()
                            .toList()
                    : lineupDrivers;

            var driverEntries = driversToShow.stream()
                    .map(driver -> {
                        var driverResults = raceResultRepository.findByDriverId(driver.getId()).stream()
                                .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                                .toList();
                        int totalPoints = driverResults.stream().mapToInt(RaceResult::getPointsTotal).sum();
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
            context.setVariable("pageTitle", team.getName());

            Files.createDirectories(teamDir);
            templateWriter.write("site/team-profile", context, teamDir.resolve(siteSlugger.slugify(team.getShortName()) + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
    }

    private static String ordinalSuffix(int n) {
		if (n >= 11 && n <= 13) {
			return "th";
		}
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static String capitalize(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
        return input.charAt(0) + input.substring(1).toLowerCase(Locale.ENGLISH);
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
            // See config/spotbugs-exclude.xml TeamProfilePageGenerator.copyLogoToAssets NP_NULL entry.
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
