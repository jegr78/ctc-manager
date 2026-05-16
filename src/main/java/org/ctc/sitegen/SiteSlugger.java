package org.ctc.sitegen;

import org.springframework.stereotype.Component;

/** Spring-injected slug utility that normalises team/season names into stable URL slugs. */
@Component
public class SiteSlugger {

    public String slugify(String input) {
        return input.toLowerCase()
                .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
