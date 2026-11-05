package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.RacePenaltyService;
import org.ctc.domain.service.RaceService;
import org.ctc.sitegen.MatchdaysPageGenerator;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteSlugger;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class RacePenaltyE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired RaceService races;
    @Autowired RacePenaltyService penalties;
    @Autowired MatchdaysPageGenerator pages;
    @Autowired SiteSlugger slugger;
    @TempDir Path output;

    @ParameterizedTest
    @ValueSource(ints = {1440, 390})
    void givenNativeDesktopOrPhone_whenCorrectingTeamPenalties_thenKeepHistoryAndPublishSafeOfficialScore(int width) throws Exception {
        // given
        var fixture = tx.execute(status -> helper.createFullSeasonFixture("NativePenalty" + UUID.randomUUID().toString().substring(0, 6)));
        var driverIds = tx.execute(status -> {
            var race = helperRace(fixture);
            var scoring = race.getMatchday().getPhase().getRaceScoring(); scoring.setRacePoints("80,70"); scoring.setQualiPoints(""); scoring.setFastestLapPoints(0);
            var home = helper.createDriver("NativePenaltyH" + UUID.randomUUID().toString().substring(0, 6), "Test home");
            var away = helper.createDriver("NativePenaltyA" + UUID.randomUUID().toString().substring(0, 6), "Test away");
            lineups.saveAllAndFlush(List.of(new RaceLineup(race, home, race.getHomeTeam()), new RaceLineup(race, away, race.getAwayTeam(), true)));
            races.saveResults(race.getId(), List.of(new RaceService.RaceResultData(home.getId(), null, null, 1, 1, false), new RaceService.RaceResultData(away.getId(), null, null, 2, 2, false)));
            return List.of(home.getId(), away.getId());
        });
        String reason = "Private penalty reason <img src=x onerror='window.privateLeak=1'> " + "W".repeat(180);
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(width, 1000))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/races/" + fixture.race().getId()));
            nativePage.getByText("Team penalty points", new Page.GetByTextOptions().setExact(true)).click();
            // when
            nativePage.getByLabel(fixture.homeTeam().getShortName() + " penalty points").fill("20");
            nativePage.getByLabel(fixture.awayTeam().getShortName() + " penalty points").fill("10");
            nativePage.getByLabel("Internal correction reason").fill(reason);
            nativePage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save team penalty points")).click();
            // then
            assertThat(nativePage.locator(".alert-success")).containsText("Regenerate");
            assertThat(nativePage.locator("#penalty-history")).containsText(reason);
            assertThat(nativePage.locator("#penalty-history img")).hasCount(0);
            assertThat(penalties.view(fixture.race().getId()).totals().home().official()).isEqualTo(70);
            assertThat((Boolean) nativePage.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();
            nativePage.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/team-penalties-" + width + ".png")).setFullPage(true));
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
            var html = Files.readString(publicFile);
            assertThat(html).doesNotContain("Private penalty reason", "development-manager", "resultFingerprint");
            nativePage.navigate(publicFile.toUri().toString());
            // then
            assertThat(nativePage.locator(".match-score")).hasText("70 : 70");
            assertThat(nativePage.locator(".match-meta")).containsText("Gross 80; penalty total 20; net deduction 10; official 70");
            assertThat((Boolean) nativePage.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();
            nativePage.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/team-penalties-public-" + width + ".png")).setFullPage(true));
        } finally {
            tx.executeWithoutResult(status -> { helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush(); drivers.deleteAllById(driverIds); teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId())); });
        }
    }

    private org.ctc.domain.model.Race helperRace(TestHelper.SeasonFixture fixture) {
        return seasons.findById(fixture.season().getId()).orElseThrow().getMatchdays().stream()
                .flatMap(day -> day.getRaces().stream()).filter(race -> race.getId().equals(fixture.race().getId())).findFirst().orElseThrow();
    }
}
