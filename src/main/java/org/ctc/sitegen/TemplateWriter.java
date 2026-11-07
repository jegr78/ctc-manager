package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Shared Thymeleaf collaborator that renders a template and writes the resulting HTML to disk. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateWriter {

    private final TemplateEngine templateEngine;

    /** Writes the page to {@code outputFile}, with asset and root links relative to {@code outRoot}. */
    public void write(String templateName, Context context, Path outputFile, Path outRoot,
                      String activeSeasonSlug, String activeSeasonName) throws IOException {
        if (!outputFile.toAbsolutePath().normalize().startsWith(outRoot.toAbsolutePath().normalize())) {
            throw new IOException("Refusing to write outside the site output: " + outputFile);
        }
        // Calculate relative paths from the output file location
        // NP: all callers pass paths of the form dir.resolve("file.html") — always multi-component,
        // getParent() is guaranteed non-null. See config/spotbugs-exclude.xml TemplateWriter.write entry.
        Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
        Path relativeRoot = outputFile.getParent().relativize(outRoot);
        context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
        String rootStr = relativeRoot.toString().replace('\\', '/');
        context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
        context.setVariable("activeSeasonSlug", activeSeasonSlug);
        context.setVariable("activeSeasonName", activeSeasonName);

        var documents = new java.util.ArrayList<DocumentLink>();
        if (context.getVariable("season") instanceof org.ctc.domain.model.Season season
                && context.getVariable("seasonSlug") instanceof String slug && !slug.isBlank()) {
            addDocument(documents, "Rulebook", season.getRulebookUrl());
            addDocument(documents, "Racing Etiquette", season.getRacingEtiquetteUrl());
        }
        context.setVariable("seasonDocumentLinks", java.util.List.copyOf(documents));
        String html = templateEngine.process(templateName, context);
        Files.writeString(outputFile, html);
        log.debug("Generated: {}", outputFile);
    }
    private static void addDocument(java.util.List<DocumentLink> documents, String label, String value) {
        String url = org.ctc.domain.model.SeasonDocumentLinks.normalize(value);
        if (url != null) documents.add(new DocumentLink(label, url));
    }

    public record DocumentLink(String label, String url) { }
}
