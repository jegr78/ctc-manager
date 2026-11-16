package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.PsnAliasRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.TeamStatsService;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.TeamStats;
import org.ctc.sitegen.model.SiteSlugs;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Writes the alltime career pages {@code driver/<slug>.html} and {@code team/<slug>.html} with the figures behind the
 * public /stats command, one row per season linking to that season's profile.
 */
@Service
@RequiredArgsConstructor
public class CareerProfilePageGenerator {
    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final DriverStatsService driverStats;
    private final TeamStatsService teamStats;
    private final StandingsService standings;
    private final PsnAliasRepository aliases;
    private final TeamRepository teams;

    /** Writes one page per driver ranked over {@code seasons} (chronological) and returns their IDs. */
    public Set<UUID> generateDrivers(Path outPath, List<Season> seasons, SiteSlugs slugs, String activeSeasonSlug, String activeSeasonName,
                                     SiteGeneratorService.GenerationResult result) throws IOException {
        var alltime = driverStats.ranked(StatsPeriod.of(null, seasons, false));
        var bySeason = new HashMap<Season, Map<UUID, DriverStats>>();
        for (var season : seasons) bySeason.put(season, driverStats.ranked(StatsPeriod.of(season, seasons, false)));
        var dir = outPath.resolve("driver");
        Files.createDirectories(dir);
        for (var stats : alltime.values()) {
            String slug = slugs.driver(stats.driverId());
            var rows = new ArrayList<DriverSeasonRow>();
            for (var season : seasons.reversed()) {
                var figures = bySeason.get(season).get(stats.driverId());
                if (figures == null) continue;
                rows.add(new DriverSeasonRow(StatsPeriod.StatsSeason.title(season), seasonUrl(season, "driver", slug),
                        figures.teams().isEmpty() ? null : figures.teams().getLast(), figures.rank(), figures.ranked(), figures.races(),
                        figures.points(), figures.wins(), figures.podiums(), figures.bestPosition()));
            }
            var context = page(stats.psnId(), "drivers");
            context.setVariable("stats", stats);
            context.setVariable("aliases", aliases.findByDriverId(stats.driverId()).stream()
                    .map(org.ctc.domain.model.PsnAlias::getAlias).filter(java.util.Objects::nonNull).toList());
            context.setVariable("seasonRows", rows);
            templateWriter.write("site/driver-career", context, dir.resolve(slug + ".html"), outPath, activeSeasonSlug, activeSeasonName);
            result.incrementPages();
        }
        return alltime.keySet();
    }

    /**
     * Writes one page per team in the alltime standings over {@code seasons} (chronological) and returns their IDs;
     * {@code logo} copies a team's current logo into the assets and returns its root-relative path or null.
     */
    public Set<UUID> generateTeams(Path outPath, List<Season> seasons, SiteSlugs slugs, java.util.function.Function<org.ctc.domain.model.Team, String> logo,
                                   String activeSeasonSlug, String activeSeasonName, SiteGeneratorService.GenerationResult result) throws IOException {
        var period = StatsPeriod.of(null, seasons, false);
        var written = new java.util.LinkedHashSet<UUID>();
        var dir = outPath.resolve("team");
        Files.createDirectories(dir);
        for (var standing : standings.calculateAlltimeStandings(seasons)) {
            UUID teamId = standing.getTeam().getId();
            var found = teamStats.find(teamId, period);
            if (found.isEmpty()) continue;
            TeamStats stats = found.get();
            String slug = slugs.team(teamId);
            var rows = new ArrayList<TeamSeasonRow>();
            for (var season : seasons.reversed()) {
                teamStats.find(teamId, StatsPeriod.of(season, seasons, false)).ifPresent(figures -> rows.add(new TeamSeasonRow(
                        StatsPeriod.StatsSeason.title(season), seasonUrl(season, "team", slug), figures.rank(), figures.ranked(), figures.group(),
                        figures.played(), figures.wins(), figures.draws(), figures.losses(), figures.matchPoints(), figures.playoffResult())));
            }
            var team = teams.findById(teamId).orElseThrow();
            var context = page(team.getShortName(), "teams");
            context.setVariable("stats", stats);
            context.setVariable("teamName", team.getName());
            context.setVariable("teamShortName", team.getShortName());
            context.setVariable("teamLogo", logo.apply(team));
            context.setVariable("seasonRows", rows);
            templateWriter.write("site/team-career", context, dir.resolve(slug + ".html"), outPath, activeSeasonSlug, activeSeasonName);
            result.incrementPages();
            written.add(teamId);
        }
        return written;
    }

    private Context page(String name, String currentPage) {
        var context = new Context(Locale.ENGLISH);
        context.setVariable("pageTitle", name);
        context.setVariable("currentPage", currentPage);
        context.setVariable("seasonSlug", null);
        context.setVariable("seasonName", null);
        context.setVariable("breadcrumbCurrent", name);
        return context;
    }

    private String seasonUrl(Season season, String folder, String slug) {
        return "../season/" + siteSlugger.slugify(season.getDisplayLabel()) + "/" + folder + "/" + slug + ".html";
    }

    public record DriverSeasonRow(String season, String url, String team, int rank, int ranked, int races, int points, int wins,
                                  int podiums, int best) { }

    public record TeamSeasonRow(String season, String url, int rank, int ranked, String group, int played, int wins, int draws,
                                int losses, int matchPoints, String playoffResult) { }
}
