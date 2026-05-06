package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/driver-ranking.html} page generation.
 *
 * <p>Phase-62 Plan-0 extraction: body lifted verbatim from
 * {@code SiteGeneratorService.generateDriverRanking} (lines 213-239 pre-extraction). Behavior
 * is byte-identical to the legacy private method (SC4 invariant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRankingPageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final DriverRankingService driverRankingService;
    private final SeasonPhaseService seasonPhaseService;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var context = new Context(Locale.ENGLISH);
        var season = ctx.season();
        context.setVariable("season", season);
        // Aggregate ranking across ALL phases of the season (REGULAR + PLAYOFF + PLACEMENT).
        var phaseIds = seasonPhaseService.findAllPhases(season.getId()).stream()
                .map(SeasonPhase::getId).toList();
        var driverRanking = driverRankingService.aggregateAcrossPhases(phaseIds, season.getId());
        var driverSlugMap = new java.util.HashMap<java.util.UUID, String>();
        for (var r : driverRanking) {
            driverSlugMap.put(r.getDriver().getId(), "driver/" + siteSlugger.slugify(r.getDriver().getPsnId()) + ".html");
        }
        context.setVariable("driverRanking", driverRanking);
        context.setVariable("driverSlugMap", driverSlugMap);

        context.setVariable("currentPage", "driver-ranking");
        context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
        context.setVariable("seasonName", season.getName());
        context.setVariable("hasPlayoff", ctx.hasPlayoff());
        context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
        context.setVariable("breadcrumbCurrent", "Driver Ranking");

        var dir = ctx.outPath().resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel()));
        Files.createDirectories(dir);
        templateWriter.write("site/driver-ranking", context, dir.resolve("driver-ranking.html"),
                ctx.activeSeasonSlug(), ctx.activeSeasonName());
        result.incrementPages();
    }
}
