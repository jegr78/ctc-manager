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
    private final SiteProperties siteProperties;

    public void write(String templateName, Context context, Path outputFile,
                      String activeSeasonSlug, String activeSeasonName) throws IOException {
        write(templateName, context, outputFile, Path.of(siteProperties.getOutputDir()),
                activeSeasonSlug, activeSeasonName);
    }

    public void write(String templateName, Context context, Path outputFile, Path outRoot,
                      String activeSeasonSlug, String activeSeasonName) throws IOException {
        // Calculate relative paths from the output file location
        Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
        Path relativeRoot = outputFile.getParent().relativize(outRoot);
        context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
        String rootStr = relativeRoot.toString().replace('\\', '/');
        context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
        context.setVariable("activeSeasonSlug", activeSeasonSlug);
        context.setVariable("activeSeasonName", activeSeasonName);

        String html = templateEngine.process(templateName, context);
        Files.writeString(outputFile, html);
        log.debug("Generated: {}", outputFile);
    }
}
