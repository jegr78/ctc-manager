package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * Helper bean for {@code site/driver-profile.html} page generation.
 *
 * <p>Phase-62 Plan-0 extraction: body lifted verbatim from
 * {@code SiteGeneratorService.generateDriverProfiles} (lines 348-385 pre-extraction).
 * Behavior is byte-identical to the legacy private method (SC4 invariant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverProfilePageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceResultRepository raceResultRepository;

    public void generate(GenerationContext ctx, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = ctx.season();
        var outPath = ctx.outPath();
        var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
        var generatedDriverIds = new java.util.HashSet<java.util.UUID>();

        for (var sd : seasonDrivers) {
            var driver = sd.getDriver();
            if (!generatedDriverIds.add(driver.getId())) continue;
            var team = sd.getTeam();
            var results = raceResultRepository.findByDriverId(driver.getId()).stream()
                    .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
                    .toList();

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

            var dir = outPath.resolve("season").resolve(siteSlugger.slugify(season.getDisplayLabel())).resolve("driver");
            Files.createDirectories(dir);
            templateWriter.write("site/driver-profile", context, dir.resolve(siteSlugger.slugify(driver.getPsnId()) + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
    }
}
