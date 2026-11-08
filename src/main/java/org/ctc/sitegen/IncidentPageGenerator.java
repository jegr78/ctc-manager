package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.incidents.PublicIncidentCase;
import org.ctc.domain.service.IncidentPublicReadService;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class IncidentPageGenerator {
    private final IncidentPublicReadService incidents;
    private final SiteSlugger slugger;
    private final TemplateWriter writer;

    public void generate(GenerationContext generation, SiteGeneratorService.GenerationResult result) throws IOException {
        var season = generation.season();
        var cases = incidents.forSeason(season.getId());
        var days = new LinkedHashMap<UUID, List<PublicIncidentCase>>();
        cases.stream().sorted(Comparator.comparingInt(PublicIncidentCase::matchdayOrder).thenComparing(PublicIncidentCase::reference))
                .forEach(item -> days.computeIfAbsent(item.matchdayId(), id -> new java.util.ArrayList<>()).add(item));
        var groups = days.entrySet().stream().map(entry -> {
            var first = entry.getValue().getFirst();
            return new MatchdayCases("matchday-" + entry.getKey(), first.matchdayLabel(),
                    "matchday/" + slugger.slugify(first.matchdayLabel()) + ".html", entry.getValue());
        }).toList();
        var context = new Context(Locale.ENGLISH);
        context.setVariable("season", season);
        context.setVariable("incidentGroups", groups);
        context.setVariable("caseCount", cases.size());
        context.setVariable("currentPage", "incidents");
        context.setVariable("seasonSlug", slugger.slugify(season.getDisplayLabel()));
        context.setVariable("seasonName", season.getName());
        context.setVariable("hasPlayoff", generation.hasPlayoff());
        context.setVariable("playoffSeasonSlug", generation.playoffSeasonSlug());
        context.setVariable("breadcrumbCurrent", "Incidents & rulings");
        context.setVariable("pageTitle", "Incidents & rulings — " + season.getDisplayLabel());
        var directory = generation.outPath().resolve("season").resolve(slugger.slugify(season.getDisplayLabel()));
        Files.createDirectories(directory);
        writer.write("site/incidents", context, directory.resolve("incidents.html"), generation.outPath(),
                generation.activeSeasonSlug(), generation.activeSeasonName());
        result.incrementPages();
    }

    public record MatchdayCases(String anchor, String label, String href, List<PublicIncidentCase> cases) {
        public MatchdayCases { cases = List.copyOf(cases); }
    }
}
