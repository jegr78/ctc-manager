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
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.GroupSubTabView;
import org.ctc.sitegen.model.PhaseTabView;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/standings.html} page generation.
 *
 * <p>Phase- and group-aware: generates {@code standings.html} (REGULAR-combined) plus per-phase
 * variants {@code standings-{phaseSlug}.html} (PLAYOFF skipped) plus per-group variants
 * {@code standings-{phaseSlug}-group-{groupSlug}.html} for GROUPS-layout phases.
 * Single-phase seasons render with no tab rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsPageGenerator {

    private static final String ARIA_CONTROLS_ID = "main-content";

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final StandingsService standingsService;
    private final SeasonPhaseService seasonPhaseService;
    private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;
    private final PhaseTeamRepository phaseTeamRepository;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var allPhases = seasonPhaseService.findAllPhases(season.getId());
        var regularPhase = allPhases.stream()
                .filter(p -> p.getPhaseType() == PhaseType.REGULAR)
                .findFirst()
                .orElseThrow();

        boolean showPhaseTabs = allPhases.size() >= 2;
        String seasonSlug = siteSlugger.slugify(season.getDisplayLabel());
        Path dir = ctx.outPath().resolve("season").resolve(seasonSlug);
        Files.createDirectories(dir);

        // 1) Legacy /season/{slug}/standings.html — REGULAR combined view
        writeStandingsFile(ctx, allPhases, regularPhase, /* groupId */ null,
                /* fileBaseName */ "standings", /* isLegacyView */ true,
                showPhaseTabs, dir, result);

        // 2) Per-phase variants — REGULAR (and PLACEMENT, when present); skip PLAYOFF
        for (SeasonPhase phase : allPhases) {
            if (phase.getPhaseType() == PhaseType.PLAYOFF) {
                continue; // PLAYOFF tab links to playoff.html; no standings-playoff.html
            }
            String phaseSlug = phaseSlug(phase);
            String phaseFileBase = "standings-" + phaseSlug;

            // 2a) Per-phase combined (or LEAGUE) page: standings-{phaseSlug}.html
            writeStandingsFile(ctx, allPhases, phase, /* groupId */ null,
                    phaseFileBase, /* isLegacyView */ false,
                    showPhaseTabs, dir, result);

            // 2b) Per-group variants for GROUPS-layout phases
            if (phase.getLayout() == PhaseLayout.GROUPS) {
                for (SeasonPhaseGroup group : seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())) {
                    String groupSlug = siteSlugger.slugify(group.getName());
                    String groupFileBase = phaseFileBase + "-group-" + groupSlug;
                    writeStandingsFile(ctx, allPhases, phase, group.getId(),
                            groupFileBase, /* isLegacyView */ false,
                            showPhaseTabs, dir, result);
                }
            }
        }
    }

    /**
     * Writes a single standings HTML file for the given phase (and optional group).
     *
     * @param phase the phase whose standings to render
     * @param groupId nullable; when non-null the page is the per-group view (no Group column,
     *     Buchholz column rendered when format=Swiss)
     * @param fileBaseName file name without ".html" suffix (e.g. "standings",
     *     "standings-regular", "standings-regular-group-group-a")
     * @param isLegacyView true for the legacy {@code standings.html} (combined REGULAR view);
     *     drives the legacy URL pattern in the phase-tab href computation
     */
    private void writeStandingsFile(GenerationContext ctx, List<SeasonPhase> allPhases,
                                    SeasonPhase phase, UUID groupId, String fileBaseName,
                                    boolean isLegacyView, boolean showPhaseTabs,
                                    Path dir, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var seasonSlug = siteSlugger.slugify(season.getDisplayLabel());
        boolean isGroupsLayout = phase.getLayout() == PhaseLayout.GROUPS;
        boolean isCombinedView = (groupId == null);
        boolean isSwissPerGroup = !isCombinedView && phase.getFormat() == SeasonFormat.SWISS;

        // Calculate standings; choose Buchholz variant for per-group + Swiss
        List<StandingsService.TeamStanding> standings = isSwissPerGroup
                ? standingsService.calculateStandingsWithBuchholz(phase.getId(), groupId)
                : standingsService.calculateStandings(phase.getId(), groupId);

        // empty-state: build 0-point roster from PhaseTeam rows when standings list is empty
        boolean emptyState = standings.isEmpty();
        if (emptyState) {
            var roster = (groupId != null)
                    ? phaseTeamRepository.findByPhaseIdAndGroupId(phase.getId(), groupId)
                    : phaseTeamRepository.findByPhaseId(phase.getId());
            standings = roster.stream()
                    .map(pt -> {
                        var ts = new StandingsService.TeamStanding(pt.getTeam());
                        ts.setGroup(pt.getGroup());
                        return ts;
                    })
                    .toList();
        }

        // Build teamSlugMap (relative href to team profile within the season directory)
        var teamSlugMap = new HashMap<UUID, String>();
        for (var s : standings) {
            teamSlugMap.put(s.getTeam().getId(),
                    "team/" + siteSlugger.slugify(s.getTeam().getShortName()) + ".html");
        }

        // Phase tab row (visible when ≥2 phases)
        List<PhaseTabView> phaseTabs = showPhaseTabs
                ? buildPhaseTabs(allPhases, phase.getPhaseType(), isLegacyView)
                : List.of();

        // Group sub-tab row (visible when current phase is GROUPS-layout).
        // phaseFileBase is ALWAYS per-phase (group sub-tab files only exist as
        // standings-{phaseSlug}-group-{groupSlug}.html — there is no legacy group variant).
        // combinedHref is the legacy URL on the combined-REGULAR view, per-phase URL otherwise.
        boolean showGroupTabs = isGroupsLayout;
        String perPhaseFileBase = "standings-" + phaseSlug(phase);
        String combinedHref = isLegacyView ? "standings.html" : perPhaseFileBase + ".html";
        List<GroupSubTabView> groupTabs = showGroupTabs
                ? buildGroupTabs(phase, perPhaseFileBase, combinedHref, groupId)
                : List.of();

        boolean showGroupColumn = isGroupsLayout && isCombinedView;
        boolean showBuchholz = isSwissPerGroup;

        // Build Thymeleaf context
        var tplCtx = new Context(Locale.ENGLISH);
        tplCtx.setVariable("season", season);
        tplCtx.setVariable("standings", standings);
        tplCtx.setVariable("teamSlugMap", teamSlugMap);
        tplCtx.setVariable("currentPage", "standings"); // sub-nav stays coarse
        tplCtx.setVariable("seasonSlug", seasonSlug);
        tplCtx.setVariable("seasonName", season.getName());
        tplCtx.setVariable("hasPlayoff", ctx.hasPlayoff());
        tplCtx.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        tplCtx.setVariable("breadcrumbCurrent", "Standings");
        tplCtx.setVariable("pageTitle", "Standings " + season.getDisplayLabel());
        tplCtx.setVariable("showPhaseTabs", showPhaseTabs);
        tplCtx.setVariable("phaseTabs", phaseTabs);
        tplCtx.setVariable("showGroupTabs", showGroupTabs);
        tplCtx.setVariable("groupTabs", groupTabs);
        tplCtx.setVariable("showGroupColumn", showGroupColumn);
        tplCtx.setVariable("showBuchholz", showBuchholz);
        tplCtx.setVariable("emptyState", emptyState);
        tplCtx.setVariable("emptyStateHeading", "No results recorded yet.");
        tplCtx.setVariable("emptyStateBody", "Standings will appear once race results are recorded.");

        templateWriter.write("site/standings", tplCtx, dir.resolve(fileBaseName + ".html"),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
    }

    /**
     * Builds the phase-tab row entries for any standings page.
     *
     * @param currentPhaseType the phase type whose page is currently being rendered (for active flag)
     * @param isLegacyView true when rendering the legacy {@code standings.html} (REGULAR combined);
     *     drives REGULAR-tab href to "standings.html" instead of "standings-regular.html"
     */
    private List<PhaseTabView> buildPhaseTabs(List<SeasonPhase> phases, PhaseType currentPhaseType,
                                              boolean isLegacyView) {
        var tabs = new ArrayList<PhaseTabView>();
        for (SeasonPhase p : phases) {
            String label = (p.getLabel() != null && !p.getLabel().isBlank())
                    ? p.getLabel()
                    : capitalize(p.getPhaseType().name());
            String href;
            if (p.getPhaseType() == PhaseType.PLAYOFF) {
                href = "playoff.html";
            } else if (isLegacyView && p.getPhaseType() == PhaseType.REGULAR) {
                href = "standings.html"; // legacy URL is the REGULAR canonical
            } else {
                href = "standings-" + phaseSlug(p) + ".html";
            }
            boolean active = (p.getPhaseType() == currentPhaseType);
            tabs.add(new PhaseTabView(label, href, active, ARIA_CONTROLS_ID));
        }
        return tabs;
    }

    /**
     * Builds the group sub-tab row entries (Combined first, then one per group in sortIndex order).
     *
     * @param phase the GROUPS-layout phase whose sub-tabs are being rendered
     * @param phaseFileBase always per-phase ({@code "standings-{phaseSlug}"}) — group sub-tab files
     *     only exist as {@code standings-{phaseSlug}-group-{groupSlug}.html}; there is no legacy
     *     group variant
     * @param combinedHref the "Combined" tab href: {@code standings.html} on the legacy view,
     *     {@code standings-{phaseSlug}.html} on the per-phase view
     * @param activeGroupId nullable; null on combined view, set on per-group view
     */
    private List<GroupSubTabView> buildGroupTabs(SeasonPhase phase, String phaseFileBase,
                                                 String combinedHref, UUID activeGroupId) {
        var tabs = new ArrayList<GroupSubTabView>();
        boolean combinedActive = (activeGroupId == null);
        tabs.add(new GroupSubTabView("Combined", combinedHref, combinedActive, ARIA_CONTROLS_ID));
        for (SeasonPhaseGroup g : seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())) {
            String groupSlug = siteSlugger.slugify(g.getName());
            String href = phaseFileBase + "-group-" + groupSlug + ".html";
            boolean active = (activeGroupId != null && activeGroupId.equals(g.getId()));
            tabs.add(new GroupSubTabView(g.getName(), href, active, ARIA_CONTROLS_ID));
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
}
