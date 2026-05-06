package org.ctc.sitegen.model;

import java.nio.file.Path;
import org.ctc.domain.model.Season;

/**
 * Immutable per-season context passed to every page-generator helper.
 *
 * <p>Carries the values that every {@code generateXxx} method on the legacy
 * {@code SiteGeneratorService} took as positional parameters: the output root path, the
 * current Season, the active season's slug + name (for cross-page nav highlighting), and
 * playoff metadata (whether the season has a playoff and its slug for cross-links).
 */
public record GenerationContext(
        Path outPath,
        Season season,
        String activeSeasonSlug,
        String activeSeasonName,
        boolean hasPlayoff,
        String playoffSeasonSlug
) {}
