package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.ScreenshotAnimations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class TrackEnvironmentE2ETest extends PlaywrightConfig {
    @Autowired TrackRepository tracks;
    UUID id;

    @BeforeEach
    void setUp() {
        setupPage();
        id = tracks.saveAndFlush(new Track("Test layout editor " + UUID.randomUUID(), "Test country")).getId();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
        tracks.deleteById(id);
    }

    @Test
    void givenLayoutEditor_whenSavingWithoutJavascript_thenRetainOrderAndRenderUsableDesktopAndMobileControls() throws Exception {
        String edit = "/admin/tracks/" + id + "/edit";
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url(edit));
            nativePage.locator("#timeOfDayText").fill("Night\nSunrise");
            nativePage.locator("#rainCapability").selectOption("RAIN");
            nativePage.locator("#track-environment button[type='submit']").click();
            assertThat(nativePage).hasURL(url(edit) + "#track-environment");
            assertThat(nativePage.locator("#timeOfDayText")).hasValue("Night\nSunrise");
            assertThat(nativePage.locator("#rainCapability")).hasValue("RAIN");
            nativePage.locator("#timeOfDayText").fill("Night\nNight");
            nativePage.locator("#track-environment button[type='submit']").click();
            assertThat(nativePage.locator("#track-environment [role='alert']")).containsText("distinct");
            assertThat(nativePage.locator("#timeOfDayText")).hasValue("Night\nNight");
        }
        var evidence = Path.of("target/failsafe-reports/track-environment");
        Files.createDirectories(evidence);
        page.navigate(url(edit));
        for (int width : new int[]{1440, 390}) {
            page.setViewportSize(width, 900);
            if (width < 768) page.waitForFunction("document.querySelector('.sidebar').getBoundingClientRect().right <= 0");
            page.locator(".entity-section-nav a[href='#track-environment']").click();
            page.locator("#timeOfDayText").click();
            assertThat(page.locator("#timeOfDayText")).isFocused();
            assertThat(page.locator("#rainCapability")).hasValue("RAIN");
            org.junit.jupiter.api.Assertions.assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
            org.junit.jupiter.api.Assertions.assertTrue(((Number) page.locator("#timeOfDayText").evaluate("element => element.getBoundingClientRect().top")).doubleValue() > 60);
            page.locator("#track-environment").screenshot(new Locator.ScreenshotOptions()
                    .setAnimations(ScreenshotAnimations.DISABLED).setPath(evidence.resolve("admin-" + width + ".png")));
        }
    }
}
