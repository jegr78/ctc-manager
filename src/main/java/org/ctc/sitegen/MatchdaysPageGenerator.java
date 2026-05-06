package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.RaceView;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/matchday.html} (per-matchday detail) and
 * {@code site/matchdays.html} (index list) generation.
 *
 * <p>Phase-62 Plan-0 extraction (Open Question 4): one class with two public entry methods —
 * {@link #generateIndex(GenerationContext, SiteGeneratorService.GenerationResult)} writes the
 * list page (legacy {@code generateMatchdayIndex}, lines 653-678 pre-extraction);
 * {@link #generateDetails(GenerationContext, SiteGeneratorService.GenerationResult)} writes
 * per-matchday detail pages (legacy {@code generateMatchdays}, lines 241-267 pre-extraction).
 * The private {@code toRaceView} helper (lines 770-821 pre-extraction) moves with this class
 * because only the matchday-detail flow calls it. Behavior is byte-identical to the legacy
 * private methods (SC4 invariant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdaysPageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final MatchdayRepository matchdayRepository;
    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;

    public void generateIndex(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        var context = new Context(Locale.ENGLISH);
        context.setVariable("season", season);
        context.setVariable("matchdays", matchdays);

        // Pre-compute relative links from season/{slug}/ level
        var matchdayLinkMap = new java.util.LinkedHashMap<java.util.UUID, String>();
        for (var md : matchdays) {
            matchdayLinkMap.put(md.getId(), "matchday/" + siteSlugger.slugify(md.getLabel()) + ".html");
        }
        context.setVariable("matchdayLinkMap", matchdayLinkMap);
        context.setVariable("currentPage", "matchdays");
        context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        context.setVariable("seasonName", season.getName());
        context.setVariable("hasPlayoff", ctx.hasPlayoff());
        context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        context.setVariable("breadcrumbCurrent", "Matchdays");

        var dir = ctx.outPath().resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        templateWriter.write("site/matchdays", context, dir.resolve("matchdays.html"),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
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
