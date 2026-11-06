package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.ctc.TestHelper;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceDisqualificationService;
import org.ctc.domain.service.RaceService;
import org.ctc.sitegen.MatchdaysPageGenerator;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteSlugger;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class DisqualificationE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired RaceRepository raceRows;
    @Autowired RaceService races;
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired MatchdaysPageGenerator pages;
    @Autowired SiteSlugger slugger;
    @TempDir Path output;

    @ParameterizedTest
    @ValueSource(ints = {1440, 390})
    void givenDesktopOrPhoneWithoutJavascript_whenRecordingAndClearingDsq_thenKeepEvidenceBonusesAndSafePublicClassification(int width) throws Exception {
        // given
        var fixture = tx.execute(status -> helper.createFullSeasonFixture("NativeDsq" + UUID.randomUUID().toString().substring(0, 6)));
        var driverIds = tx.execute(status -> {
            var race = raceRows.findById(fixture.race().getId()).orElseThrow(); var scheme = race.getMatchday().getPhase().getRaceScoring();
            scheme.setRacePoints("80,70"); scheme.setQualiPoints("3,2,1"); scheme.setFastestLapPoints(2);
            var home = helper.createDriver("TestDsqH" + UUID.randomUUID().toString().substring(0, 6), "Test home");
            var away = helper.createDriver("TestDsqA" + UUID.randomUUID().toString().substring(0, 6), "Test away");
            lineups.saveAllAndFlush(List.of(new RaceLineup(race, home, race.getHomeTeam()), new RaceLineup(race, away, race.getAwayTeam(), true)));
            races.saveResults(race.getId(), List.of(new RaceService.RaceResultData(home.getId(), null, null, 1, 1, true),
                    new RaceService.RaceResultData(away.getId(), null, null, 2, 2, false)));
            return List.of(home.getId(), away.getId());
        });
        var raceId = fixture.race().getId(); var homePsn = drivers.findById(driverIds.getFirst()).orElseThrow().getPsnId();
        var resultId = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
                .filter(r -> r.getDriver().getId().equals(driverIds.getFirst())).findFirst().orElseThrow().getId());
        String reason = "Private DSQ reason <img src=x onerror='window.privateLeak=1'> " + "W".repeat(180);
        Files.createDirectories(Path.of(".screenshots"));
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(width, 1000))) {
            var nativePage = nativeContext.newPage(); nativePage.navigate(url("/admin/races/" + raceId + "/results"));
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Review disqualification for " + homePsn)).click();
            // when
            nativePage.getByLabel("Classification status").selectOption("true"); nativePage.getByLabel("Internal correction reason").fill(reason);
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save driver disqualification")).click();
            // then
            assertThat(nativePage.locator(".alert-success")).containsText("manual team penalty totals");
            assertThat(nativePage.locator("#dsq-current-classification")).hasText("DSQ");
            assertThat(nativePage.locator("#disqualification-history")).containsText(reason);
            assertThat(nativePage.locator("#disqualification-history img")).hasCount(0);
            var saved = disqualifications.view(raceId, resultId);
            assertThat(saved.result().getPosition()).isEqualTo(1); assertThat(saved.result().getPointsRace()).isZero();
            assertThat(saved.result().getPointsQuali()).isEqualTo(3); assertThat(saved.result().getPointsFl()).isEqualTo(2);
            assertThat(saved.result().getPointsTotal()).isEqualTo(5); assertThat(saved.history()).hasSize(1);
            assertThat((Boolean) nativePage.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();
            nativePage.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/disqualification-native-" + width + ".png")).setFullPage(true));
            Files.writeString(Path.of(".screenshots/disqualification-native-" + width + ".html"), nativePage.content());
            // when
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Back to Race Results")).click();
            assertThat(nativePage.locator(".classification-label").filter(new Locator.FilterOptions().setHasText("DSQ"))).hasCount(1);
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Results").setExact(true)).click();
            // then
            assertThat(disqualifications.view(raceId, resultId).disqualified()).isTrue();
            assertThat(disqualifications.view(raceId, resultId).history()).hasSize(1);
            // when
            var publicFile = tx.execute(status -> {
                var season = seasons.findById(fixture.season().getId()).orElseThrow();
                var slugs = new SiteSlugs(Map.of(fixture.homeTeam().getId(), "test-home", fixture.awayTeam().getId(), "test-away"),
                        Map.of(driverIds.get(0), "test-home-driver", driverIds.get(1), "test-away-driver"), List.of());
                try { pages.generateDetails(new GenerationContext(output, season, "test", "Test", false, null, slugs), new SiteGeneratorService.GenerationResult()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                return output.resolve("season").resolve(slugger.slugify(season.getDisplayLabel())).resolve("matchday").resolve(slugger.slugify(fixture.matchday().getLabel()) + ".html");
            });
            var css = output.resolve("assets/css/style.css"); Files.createDirectories(css.getParent());
            Files.copy(Path.of("src/main/resources/static/site/css/style.css"), css);
            var logo = output.resolve("assets/img/ctc-logo-white.png"); Files.createDirectories(logo.getParent());
            Files.copy(Path.of("src/main/resources/static/site/img/ctc-logo-white.png"), logo);
            assertThat(Files.readString(publicFile)).doesNotContain("Private DSQ reason", "development-manager", "resultFingerprint");
            Files.copy(publicFile, Path.of(".screenshots/disqualification-public-" + width + ".html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            nativePage.navigate(publicFile.toUri().toString());
            nativePage.locator(".race-driver-results > summary").click();
            // then
            assertThat(nativePage.locator(".match-score")).hasText("5 : 82");
            assertThat(nativePage.locator(".result-position").filter(new Locator.FilterOptions().setHasText("DSQ"))).hasCount(1);
            assertThat(nativePage.locator(".result-position").filter(new Locator.FilterOptions().setHasText("Position 1"))).hasCount(1);
            String publicResults = width < 768 ? ".mobile-race-results" : ".race-full-table";
            assertThat(nativePage.locator(publicResults + " :text-is('DSQ'):visible")).hasCount(1);
            if (width < 768) {
                var label = nativePage.locator(".mobile-race-results .result-position > span:last-child")
                        .filter(new Locator.FilterOptions().setHasText("DSQ"));
                Number lines = (Number) label.evaluate("element => { const range = document.createRange(); range.selectNodeContents(element); return range.getClientRects().length; }");
                assertThat(lines.intValue()).as("DSQ remains one readable line in the mobile classification column").isEqualTo(1);
            }
            assertThat((Boolean) nativePage.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();
            nativePage.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/disqualification-public-" + width + ".png")).setFullPage(true));
            try (var previewContext = browser.newContext(new Browser.NewContextOptions().setViewportSize(width, 1000))) {
                var preview = previewContext.newPage(); preview.navigate(url("/admin/races/" + raceId + "/results"));
                var driverRow = preview.locator(".result-row").filter(new Locator.FilterOptions().setHasText(homePsn));
                assertThat(driverRow.locator(".points-cell")).hasText("5");
                driverRow.locator(".quali-input").fill("2");
                assertThat(driverRow.locator(".points-cell")).hasText("4");
            }
            // when
            nativePage.navigate(url("/admin/races/" + raceId + "/results/" + resultId + "/disqualification"));
            nativePage.getByLabel("Classification status").selectOption("false"); nativePage.getByLabel("Internal correction reason").fill("Test clear decision");
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save driver disqualification")).click();
            // then
            assertThat(nativePage.locator("#dsq-current-classification")).hasText("1");
            assertThat(disqualifications.view(raceId, resultId).history()).hasSize(2);
            assertThat(disqualifications.view(raceId, resultId).result().getPointsTotal()).isEqualTo(85);
        } finally {
            tx.executeWithoutResult(status -> { helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
                drivers.deleteAllById(driverIds); teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId())); });
        }
    }
}
