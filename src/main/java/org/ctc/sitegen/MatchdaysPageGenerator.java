package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.GroupSubTabView;
import org.ctc.sitegen.model.PhaseTabView;
import org.ctc.sitegen.model.RaceView;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/matchday.html} (per-matchday detail) and
 * {@code site/matchdays.html} (index list) generation.
 *
 * <p>Phase- and group-aware index: generates {@code matchdays.html} (REGULAR-only) plus per-phase
 * variants {@code matchdays-{phaseSlug}.html} (PLAYOFF skipped) plus per-group variants for
 * GROUPS-layout phases. Detail pages are phase-agnostic. Single-phase seasons render with no tabs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdaysPageGenerator {

    private static final String ARIA_CONTROLS_ID = "main-content";

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final MatchdayRepository matchdayRepository;
    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonPhaseService seasonPhaseService;
    private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;

    public void generateIndex(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
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

        // 1) Legacy /season/{slug}/matchdays.html — REGULAR-only (Open Question 2 locked)
        writeIndexVariant(ctx, dir, "matchdays.html",
                matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regularPhase.getId()),
                allPhases, regularPhase, /* groupId */ null,
                /* isLegacyView */ true, showPhaseTabs, result);

        // 2) Per-phase variants — skip PLAYOFF (PLAYOFF tab links to playoff.html)
        for (SeasonPhase phase : allPhases) {
            if (phase.getPhaseType() == PhaseType.PLAYOFF) {
                continue; // never generate matchdays-playoff.html
            }
            String phaseSlug = phaseSlug(phase);
            String phaseFileBase = "matchdays-" + phaseSlug;

            // 2a) Per-phase combined (or LEAGUE) page
            writeIndexVariant(ctx, dir, phaseFileBase + ".html",
                    matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phase.getId()),
                    allPhases, phase, /* groupId */ null,
                    /* isLegacyView */ false, showPhaseTabs, result);

            // 2b) Per-group variants for GROUPS-layout phases
            if (phase.getLayout() == PhaseLayout.GROUPS) {
                for (SeasonPhaseGroup group : seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())) {
                    String groupSlug = siteSlugger.slugify(group.getName());
                    String groupFileBase = phaseFileBase + "-group-" + groupSlug;
                    writeIndexVariant(ctx, dir, groupFileBase + ".html",
                            matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phase.getId(), group.getId()),
                            allPhases, phase, group.getId(),
                            /* isLegacyView */ false, showPhaseTabs, result);
                }
            }
        }
    }

    /**
     * Writes a single matchdays-index HTML file for the given phase (and optional group).
     *
     * @param filename the output filename ("matchdays.html", "matchdays-regular.html",
     *     "matchdays-regular-group-group-a.html", etc.)
     * @param matchdays the matchday rows to render in the index table
     * @param currentPhase the phase whose page is currently being rendered (drives tab actives)
     * @param currentGroupId nullable; non-null on per-group view (drives group-tab actives)
     * @param isLegacyView true for the legacy {@code matchdays.html}; drives REGULAR-tab href
     *     to "matchdays.html" (instead of "matchdays-regular.html") and Combined-tab href to
     *     "matchdays.html"
     */
    private void writeIndexVariant(GenerationContext ctx, Path dir, String filename,
                                    List<Matchday> matchdays,
                                    List<SeasonPhase> allPhases, SeasonPhase currentPhase, UUID currentGroupId,
                                    boolean isLegacyView, boolean showPhaseTabs,
                                    SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        boolean isGroupsLayout = currentPhase.getLayout() == PhaseLayout.GROUPS;

        // Phase tab row (visible when ≥2 phases)
        List<PhaseTabView> phaseTabs = showPhaseTabs
                ? buildPhaseTabs(allPhases, currentPhase.getPhaseType(), isLegacyView)
                : List.of();

        // Group sub-tab row (visible when current phase is GROUPS-layout).
        // phaseFileBase is ALWAYS per-phase (group sub-tab files only exist as
        // matchdays-{phaseSlug}-group-{groupSlug}.html — there is no legacy group variant).
        // combinedHref is the legacy URL on the combined-REGULAR view, per-phase URL otherwise.
        boolean showGroupTabs = isGroupsLayout;
        String perPhaseFileBase = "matchdays-" + phaseSlug(currentPhase);
        String combinedHref = isLegacyView ? "matchdays.html" : perPhaseFileBase + ".html";
        List<GroupSubTabView> groupTabs = showGroupTabs
                ? buildGroupTabs(currentPhase, perPhaseFileBase, combinedHref, currentGroupId)
                : List.of();

        // Pre-compute relative links from season/{slug}/ level (matchday detail pages stay
        // phase-agnostic per plan — their slugs are unique per season already)
        var matchdayLinkMap = new LinkedHashMap<UUID, String>();
        for (var md : matchdays) {
            matchdayLinkMap.put(md.getId(), "matchday/" + siteSlugger.slugify(md.getLabel()) + ".html");
        }

        var tplCtx = new Context(Locale.ENGLISH);
        tplCtx.setVariable("season", season);
        tplCtx.setVariable("matchdays", matchdays);
        tplCtx.setVariable("matchdayLinkMap", matchdayLinkMap);
        tplCtx.setVariable("currentPage", "matchdays"); // sub-nav stays coarse
        tplCtx.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        tplCtx.setVariable("seasonName", season.getName());
        tplCtx.setVariable("hasPlayoff", ctx.hasPlayoff());
        tplCtx.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        tplCtx.setVariable("breadcrumbCurrent", "Matchdays");
        tplCtx.setVariable("pageTitle", "Matchdays — " + season.getDisplayLabel());
        tplCtx.setVariable("showPhaseTabs", showPhaseTabs);
        tplCtx.setVariable("phaseTabs", phaseTabs);
        tplCtx.setVariable("showGroupTabs", showGroupTabs);
        tplCtx.setVariable("groupTabs", groupTabs);

        templateWriter.write("site/matchdays", tplCtx, dir.resolve(filename),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
    }

    /**
     * Builds the phase-tab row entries for any matchdays-index page.
     *
     * @param currentPhaseType the phase type whose page is currently being rendered (for active flag)
     * @param isLegacyView true when rendering the legacy {@code matchdays.html}; drives REGULAR
     *     tab href to "matchdays.html" instead of "matchdays-regular.html"
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
                href = "matchdays.html"; // legacy URL is the REGULAR canonical
            } else {
                href = "matchdays-" + phaseSlug(p) + ".html";
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
     * @param phaseFileBase always per-phase ({@code "matchdays-{phaseSlug}"}) — group sub-tab files
     *     only exist as {@code matchdays-{phaseSlug}-group-{groupSlug}.html}; there is no legacy
     *     group variant
     * @param combinedHref the "Combined" tab href: {@code matchdays.html} on the legacy view,
     *     {@code matchdays-{phaseSlug}.html} on the per-phase view
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

    public void generateDetails(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
        // Pre-fetch all lineups for the season to avoid per-result repository queries in toRaceView
        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());

        for (var matchday : matchdays) {
            var context = new Context(Locale.ENGLISH);
            context.setVariable("season", season);
            context.setVariable("matchday", matchday);
            var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
                    .map(r -> toRaceView(r, season, "../driver/", allLineups)).toList();
            context.setVariable("races", raceViews);

            context.setVariable("currentPage", "matchdays");
            context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
            context.setVariable("seasonName", season.getName());
            context.setVariable("hasPlayoff", ctx.hasPlayoff());
            context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
            context.setVariable("breadcrumbCurrent", matchday.getLabel());
            context.setVariable("pageTitle", matchday.getLabel());

            var dir = ctx.outPath().resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel())).resolve("matchday");
            Files.createDirectories(dir);
            templateWriter.write("site/matchday", context, dir.resolve(siteSlugger.slugify(matchday.getLabel()) + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
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
                    String driverSlug = siteSlugger.slugify(r.getDriver().getPsnId());
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
}
