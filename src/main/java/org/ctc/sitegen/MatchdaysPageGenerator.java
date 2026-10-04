package org.ctc.sitegen;

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.ctc.sitegen.model.GroupSubTabView;
import org.ctc.sitegen.model.PhaseTabView;
import org.ctc.sitegen.model.RaceView;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

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

        writeIndexVariant(ctx, dir, "matchdays.html",
                matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regularPhase.getId()),
                allPhases, regularPhase, null,
                true, showPhaseTabs, result);

        for (SeasonPhase phase : allPhases) {
            if (phase.getPhaseType() == PhaseType.PLAYOFF) {
                continue;
            }
            String phaseSlug = phaseSlug(phase);
            String phaseFileBase = "matchdays-" + phaseSlug;

            writeIndexVariant(ctx, dir, phaseFileBase + ".html",
                    matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phase.getId()),
                    allPhases, phase, null,
                    false, showPhaseTabs, result);

            if (phase.getLayout() == PhaseLayout.GROUPS) {
                for (SeasonPhaseGroup group : seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())) {
                    String groupSlug = siteSlugger.slugify(group.getName());
                    String groupFileBase = phaseFileBase + "-group-" + groupSlug;
                    writeIndexVariant(ctx, dir, groupFileBase + ".html",
                            matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phase.getId(), group.getId()),
                            allPhases, phase, group.getId(),
                            false, showPhaseTabs, result);
                }
            }
        }
    }

    private void writeIndexVariant(GenerationContext ctx, Path dir, String filename,
                                    List<Matchday> matchdays,
                                    List<SeasonPhase> allPhases, SeasonPhase currentPhase, UUID currentGroupId,
                                    boolean isLegacyView, boolean showPhaseTabs,
                                    SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        boolean isGroupsLayout = currentPhase.getLayout() == PhaseLayout.GROUPS;

        List<PhaseTabView> phaseTabs = showPhaseTabs
                ? buildPhaseTabs(allPhases, currentPhase.getPhaseType(), isLegacyView)
                : List.of();

        boolean showGroupTabs = isGroupsLayout;
        String perPhaseFileBase = "matchdays-" + phaseSlug(currentPhase);
        String combinedHref = isLegacyView ? "matchdays.html" : perPhaseFileBase + ".html";
        List<GroupSubTabView> groupTabs = showGroupTabs
                ? buildGroupTabs(currentPhase, perPhaseFileBase, combinedHref, currentGroupId)
                : List.of();

        var matchdayLinkMap = new LinkedHashMap<UUID, String>();
        for (var md : matchdays) {
            matchdayLinkMap.put(md.getId(), "matchday/" + siteSlugger.slugify(md.getLabel()) + ".html");
        }

        var tplCtx = new Context(Locale.ENGLISH);
        tplCtx.setVariable("season", season);
        tplCtx.setVariable("matchdays", matchdays);
        tplCtx.setVariable("matchdayLinkMap", matchdayLinkMap);
        tplCtx.setVariable("currentPage", "matchdays");
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
                ctx.outPath(), ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
    }

    private List<PhaseTabView> buildPhaseTabs(List<SeasonPhase> phases, PhaseType currentPhaseType,
                                              boolean isLegacyView) {
        var tabs = new ArrayList<PhaseTabView>();
        for (SeasonPhase p : phases) {
            String label = hasText(p.getLabel())
                    ? p.getLabel()
                    : capitalize(p.getPhaseType().name());
            String href;
            if (p.getPhaseType() == PhaseType.PLAYOFF) {
                href = "playoff.html";
            } else if (isLegacyView && p.getPhaseType() == PhaseType.REGULAR) {
                href = "matchdays.html";
            } else {
                href = "matchdays-" + phaseSlug(p) + ".html";
            }
            boolean active = p.getPhaseType() == currentPhaseType;
            tabs.add(new PhaseTabView(label, href, active, ARIA_CONTROLS_ID));
        }
        return tabs;
    }

    private List<GroupSubTabView> buildGroupTabs(SeasonPhase phase, String phaseFileBase,
                                                 String combinedHref, UUID activeGroupId) {
        var tabs = new ArrayList<GroupSubTabView>();
        boolean combinedActive = activeGroupId == null;
        tabs.add(new GroupSubTabView("Combined", combinedHref, combinedActive, ARIA_CONTROLS_ID));
        for (SeasonPhaseGroup g : seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId())) {
            String groupSlug = siteSlugger.slugify(g.getName());
            String href = phaseFileBase + "-group-" + groupSlug + ".html";
            boolean active = activeGroupId != null && activeGroupId.equals(g.getId());
            tabs.add(new GroupSubTabView(g.getName(), href, active, ARIA_CONTROLS_ID));
        }
        return tabs;
    }

    private String phaseSlug(SeasonPhase phase) {
        return phase.getPhaseType().name().toLowerCase(Locale.ENGLISH);
    }

    private String capitalize(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
        return input.charAt(0) + input.substring(1).toLowerCase(Locale.ENGLISH);
    }

    public void generateDetails(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        var allLineups = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId());

        for (var matchday : matchdays) {
            var context = new Context(Locale.ENGLISH);
            context.setVariable("season", season);
            context.setVariable("matchday", matchday);
            context.setVariable("matchdaysIndexHref", matchdaysIndexHref(matchday));
            var raceViews = raceRepository.findByMatchdayId(matchday.getId()).stream()
                    .map(r -> toRaceView(r, season, "../driver/", allLineups, ctx.slugs())).toList();
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
                    ctx.outPath(), ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
    }

    private String matchdaysIndexHref(Matchday matchday) {
        var phase = matchday.getPhase();
        if (phase.getPhaseType() == PhaseType.PLAYOFF) {
            return "../playoff.html";
        }
        String file = "../matchdays-" + phaseSlug(phase);
        if (phase.getLayout() == PhaseLayout.GROUPS && matchday.getGroup() != null) {
            file += "-group-" + siteSlugger.slugify(matchday.getGroup().getName());
        }
        return file + ".html";
    }

    private RaceView toRaceView(Race race, Season season, String driverUrlPrefix,
                                List<RaceLineup> seasonLineups, SiteSlugs slugs) {
        var homeTeam = race.getHomeTeam();
        String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";

        var results = race.getResults().stream()
                .map(r -> {

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
                    String driverSlug = slugs.driver(r.getDriver().getId());
                    String driverProfileUrl = driverUrlPrefix + driverSlug + ".html";
                    return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName, scoringTeamShortName,
                            r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal(),
                            driverProfileUrl);
                })
                .toList();

        String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

        int homeTotal = pointsForTeam(results, homeTeam);
        int awayTotal = pointsForTeam(results, race.getAwayTeam());

        String trackName = race.getTrack() != null ? race.getTrack().getName() : null;
        String carName = race.getCar() != null ? race.getCar().getDisplayName() : null;

        boolean hasResults = !race.getResults().isEmpty();
        boolean homeTeamWon = hasResults && homeTotal > awayTotal;
        boolean awayTeamWon = hasResults && awayTotal > homeTotal;
        return new RaceView(homeShortName, awayShortName,
                trackName, carName, homeTotal, awayTotal, hasResults,
                homeTeamWon, awayTeamWon, results);
    }
    private int pointsForTeam(List<RaceView.ResultView> results, Team team) {
        if (team == null) {
            return 0;
        }
        return results.stream()
                .filter(r -> (team.isSubTeam() ? r.teamShortName() : r.scoringTeamShortName())
                        .equals(team.getShortName()))
                .mapToInt(RaceView.ResultView::pointsTotal).sum();
    }

}
