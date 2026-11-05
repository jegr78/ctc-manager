package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.TestHelper;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceCarSelectionService;
import org.ctc.domain.service.RaceService;
import org.ctc.gt7sync.Gt7ScraperService;
import org.ctc.gt7sync.GtPlusCarYearSource;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("e2e")
class CategoryPoolReleaseE2ETest extends PlaywrightConfig {
    static final Path ROOT = SitegenTestDir.create("pool-release-browser");
    static final String LABEL = "Test combined Gr.3 MR + RR";
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired CarRepository cars;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired RaceRepository races;
    @Autowired TrackRepository tracks;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired RaceService raceService;
    @Autowired RaceCarSelectionService selections;
    @Autowired BackupArchiveService archives;
    @Autowired BackupImportService imports;
    @MockitoBean Gt7ScraperService scraper;
    @MockitoBean GtPlusCarYearSource years;
    TestHelper.SeasonFixture fixture;
    TestHelper.SeasonFixture fixed;
    UUID mr;
    UUID rr;
    UUID unknown;
    UUID track;
    UUID second;
    UUID otherHome;
    UUID entry;

    @BeforeEach
    void setUp() throws Exception {
        setupPage();
        Files.createDirectories(ROOT.resolve("uploads"));
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            fixture = helper.createFullSeasonFixture("TPRC" + suffix);
            fixture.matchday().getPhase().setLegs(3);
            fixed = helper.createFullSeasonFixture("TPRF" + suffix);
            mr = cars.saveAndFlush(new Car("Test release " + suffix, "MR concept")).getId();
            rr = cars.saveAndFlush(new Car("Test release " + suffix, "RR concept")).getId();
            cars.findById(mr).orElseThrow().setGt7Id("car999988");
            cars.findById(rr).orElseThrow().setGt7Id("car999987");
            var info = new Car("Test <img src=x onerror='window.injected=1'>", "Unknown year " + suffix);
            info.setCategory(CarCategory.GR_4);
            info.setDrivetrain(Drivetrain.FR);
            info.setModelYearOverride(true);
            info.setModelYearSource("MANUAL");
            info.setModelYearEvidence("Deliberately unknown test year");
            unknown = cars.saveAndFlush(info).getId();
            track = tracks.saveAndFlush(new Track("Test release circuit " + suffix)).getId();
            fixture.season().getTracks().add(tracks.findById(track).orElseThrow());
            fixed.season().getCars().add(cars.findById(rr).orElseThrow());
            second = draft(false);
            otherHome = draft(true);
        });
        org.assertj.core.api.Assertions.assertThat(raceService.saveRace(fixed.race().getId(), fixed.matchday().getId(),
                fixed.homeTeam().getId(), fixed.awayTeam().getId(), null, rr, null, 5, null, null, null, null, null,
                TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, null).success()).isTrue();
        var source = tx.execute(status -> List.of(mr, rr).stream().map(id -> {
            var car = cars.findById(id).orElseThrow();
            return new Gt7ScraperService.ScrapedCar(car.getGt7Id(), car.getManufacturer(), car.getName(), "",
                    CarCategory.GR_3, id.equals(mr) ? Drivetrain.MR : Drivetrain.RR, "Gr.3", id.equals(mr) ? "MR" : "RR");
        }).toList());
        when(scraper.scrapeCars()).thenReturn(source);
        when(scraper.scrapeTracks()).thenReturn(List.of());
        when(years.fetchCandidates()).thenReturn(Map.of("car999988", new GtPlusCarYearSource.YearCandidate(9892, "Test numeric-ID candidate MR", null),
                "car999987", new GtPlusCarYearSource.YearCandidate(9892, "Test numeric-ID candidate RR", null)));
    }

    @AfterEach
    void cleanUp() {
        teardownPage();
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            for (var graph : List.of(fixture, fixed)) helper.deleteSeasonCascade(seasons.findById(graph.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr, unknown));
            tracks.deleteById(track);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId(), fixed.homeTeam().getId(), fixed.awayTeam().getId()));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenExistingCarsAndYearCandidates_whenCompletingNativeSeasonWorkflow_thenKeepSelectionAndBackupContracts(boolean javaScript) throws Exception {
        // given
        context.close();
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(javaScript)
                .setViewportSize(javaScript ? 1440 : 375, 900));
        page = context.newPage();
        var errors = new ArrayList<String>();
        page.onPageError(errors::add);
        // when: enrich the existing identities through the real native sync and review the candidate.
        page.navigate(url("/admin/gt7-sync"));
        page.locator("#fetchBtn").click();
        assertThat(page.locator("input[name=selectedMetadata]")).hasCount(2);
        page.locator("input[name=selectedMetadata][value=car999988]").check();
        page.locator("input[name=selectedMetadata][value=car999987]").check();
        checkWidths();
        page.locator("#importBtn").click();
        assertThat(page.locator(".alert-success")).containsText("2 car metadata updates");
        for (var car : List.of(mr, rr)) {
            page.navigate(url("/admin/cars/" + car + "/edit"));
            assertThat(page.locator("#modelYear")).isEmpty();
            page.locator("#car-metadata details summary").click();
            assertThat(page.locator("#car-metadata details")).containsText("9892");
            assertThat(page.locator("#car-metadata details")).containsText("Test numeric-ID candidate");
            page.locator("#modelYear").fill("9892");
            checkWidths();
            page.locator(".entity-editor-actions button").click();
            assertThat(page).hasURL(url("/admin/cars"));
        }
        // then
        tx.executeWithoutResult(status -> {
            for (var id : List.of(mr, rr)) {
                var car = cars.findById(id).orElseThrow();
                org.assertj.core.api.Assertions.assertThat(car.getModelYear()).isEqualTo(9892);
                org.assertj.core.api.Assertions.assertThat(car.getCategory()).isEqualTo(CarCategory.GR_3);
                org.assertj.core.api.Assertions.assertThat(car.isModelYearOverride()).isTrue();
                org.assertj.core.api.Assertions.assertThat(car.getCategorySource()).contains("GT7:");
            }
            org.assertj.core.api.Assertions.assertThat(races.findById(fixed.race().getId()).orElseThrow().getCar().getId()).isEqualTo(rr);
        });
        // when: build the exact-year OR pool through native forms.
        page.navigate(url("/admin/seasons/" + fixture.season().getId() + "/edit"));
        page.locator("#carPoolMode").selectOption("CATEGORY_POOL");
        page.getByText("Save pool mode", new Page.GetByTextOptions().setExact(true)).click();
        page.locator("#pool-label").fill(LABEL);
        page.locator("#pool-category-0").selectOption("GR_3");
        page.locator("#pool-drive-0").selectOption("MR");
        page.locator("#pool-year-0").fill("9892");
        page.locator("button[value=add-clause]").click();
        page.locator("#pool-category-1").selectOption("GR_3");
        page.locator("#pool-drive-1").selectOption("RR");
        page.locator("#pool-year-1").fill("9892");
        checkWidths();
        page.locator("button[value=save]").click();
        assertThat(page.locator("#pool-preview summary").first()).containsText("2 eligible car(s)");
        page.locator("#freeze-pool").click();
        assertThat(page.locator("#carPool")).containsText("Frozen category pool");
        entry = tx.execute(status -> seasons.findById(fixture.season().getId()).orElseThrow().getCarPoolEntries().getFirst().getId());
        choose(fixture.race().getId(), mr, javaScript);
        // then: the other alternative cannot bypass consumption, including a forged native submission.
        page.navigate(url("/admin/races/" + second + "/edit"));
        assertThat(page.locator("#carPoolEntryId option[value='" + entry + "']")).isDisabled();
        var response = context.request().post(url("/admin/races/save"), RequestOptions.create().setMaxRedirects(0)
                .setForm(FormData.create().set("lobbyRulesProfile", "LEGACY").set("startType", "GRID_FALSE_START").set("slipstream", "REAL").set("id", second.toString()).set("matchdayId", fixture.matchday().getId().toString())
                        .set("homeTeamId", fixture.homeTeam().getId().toString()).set("awayTeamId", fixture.awayTeam().getId().toString())
                        .set("carPoolEntryId", entry.toString()).set("carId", rr.toString()).set("numberOfLaps", "19")));
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(302);
        page.navigate(url("/admin/races/" + second + "/edit"));
        assertThat(page.locator(".alert-error")).containsText("already used");
        assertThat(page.locator("#numberOfLaps")).hasValue("19");
        tx.executeWithoutResult(status -> org.assertj.core.api.Assertions.assertThat(races.findById(second).orElseThrow().getCar()).isNull());
        // when: another home can use the same entry, while edit and delete preserve derived ownership.
        choose(otherHome, rr, javaScript);
        choose(fixture.race().getId(), rr, javaScript);
        var deletion = context.request().post(url("/admin/races/" + fixture.race().getId() + "/delete"), RequestOptions.create().setMaxRedirects(0));
        org.assertj.core.api.Assertions.assertThat(deletion.status()).isEqualTo(302);
        choose(second, mr, javaScript);
        page.navigate(url("/admin/races/" + second));
        assertThat(page.locator(".pool-entry-label")).hasText(LABEL);
        checkWidths();
        // when: render the real output, export, change the catalog, then restore the same backup.
        Path cards = ROOT.resolve("uploads/team-cards").resolve(fixture.season().getId().toString());
        Files.createDirectories(cards);
        for (var team : List.of(fixture.homeTeam(), fixture.awayTeam()))
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", cards.resolve(team.getShortName() + ".png").toFile());
        var generated = context.request().post(url("/admin/races/" + second + "/generate-settings"), RequestOptions.create().setMaxRedirects(0));
        org.assertj.core.api.Assertions.assertThat(generated.status()).isEqualTo(302);
        org.assertj.core.api.Assertions.assertThat(context.request().get(url("/uploads/races/" + second + "/settings.png")).status()).isEqualTo(200);
        Path png = ROOT.resolve("uploads/races/" + second + "/settings.png");
        byte[] pngBytes = Files.readAllBytes(png);
        org.assertj.core.api.Assertions.assertThat(ImageIO.read(png.toFile()).getWidth()).isEqualTo(1920);
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        tx.executeWithoutResult(status -> cars.findById(mr).orElseThrow().setCategory(CarCategory.GR_4));
        var preview = imports.stage(new MockMultipartFile("file", "test-pool-release.zip", "application/zip", output.toByteArray()));
        imports.execute(preview.stagingId());
        // then
        tx.executeWithoutResult(status -> {
            org.assertj.core.api.Assertions.assertThat(cars.findById(mr).orElseThrow().getCategory()).isEqualTo(CarCategory.GR_3);
            org.assertj.core.api.Assertions.assertThat(entries.findById(entry).orElseThrow().getMembers())
                    .extracting(member -> member.getCar().getId()).containsExactlyInAnyOrder(mr, rr);
            org.assertj.core.api.Assertions.assertThat(races.findById(second).orElseThrow().getCar().getId()).isEqualTo(mr);
            for (var home : List.of(fixture.homeTeam(), fixture.awayTeam()))
                org.assertj.core.api.Assertions.assertThat(selections.availability(fixture.season().getId(), home.getId(), null).consumedEntryIds()).contains(entry);
        });
        org.assertj.core.api.Assertions.assertThat(Files.readAllBytes(png)).isEqualTo(pngBytes);
        org.assertj.core.api.Assertions.assertThat(context.request().get(url("/uploads/races/" + second + "/settings.png")).status()).isEqualTo(200);
        page.navigate(url("/admin/races/" + fixed.race().getId()));
        assertThat(page.locator(".pool-entry-label")).hasCount(0);
        page.navigate(url("/admin/data-audit"));
        var information = page.locator("#audit-car_eligibility tbody tr").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(unknown.toString()));
        assertThat(information).containsText("model year is unknown");
        assertThat(information).containsText("Unknown values are valid");
        assertThat(page.locator("img[src=x], [onerror]")).hasCount(0);
        checkWidths();
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
    }

    private UUID draft(boolean reverse) {
        var race = new Race();
        race.setMatchday(fixture.matchday());
        race.setMatch(fixture.match());
        if (reverse) {
            race.setHomeTeamOverride(fixture.awayTeam());
            race.setAwayTeamOverride(fixture.homeTeam());
        }
        return races.saveAndFlush(race).getId();
    }

    private void choose(UUID race, UUID car, boolean javaScript) {
        page.navigate(url("/admin/races/" + race + "/edit"));
        page.locator("#carPoolEntryId").selectOption(entry.toString());
        if (!javaScript) page.getByText("Refresh choices", new Page.GetByTextOptions().setExact(true)).click();
        assertThat(page.locator("#carId option[value='" + car + "']")).hasCount(1);
        page.locator("#carId").selectOption(car.toString());
        page.locator("#trackId").selectOption(track.toString());
        for (var field : Map.ofEntries(Map.entry("numberOfLaps", "5"), Map.entry("numberOfRequiredPitStops", "1"),
                Map.entry("tyreWearMultiplier", "2"), Map.entry("fuelConsumptionMultiplier", "2"), Map.entry("refuelingSpeed", "1"),
                Map.entry("initialFuel", "Full"), Map.entry("timeOfDay", "Day"), Map.entry("weather", "Clear"),
                Map.entry("availableTyres", "RS, RM"), Map.entry("mandatoryTyres", "RM")).entrySet())
            page.locator("#" + field.getKey()).fill(field.getValue());
        page.locator("#timeProgressionMode").selectOption("MULTIPLIER");
        page.locator("#timeProgressionMultiplier").fill("0");
        checkWidths();
        page.locator(".entity-editor-actions button").focus();
        page.keyboard().press("Enter");
        assertThat(page).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
        tx.executeWithoutResult(status -> {
            var saved = races.findById(race).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(saved.getCar().getId()).isEqualTo(car);
            org.assertj.core.api.Assertions.assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
        });
    }

    private void checkWidths() {
        int previous = page.viewportSize().width;
        for (var width : List.of(1440, 375)) {
            page.setViewportSize(width, 900);
            org.assertj.core.api.Assertions.assertThat(page.locator(".score-banner__name").evaluateAll(
                    "elements => elements.every(element => element.scrollWidth <= element.clientWidth)"))
                    .as("score-banner short names wrap inside their columns at %s", width).isEqualTo(true);
            boolean fits = (Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth");
            if (!fits) page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/category-pool-release-overflow.png")).setFullPage(true));
            org.assertj.core.api.Assertions.assertThat(fits)
                    .as("native page fits at %s on %s; overflow: %s", width, page.url(), fits ? "none" : page.evaluate("""
                            JSON.stringify([...document.querySelectorAll('body *')].filter(element => {
                                const box=element.getBoundingClientRect(); return box.width>0 && box.right>innerWidth+1;
                            }).slice(0,12).map(element => ({tag:element.tagName,id:element.id,cls:element.className,
                                width:element.getBoundingClientRect().width,text:element.textContent.trim().slice(0,60)})))
                            """)).isTrue();
        }
        page.setViewportSize(previous, 900);
    }
}
