package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.DriverRankingService.DriverRanking;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.PhaseTabView;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/driver-ranking.html} page generation.
 *
 * <p>Phase 62 Plan 3 — phase-aware. Generates the legacy
 * {@code /season/{slug}/driver-ranking.html} (cross-phase aggregated, D-11 SC4-clean — data
 * source unchanged: {@link DriverRankingService#aggregateAcrossPhases}) plus per-phase variants
 * {@code driver-ranking-{phaseSlug}.html} using
 * {@link DriverRankingService#calculateRankingForPhase}.
 *
 * <p>UI-SPEC line 333 explicitly authorizes {@code driver-ranking-playoff.html} when the
 * PLAYOFF phase has driver data — this is intentionally distinct from D-08 which governs
 * {@code standings-playoff.html} (NEVER generated for the bracket). Per-phase driver rankings
 * ARE meaningful for PLAYOFF (per-driver points in semifinals/finals); per-phase team standings
 * are not (the bracket already covers what users want for PLAYOFF teams).
 *
 * <p>The phase-tab row's first tab is always labeled "All Phases" (UI-SPEC line 263) and points
 * at the legacy URL. driver-ranking has NO per-group variants — driver-team relationships
 * span groups, the ranking is per-phase only.
 *
 * <p>Single-REGULAR-LEAGUE seasons render driver-ranking.html with no phase-tab row
 * (showPhaseTabs=false) — SC4 backward-compat invariant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRankingPageGenerator {

    private static final String ARIA_CONTROLS_ID = "main-content";

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final DriverRankingService driverRankingService;
    private final SeasonPhaseService seasonPhaseService;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var allPhases = seasonPhaseService.findAllPhases(season.getId());
        boolean showPhaseTabs = allPhases.size() >= 2;

        String seasonSlug = siteSlugger.slugify(season.getDisplayLabel());
        Path dir = ctx.outPath().resolve("season").resolve(seasonSlug);
        Files.createDirectories(dir);

        // Pre-compute per-phase rankings once. This both decides whether to emit
        // driver-ranking-{phaseSlug}.html for PLAYOFF (skipped when empty) and whether to
        // include the PLAYOFF entry in the tab list.
        List<PhaseWithRanking> perPhaseRankings = new ArrayList<>(allPhases.size());
        for (SeasonPhase p : allPhases) {
            perPhaseRankings.add(new PhaseWithRanking(p, driverRankingService.calculateRankingForPhase(p.getId())));
        }

        // 1) Legacy /season/{slug}/driver-ranking.html — cross-phase aggregated (D-11 SC4-clean).
        //    Data source UNCHANGED from pre-Plan-3 behavior.
        List<UUID> phaseIds = allPhases.stream().map(SeasonPhase::getId).toList();
        var legacyRanking = driverRankingService.aggregateAcrossPhases(phaseIds, season.getId());
        writeRankingVariant(ctx, dir, "driver-ranking.html", legacyRanking,
                showPhaseTabs,
                buildPhaseTabs(perPhaseRankings, /*activePhaseId*/ null, /*isLegacyView*/ true));
        result.incrementPages();

        // 2) Per-phase variants: driver-ranking-{phaseSlug}.html for every phase (REGULAR,
        //    PLAYOFF, PLACEMENT) that has driver data. PLAYOFF with no driver data is skipped
        //    AND its tab is omitted from the row (codified rule).
        for (PhaseWithRanking entry : perPhaseRankings) {
            SeasonPhase p = entry.phase();
            List<DriverRanking> phaseRanking = entry.ranking();
            if (phaseRanking.isEmpty() && p.getPhaseType() == PhaseType.PLAYOFF) {
                // Codified rule: PLAYOFF with no driver data → no driver-ranking-playoff.html
                // and PLAYOFF tab omitted from the row (handled in buildPhaseTabs below).
                continue;
            }
            String phaseSlug = phaseSlug(p);
            writeRankingVariant(ctx, dir, "driver-ranking-" + phaseSlug + ".html", phaseRanking,
                    showPhaseTabs,
                    buildPhaseTabs(perPhaseRankings, p.getId(), /*isLegacyView*/ false));
            result.incrementPages();
        }
    }

    /**
     * Writes a single driver-ranking HTML file.
     *
     * @param filename "driver-ranking.html" (legacy) or "driver-ranking-{phaseSlug}.html"
     * @param ranking the driver ranking rows for this variant
     * @param showPhaseTabs whether the phase-tab row should be emitted (≥2 phases)
     * @param phaseTabs the tab entries (first is always "All Phases" when showPhaseTabs=true)
     */
    private void writeRankingVariant(GenerationContext ctx, Path dir, String filename,
                                     List<DriverRanking> ranking, boolean showPhaseTabs,
                                     List<PhaseTabView> phaseTabs) throws IOException {
        var season = ctx.season();
        var tplCtx = new Context(Locale.ENGLISH);
        tplCtx.setVariable("season", season);
        tplCtx.setVariable("driverRanking", ranking);

        var driverSlugMap = new HashMap<UUID, String>();
        for (var r : ranking) {
            driverSlugMap.put(r.getDriver().getId(),
                    "driver/" + siteSlugger.slugify(r.getDriver().getPsnId()) + ".html");
        }
        tplCtx.setVariable("driverSlugMap", driverSlugMap);

        tplCtx.setVariable("currentPage", "driver-ranking"); // sub-nav stays coarse
        tplCtx.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        tplCtx.setVariable("seasonName", season.getName());
        tplCtx.setVariable("hasPlayoff", ctx.hasPlayoff());
        tplCtx.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        tplCtx.setVariable("breadcrumbCurrent", "Driver Ranking");
        tplCtx.setVariable("pageTitle", "Driver Ranking " + season.getDisplayLabel());
        tplCtx.setVariable("showPhaseTabs", showPhaseTabs);
        tplCtx.setVariable("phaseTabs", phaseTabs);

        templateWriter.write("site/driver-ranking", tplCtx, dir.resolve(filename),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
    }

    /**
     * Builds the phase-tab row entries for any driver-ranking page.
     *
     * <p>The FIRST entry is always "All Phases" (UI-SPEC line 263) pointing at the legacy URL
     * {@code driver-ranking.html}; it is active when no specific phase is active (i.e. on the
     * legacy URL).
     *
     * <p>Subsequent entries are per-phase, ordered by {@link SeasonPhase#getSortIndex()} (the
     * order in which {@code findAllPhases} returns phases). PLAYOFF entries are omitted when
     * the phase has no driver data — see the codified rule in {@link #generate}.
     *
     * @param perPhaseRankings the phases paired with their pre-computed rankings (for the
     *     PLAYOFF skip rule). The list iteration order drives the tab order.
     * @param activePhaseId nullable; when {@code null} the "All Phases" tab is active (legacy
     *     URL); otherwise the matching phase's tab is active.
     * @param isLegacyView true when rendering the legacy {@code driver-ranking.html}; "All
     *     Phases" tab href is always {@code "driver-ranking.html"} regardless.
     */
    private List<PhaseTabView> buildPhaseTabs(List<PhaseWithRanking> perPhaseRankings,
                                              UUID activePhaseId, boolean isLegacyView) {
        var tabs = new ArrayList<PhaseTabView>();
        // First entry: "All Phases" → driver-ranking.html, active when no specific phase is active
        boolean allPhasesActive = (activePhaseId == null);
        tabs.add(new PhaseTabView("All Phases", "driver-ranking.html", allPhasesActive, ARIA_CONTROLS_ID));

        // Per-phase entries
        for (PhaseWithRanking entry : perPhaseRankings) {
            SeasonPhase p = entry.phase();
            // Codified rule (mirrors the per-phase variant skip in generate()): PLAYOFF with no
            // driver data is omitted from the tab row, since the variant file is not generated.
            if (entry.ranking().isEmpty() && p.getPhaseType() == PhaseType.PLAYOFF) {
                continue;
            }
            String label = (p.getLabel() != null && !p.getLabel().isBlank())
                    ? p.getLabel()
                    : capitalize(p.getPhaseType().name());
            String href = "driver-ranking-" + phaseSlug(p) + ".html";
            boolean active = activePhaseId != null && activePhaseId.equals(p.getId());
            tabs.add(new PhaseTabView(label, href, active, ARIA_CONTROLS_ID));
        }
        return tabs;
    }

    /**
     * D-02 phase slug = lowercased PhaseType name (regular / playoff / placement).
     */
    private String phaseSlug(SeasonPhase phase) {
        return phase.getPhaseType().name().toLowerCase(Locale.ENGLISH);
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.charAt(0) + input.substring(1).toLowerCase(Locale.ENGLISH);
    }

    /**
     * Internal carrier pairing a phase with its pre-computed driver ranking. Pre-computing the
     * ranking once avoids invoking {@link DriverRankingService#calculateRankingForPhase} twice
     * (once in {@link #generate} for the variant write, once in {@link #buildPhaseTabs} for the
     * PLAYOFF tab visibility check).
     */
    private record PhaseWithRanking(SeasonPhase phase, List<DriverRanking> ranking) {}
}
