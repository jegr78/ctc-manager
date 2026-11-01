package org.ctc.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.ctc.TestHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class TimeProgressionE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate transactions;
    @Autowired org.ctc.domain.repository.TeamRepository teams;

    @Test
    void givenNativeEditor_whenSavingOnDesktopAndMobile_thenEqualConditionsAndZeroRemainDistinct() throws Exception {
        // given
        var fixture = transactions.execute(status -> helper.createFullSeasonFixture("Test_Time_" + UUID.randomUUID().toString().substring(0, 8)));
        setupPage();
        page.emulateMedia(new com.microsoft.playwright.Page.EmulateMediaOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE));
        try {
            // when / then
            Files.createDirectories(Path.of(".screenshots"));
            for (int width : new int[]{1440, 390}) {
                page.setViewportSize(width, 900);
                page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
                page.getByLabel("Time Progression", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true)).selectOption("EQUAL_CONDITIONS");
                page.getByLabel("Time Progression Multiplier").fill("");
                page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save Race")).click();
                page.waitForURL("**/admin/races?matchdayId=*");
                page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
                assertThat(page.locator("#timeProgressionMode")).hasValue("EQUAL_CONDITIONS");
                assertThat(page.locator("#timeProgressionMultiplier")).hasValue("");
                org.junit.jupiter.api.Assertions.assertTrue((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"));
                page.locator("#race-settings").screenshot(new com.microsoft.playwright.Locator.ScreenshotOptions()
                        .setPath(Path.of(".screenshots/time-progression-controls-" + width + ".png")));
                page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(Path.of(".screenshots/time-progression-" + width + ".png")).setFullPage(true));
                page.locator("#timeProgressionMode").selectOption("MULTIPLIER");
                page.locator("#timeProgressionMultiplier").fill("0");
                page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save Race")).click();
                page.waitForURL("**/admin/races?matchdayId=*");
                page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
                assertThat(page.locator("#timeProgressionMode")).hasValue("MULTIPLIER");
                assertThat(page.locator("#timeProgressionMultiplier")).hasValue("0");
            }
        } finally {
            teardownPage();
            transactions.executeWithoutResult(status -> {
                helper.deleteSeasonCascade(fixture.season());
                teams.deleteById(fixture.homeTeam().getId());
                teams.deleteById(fixture.awayTeam().getId());
            });
        }
    }
}
