package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.sitegen.model.GenerationContext;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/** Writes the page that a formerly shared profile URL shows: links to every profile of that season. */
@Component
@RequiredArgsConstructor
public class SharedProfilePageGenerator {

    private final TemplateWriter templateWriter;
    private final SiteSlugger siteSlugger;

    /**
     * Writes one page per shared slug of {@code kind} into {@code dir} when at least one of its
     * profiles was generated for this season; {@code generatedNames} maps those profiles to names.
     */
    public void generate(GenerationContext ctx, SiteSlugKind kind, Path dir, Map<UUID, String> generatedNames,
                         SiteGeneratorService.GenerationResult result) throws IOException {
        for (var shared : ctx.slugs().shared(kind)) {
            List<ProfileLink> profiles = shared.memberIds().stream()
                    .filter(generatedNames::containsKey)
                    .map(id -> new ProfileLink(generatedNames.get(id), slugOf(ctx, kind, id) + ".html"))
                    .sorted(Comparator.comparing(ProfileLink::name))
                    .toList();
            if (profiles.isEmpty()) {
                continue;
            }
            var context = new Context(Locale.ENGLISH);
            context.setVariable("profiles", profiles);
            context.setVariable("pageTitle", kind == SiteSlugKind.TEAM ? "Teams" : "Drivers");
            context.setVariable("currentPage", kind == SiteSlugKind.TEAM ? "team" : "driver");
            context.setVariable("seasonSlug", siteSlugger.slugify(ctx.season().getDisplayLabel()));
            context.setVariable("seasonName", ctx.season().getName());
            context.setVariable("hasPlayoff", ctx.hasPlayoff());
            context.setVariable("playoffSeasonSlug", ctx.playoffSeasonSlug());
            context.setVariable("breadcrumbCurrent", shared.slug());
            templateWriter.write("site/shared-profile", context, dir.resolve(shared.slug() + ".html"),
                    ctx.activeSeasonSlug(), ctx.activeSeasonName());
            result.incrementPages();
        }
    }

    private static String slugOf(GenerationContext ctx, SiteSlugKind kind, UUID id) {
        return kind == SiteSlugKind.TEAM ? ctx.slugs().team(id) : ctx.slugs().driver(id);
    }

    public record ProfileLink(String name, String url) {
    }
}
