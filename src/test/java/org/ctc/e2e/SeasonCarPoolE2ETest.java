package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("e2e")
class SeasonCarPoolE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired CarRepository cars;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void closePage() { teardownPage(); }

    @ParameterizedTest
    @ValueSource(ints = {1440, 390})
    void givenNativeBrowser_whenBuildingFreezingAndReopeningPool_thenSnapshotAndControlsWork(int width) {
        // given
        String entryLabel = "Gr.3 MR + RR " + "W".repeat(107);
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(width, 1000));
        page = context.newPage();
        var season = tx.execute(status -> helper.createSeason("NativePool " + UUID.randomUUID().toString().substring(0, 8)));
        var longTeam = tx.execute(status -> teams.saveAndFlush(new Team("Test pool " + "W".repeat(110), "T-" + UUID.randomUUID().toString().substring(0, 8))));
        var ids = tx.execute(status -> {
            var mr = new Car("Test Pool Browser", "MR " + UUID.randomUUID());
            mr.setCategory(CarCategory.GR_3);
            mr.setDrivetrain(Drivetrain.MR);
            var rr = new Car("Test Pool Browser", "RR " + UUID.randomUUID());
            rr.setCategory(CarCategory.GR_3);
            rr.setDrivetrain(Drivetrain.RR);
            return cars.saveAllAndFlush(List.of(mr, rr)).stream().map(Car::getId).toList();
        });
        try {
            // when
            page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));
            page.locator("#carPoolMode").selectOption("CATEGORY_POOL");
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save pool mode").setExact(true)).click();
            page.locator("#pool-label").fill(entryLabel);
            page.locator("#pool-category-0").selectOption("GR_3");
            page.locator("#pool-drive-0").selectOption("MR");
            page.locator("button[value='add-clause']").click();
            // then
            assertThat(page.locator("#pool-label")).hasValue(entryLabel);
            assertThat(page.locator("#pool-drive-0")).hasValue("MR");
            page.locator("#pool-category-1").selectOption("GR_3");
            page.locator("#pool-drive-1").selectOption("RR");
            page.locator("button[value='save']").click();
            assertThat(page.locator("#pool-preview summary").first()).containsText(entryLabel + " · 2 eligible car(s)");
            page.locator("#freeze-pool").click();
            assertThat(page.locator("#carPool")).containsText("Frozen category pool");
            assertThat(page.locator("#pool-entry-editor")).hasCount(0);
            tx.executeWithoutResult(status -> {
                var car = cars.findById(ids.getFirst()).orElseThrow();
                car.setCategory(CarCategory.GR_4);
                cars.saveAndFlush(car);
            });
            page.reload();
            assertThat(page.locator("#pool-preview summary").first()).containsText("2 eligible car(s)");
            assertTrue((Boolean) page.locator(".pool-entry h4").first().evaluate("e => e.scrollWidth <= e.clientWidth"),
                    "A maximum-length entry label wraps inside its card");
            assertTrue((Boolean) page.locator("#pool-preview summary").first().evaluate("e => e.scrollWidth <= e.clientWidth"),
                    "A maximum-length entry label wraps inside its preview");
            assertTrue((Boolean) page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"),
                    () -> page.evaluate("JSON.stringify([...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().right > innerWidth && e.getBoundingClientRect().width > 0).map(e => ({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right})))").toString());
            page.locator("#reopen-pool").click();
            assertThat(page.locator("#pool-entry-editor")).isVisible();
            assertThat(page.locator("#carPool")).containsText("Draft category pool");
            // when
            page.locator("#pool-label").fill("Empty entry");
            page.locator("#pool-category-0").selectOption("GR_2");
            page.locator("#pool-year-0").fill("1000");
            page.locator("button[value='save']").click();
            page.locator("#freeze-pool").click();
            // then
            assertThat(page.locator("#pool-problems")).containsText("Empty entry");
            assertTrue(page.url().endsWith("#carPool"), "A rejected native submission retains its pool section anchor");
            assertTrue((Boolean) page.evaluate("(() => { const top=document.querySelector('#carPool').getBoundingClientRect().top; return top >= 60 && top <= 96; })()"));
        } finally {
            tx.executeWithoutResult(status -> {
                helper.deleteSeasonCascade(seasons.findById(season.getId()).orElseThrow());
                cars.deleteAllById(ids);
                teams.deleteById(longTeam.getId());
            });
        }
    }
}
