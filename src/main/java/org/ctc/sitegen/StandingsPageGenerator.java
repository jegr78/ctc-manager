package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/standings.html} page generation.
 *
 * <p>Phase-62 Plan-0 extraction: body lifted verbatim from
 * {@code SiteGeneratorService.generateStandings} (lines 186-211 pre-extraction). Behavior is
 * byte-identical to the legacy private method (SC4 invariant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsPageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final StandingsService standingsService;
    private final SeasonPhaseService seasonPhaseService;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var context = new Context(Locale.ENGLISH);
        var season = ctx.season();
        context.setVariable("season", season);
        // Phase-aware standings via REGULAR phase (templates stay LEAGUE-shaped).
        var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
        var standings = standingsService.calculateStandings(regularPhase.getId(), null);
        var teamSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var s : standings) {
            teamSlugMap.put(s.getTeam().getId(), "team/" + siteSlugger.slugify(s.getTeam().getShortName()) + ".html");
        }
        context.setVariable("standings", standings);
        context.setVariable("teamSlugMap", teamSlugMap);

        context.setVariable("currentPage", "standings");
        context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        context.setVariable("seasonName", season.getName());
        context.setVariable("hasPlayoff", ctx.hasPlayoff());
        context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        context.setVariable("breadcrumbCurrent", "Standings");

        var dir = ctx.outPath().resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        templateWriter.write("site/standings", context, dir.resolve("standings.html"),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
    }
}
