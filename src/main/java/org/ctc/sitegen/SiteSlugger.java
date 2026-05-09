package org.ctc.sitegen;

import org.springframework.stereotype.Component;

/**
 * Spring-injected slug utility extracted from {@code SiteGeneratorService.slugify}.
 *
 * <p>The slug body is byte-identical to the legacy private method to preserve URL stability
 * (D-02 / D-03 stability + Risk 7 GitHub-Pages case-sensitivity).
 */
@Component
public class SiteSlugger {

    public String slugify(String input) {
        return input.toLowerCase()
                .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
