package org.ctc.sitegen.model;

import java.nio.file.Path;
import org.ctc.domain.model.Season;

/**
 * Immutable per-season context passed to every page-generator helper.
 *
 * <p>Carries the output root path, the current Season, active season slug and name for nav
 * highlighting, and playoff metadata for cross-links.
 */
public record GenerationContext(
        Path outPath,
        Season season,
        String activeSeasonSlug,
        String activeSeasonName,
        boolean hasPlayoff,
        String playoffSeasonSlug
) {}
