package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.assertions.LocatorAssertions;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.ctc.TestHelper;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchdayTrailerE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired MediaJobExecutor jobs;

    private final CountDownLatch firstStep = new CountDownLatch(1);
    private final CountDownLatch finish = new CountDownLatch(1);
    private String scopeKey;

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        teardownPage();
        firstStep.countDown();
        finish.countDown();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (scopeKey != null && jobs.status(scopeKey).map(s -> s.running()).orElse(false) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }

    @Test
    void givenRunningTrailerAndNoJavaScript_whenReloading_thenNativeStartButtonAndProgressText() {
        // given
        var matchdayId = runningJob();
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = context.newPage();

            // when
            nativePage.navigate(url("/admin/matchdays/" + matchdayId));
            nativePage.reload();

            // then
            var section = nativePage.locator("#matchday-trailer");
            assertThat(section.locator("form.trailer-start button[type=submit]")).hasText("Render trailer");
            assertThat(section.locator("[data-trailer-status]")).hasText("Rendering scene 1 of 2");
            assertThat(section.getByLabel("Music")).isVisible();
        }
    }

    @Test
    void givenRunningTrailerWithJavaScript_whenJobAdvances_thenStatusUpdatesWithoutReload() {
        // given
        var matchdayId = runningJob();
        page.navigate(url("/admin/matchdays/" + matchdayId));
        var status = page.locator("#matchday-trailer [data-trailer-status]");
        assertThat(status).hasAttribute("aria-live", "polite");
        assertThat(status).hasText("Rendering scene 1 of 2");
        page.evaluate("window.__trailerMarker = true");

        // when
        firstStep.countDown();

        // then
        assertThat(status).hasText("Rendering scene 2 of 2", new LocatorAssertions.HasTextOptions().setTimeout(10_000));
        org.assertj.core.api.Assertions.assertThat(page.evaluate("window.__trailerMarker === true")).as("no reload").isEqualTo(true);
    }

    private UUID runningJob() {
        var fixture = helper.createFullSeasonFixture("Test-UI Trailer " + UUID.randomUUID().toString().substring(0, 8));
        UUID matchdayId = fixture.matchday().getId();
        scopeKey = "matchday:" + matchdayId;
        jobs.submit(MediaJobKind.TRAILER, scopeKey, ctx -> {
            ctx.progress(1, 2, "Rendering scene 1 of 2");
            firstStep.await(30, TimeUnit.SECONDS);
            ctx.progress(2, 2, "Rendering scene 2 of 2");
            finish.await(30, TimeUnit.SECONDS);
        });
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!jobs.status(scopeKey).map(s -> s.step() == 1).orElse(false) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        return matchdayId;
    }
}
