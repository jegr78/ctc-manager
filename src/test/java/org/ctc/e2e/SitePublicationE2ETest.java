package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.sitegen.publication.SitePublicationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class SitePublicationE2ETest extends PlaywrightConfig {
    @Autowired SitePublicationService service;
    @TempDir Path source;

    @Test
    void givenGeneratedScripts_whenPreviewedOnDesktopAndMobile_thenLocalAssetsWorkAndManagerAccessIsBlocked() throws Exception {
        Files.writeString(source.resolve("index.html"), """
                <!doctype html><html><head><link rel="stylesheet" href="style.css"></head><body>
                <h1>Test snapshot A</h1><script>
                try { window.parent.document.body.dataset.compromised = 'yes'; document.body.dataset.parentBlocked = 'no'; }
                catch (e) { document.body.dataset.parentBlocked = 'yes'; }
                fetch('/admin/seasons').then(() => document.body.dataset.connectionBlocked = 'no')
                    .catch(() => document.body.dataset.connectionBlocked = 'yes');
                </script></body></html>
                """);
        Files.writeString(source.resolve("style.css"), "body { color: rgb(11, 22, 33); }");
        var snapshot = service.capture(source);
        setupPage();
        try {
            for (int width : new int[]{1440, 390}) {
                page.setViewportSize(width, 900);
                page.navigate(url("/admin/generate?snapshot=" + snapshot.id()));
                var preview = page.frameLocator("iframe[title='Private website snapshot preview']");
                assertThat(preview.locator("h1")).hasText("Test snapshot A");
                assertThat(preview.locator("body")).hasAttribute("data-parent-blocked", "yes");
                assertThat(preview.locator("body")).hasAttribute("data-connection-blocked", "yes");
                assertThat(preview.locator("body")).hasCSS("color", "rgb(11, 22, 33)");
                assertEquals(null, page.locator("body").getAttribute("data-compromised"));
                assertTrue((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"));
                assertThat(page.getByText("Publication is disabled for this installation.", new com.microsoft.playwright.Page.GetByTextOptions().setExact(false))).isVisible();
            }
        } finally {
            teardownPage();
        }
    }
}
