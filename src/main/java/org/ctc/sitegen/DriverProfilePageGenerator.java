package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/driver-profile.html} page generation.
 *
 * <p>Phase-62 Plan-0 extraction: body lifted verbatim from
 * {@code SiteGeneratorService.generateDriverProfiles} (lines 348-385 pre-extraction).
 *
 * <p>Phase-62 Plan-4: adds D-15 per-phase results sectioning. When the season has &ge;2 phases
 * AND the driver has results in &ge;2 of them, the {@code resultsByPhase} LinkedHashMap drives a
 * per-phase {@code <h3 class="section-title">} heading per UI-SPEC copy ("Regular Season Results"
 * / "Playoff Results" / "Placement Phase Results"). When the season has only one phase, the
 * page renders byte-identical to today (SC4 invariant).
 *
 * <p>Single profile URL preserved per (season, driver) — no per-phase URL forks (D-16).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverProfilePageGenerator {

    private static final List<PhaseType> PHASE_ORDER =
            List.of(PhaseType.REGULAR, PhaseType.PLAYOFF, PhaseType.PLACEMENT);

    private static final Map<PhaseType, String> PHASE_HEADINGS = Map.of(
            PhaseType.REGULAR, "Regular Season Results",
            PhaseType.PLAYOFF, "Playoff Results",
            PhaseType.PLACEMENT, "Placement Phase Results");

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonPhaseService seasonPhaseService;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var outPath = ctx.outPath();
        var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
        var generatedDriverIds = new java.util.HashSet<java.util.UUID>();

        // showPhaseBreakdown is gated by season.phases.size() >= 2 (server-side flag).
        boolean seasonHasMultiplePhases =
                seasonPhaseService.findAllPhases(season.getId()).size() >= 2;

        for (var sd : seasonDrivers) {
            var driver = sd.getDriver();
            if (!generatedDriverIds.add(driver.getId())) continue;
            var team = sd.getTeam();
            var results = raceResultRepository.findByDriverId(driver.getId()).stream()
                    .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                    .toList();

            // Split results into a LinkedHashMap (REGULAR -> PLAYOFF -> PLACEMENT canonical order),
            // filtered in Java on race.matchday.phase.phaseType (no dedicated repository method).
            //
            // Phase participation is detected via RaceLineup, NOT RaceResult: TestDataService creates
            // PLAYOFF Race+RaceLineup but no RaceResult rows yet, so a RaceResult-only check would
            // miss PLAYOFF participation. Lineups are the single source of truth for participation
            // (per CLAUDE.md "RaceLineup is Source of Truth").
            boolean showPhaseBreakdown = seasonHasMultiplePhases;
            LinkedHashMap<PhaseType, List<RaceResult>> resultsByPhase = new LinkedHashMap<>();
            if (showPhaseBreakdown) {
                Set<PhaseType> participatedPhases = raceLineupRepository
                        .findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), season.getId()).stream()
                        .map(rl -> rl.getRace().getMatchday().getPhase().getPhaseType())
                        .collect(Collectors.toCollection(java.util.HashSet::new));
                for (PhaseType pt : PHASE_ORDER) {
                    if (!participatedPhases.contains(pt)) continue;
                    var phaseResults = results.stream()
                            .filter(r -> r.getRace().getMatchday().getPhase().getPhaseType() == pt)
                            .toList();
                    // Always include the phase entry when the driver participated, even when the
                    // RaceResult list is empty (PLAYOFF results aren't seeded in test fixtures).
                    resultsByPhase.put(pt, phaseResults);
                }
                // Edge case: driver only participated in one phase even though season has >=2.
                // Fall back to showPhaseBreakdown=false to keep SC4 byte-identity for that driver.
                if (resultsByPhase.size() < 2) {
                    showPhaseBreakdown = false;
                    resultsByPhase = new LinkedHashMap<>();
                }
            }

            var context = new Context(Locale.ENGLISH);
            context.setVariable("season", season);
            context.setVariable("driver", driver);
            context.setVariable("team", team);
            context.setVariable("results", results);
            int total = results.stream().mapToInt(r -> r.getPointsTotal()).sum();
            context.setVariable("totalRaces", results.size());
            context.setVariable("totalPoints", total);
            context.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) total / results.size());
            context.setVariable("bestPosition", results.isEmpty() ? null :
                    results.stream().mapToInt(r -> r.getPosition()).min().orElse(0));

            context.setVariable("currentPage", "driver");
            context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
            context.setVariable("seasonName", season.getName());
            context.setVariable("hasPlayoff", ctx.hasPlayoff());
            context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
            context.setVariable("breadcrumbCurrent", driver.getPsnId());
            context.setVariable("showPhaseBreakdown", showPhaseBreakdown);
            context.setVariable("resultsByPhase", resultsByPhase);
            context.setVariable("phaseHeadings", PHASE_HEADINGS);

            var dir = outPath.resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel())).resolve("driver");
            Files.createDirectories(dir);
            templateWriter.write("site/driver-profile", context, dir.resolve(siteSlugger.slugify(driver.getPsnId()) + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
    }
}
