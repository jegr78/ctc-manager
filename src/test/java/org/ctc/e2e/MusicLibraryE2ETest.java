package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.options.FilePayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.ctc.domain.repository.MusicTrackRepository;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.MediaTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MusicLibraryE2ETest extends PlaywrightConfig {
    @Autowired MusicTrackRepository repository;
    @Autowired MusicLibraryService library;
    @Autowired MediaTool media;

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
        repository.findAll().stream().filter(track -> track.getTitle().startsWith("Test_"))
                .forEach(track -> library.delete(track.getId()));
    }

    @Test
    void givenNoJavaScript_whenUploadingAndDeleting_thenNativeFormsWork() throws Exception {
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            // given
            var nativePage = context.newPage();
            nativePage.navigate(url("/admin/music"));
            assertThat(nativePage.locator("nav a[href='/admin/music']")).hasAttribute("aria-current", "page");
            // when
            nativePage.locator("#music-file").setInputFiles(new FilePayload("test-sine.mp3", "audio/mpeg", sineMp3()));
            nativePage.getByLabel("Title").fill("Test_E2E Sine");
            nativePage.getByLabel("Artist").fill("Test Artist");
            nativePage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Add music")).click();
            // then
            assertThat(nativePage.locator(".alert-success")).containsText("Test_E2E Sine");
            var row = nativePage.locator("[data-music-track]", new com.microsoft.playwright.Page.LocatorOptions().setHasText("Test_E2E Sine"));
            assertThat(row).containsText("0:02");
            // when
            row.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Delete Test_E2E Sine")).click();
            // then
            assertThat(nativePage.locator(".alert-success")).hasText("Music track deleted.");
            assertThat(row).hasCount(0);
        }
    }

    @Test
    void givenMusicPage_whenTabbingToTheFileInput_thenItIsLabelledAndFocusable() {
        // given
        page.navigate(url("/admin/music"));
        // when
        page.locator("#music-file").focus();
        // then
        assertThat(page.locator("#music-file")).isFocused();
        assertThat(page.getByLabel("MP3 file")).hasAttribute("aria-describedby", "music-file-hint");
    }

    private byte[] sineMp3() throws Exception {
        Path out = Files.createTempFile("test-sine", ".mp3");
        try {
            media.run(List.of("-f", "lavfi", "-i", "sine=d=2", "-y", out.toString()), Duration.ofSeconds(30));
            return Files.readAllBytes(out);
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
