package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.sitegen.publication.SitePublicationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:authenticatedpreview;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "logging.config=classpath:logback-test.xml",
        "spring.security.user.name=test-owner", "spring.security.user.password=test-password",
        "ctc.site.publication.directory=target/authenticated-preview-${surefire.forkNumber:0}", "ctc.outbound.enabled=false"
})
@ActiveProfiles(value = "prod", inheritProfiles = false)
class SitePublicationAuthenticatedPreviewE2ETest extends PlaywrightConfig {
    @Autowired SitePublicationService service;
    @TempDir Path source;

    @Test
    void givenBasicAuthenticationAndOpaqueSandbox_whenPreviewLoads_thenLocalFontsRenderWithoutManagerAccess() throws Exception {
        Files.createDirectories(source.resolve("assets/fonts"));
        try (var font = getClass().getResourceAsStream("/static/site/fonts/ConthraxSb.woff2")) {
            Files.copy(java.util.Objects.requireNonNull(font), source.resolve("assets/fonts/preview.woff2"));
        }
        Files.writeString(source.resolve("index.html"), """
                <!doctype html><html><head><link rel="stylesheet" href="assets/style.css"></head>
                <body><h1>Test authenticated preview</h1></body></html>
                """);
        Files.writeString(source.resolve("assets/style.css"), "@font-face{font-family:PreviewTest;src:url('fonts/preview.woff2')}h1{font-family:PreviewTest}");
        var snapshot = service.capture(source);
        try (var authenticated = browser.newContext(new Browser.NewContextOptions().setHttpCredentials("test-owner", "test-password"))) {
            var owner = authenticated.newPage();
            owner.navigate(url("/admin/generate?snapshot=" + snapshot.id()));
            var preview = owner.frameLocator("iframe");
            assertThat(preview.locator("h1")).hasText("Test authenticated preview");
            var frame = owner.frames().stream().filter(candidate -> candidate.url().contains("/admin/site-preview/")).findFirst().orElseThrow();
            assertTrue((Boolean) frame.evaluate("async () => { await document.fonts.ready; const fonts = [...document.fonts]; return fonts.length === 1 && fonts.every(font => font.status === 'loaded'); }"), "Private preview fonts must load under production authentication");
            assertTrue((Boolean) frame.evaluate("() => { try { window.parent.document.body; return false; } catch { return true; } }"), "Preview must retain its opaque origin");
        }
    }
}
