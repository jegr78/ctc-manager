package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.TestHelper;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.MatchdaysPageGenerator;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteSlugService;
import org.ctc.sitegen.SiteSlugger;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.TemplateEngine;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("e2e")
@Import(CategoryCarPresentationE2ETest.PublicFiles.class)
class CategoryCarPresentationE2ETest extends PlaywrightConfig {
    static final Path PUBLIC_ROOT = SitegenTestDir.create("category-display");
    static final String LABEL = ("Gr.3 MR + RR <img src=x onerror='window.injected=1'> & \"choice\" " + "W".repeat(120)).substring(0, 120);
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceService races;
    @Autowired SeasonCarPoolService pools;
    @Autowired MatchdaysPageGenerator generator;
    @Autowired SiteSlugService slugs;
    @Autowired SiteSlugger slugger;
    @Autowired TemplateEngine engine;
    TestHelper.SeasonFixture fixture;
    UUID mr;
    UUID rr;
    UUID track;
    UUID entry;
    Path graphicRoot;
    String publicUrl;

    @BeforeEach
    void setUp() throws Exception {
        setupPage();
        graphicRoot = Path.of(".screenshots/category-car-graphics").resolve(UUID.randomUUID().toString());
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            fixture = helper.createFullSeasonFixture("TCD" + suffix);
            mr = car(suffix, "MR");
            rr = car(suffix, "RR");
            track = tracks.saveAndFlush(new Track("Test display circuit")).getId();
            fixture.season().getTracks().add(tracks.findById(track).orElseThrow());
        });
        tx.executeWithoutResult(status -> {
            pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            entry = pools.saveEntry(fixture.season().getId(), null, LABEL, List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9889)));
            pools.freeze(fixture.season().getId());
        });
        org.assertj.core.api.Assertions.assertThat(races.saveRace(fixture.race().getId(), fixture.matchday().getId(),
                fixture.homeTeam().getId(), fixture.awayTeam().getId(), track, mr, null,
                5, 2, 2, 1, "Full", 1, TimeProgressionMode.MULTIPLIER, 0, "Clear", "Day", "RS, RM", "RM", entry).success()).isTrue();
        tx.executeWithoutResult(status -> {
            var configured = raceRepository.findById(fixture.race().getId()).orElseThrow();
            var draft = new Race();
            draft.setMatchday(configured.getMatchday());
            draft.setMatch(configured.getMatch());
            raceRepository.saveAndFlush(draft);
        });
        copyAssets();
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var context = new GenerationContext(PUBLIC_ROOT, season, slugger.slugify(season.getDisplayLabel()),
                    season.getName(), false, null, slugs.allocate());
            try {
                generator.generateDetails(context, new SiteGeneratorService.GenerationResult());
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
        publicUrl = url("/category-car-public/season/" + slugger.slugify(fixture.season().getDisplayLabel())
                + "/matchday/" + slugger.slugify(fixture.matchday().getLabel()) + ".html");
    }

    @AfterEach
    void cleanUp() throws Exception {
        teardownPage();
        if (fixture != null) {
            tx.executeWithoutResult(status -> {
                helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
                seasons.flush();
                cars.deleteAllById(List.of(mr, rr));
                tracks.deleteById(track);
                teams.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
            });
        }
        if (graphicRoot != null && Files.exists(graphicRoot)) {
            try (var paths = Files.walk(graphicRoot)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenLongEscapedCategoryLabel_whenReadingNativeAndPublicPages_thenKeepContextWithinPhoneAndDesktop(boolean javaScript) {
        // given
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(javaScript))) {
            var editor = nativeContext.newPage();
            var errors = new ArrayList<String>();
            editor.onPageError(errors::add);
            for (int width : List.of(1440, 375)) {
                editor.setViewportSize(width, 900);
                for (var path : List.of("/admin/races/" + fixture.race().getId(),
                        "/admin/races?seasonId=" + fixture.season().getId(),
                        "/admin/races/" + fixture.race().getId() + "/results",
                        "/admin/matches/" + fixture.match().getId(), "/admin/matchdays/" + fixture.matchday().getId())) {
                    // when
                    editor.navigate(url(path));
                    // then
                    assertThat(editor.locator(".pool-entry-label").first()).containsText(LABEL);
                    assertThat(editor.locator("img[src=x], [onerror]")).hasCount(0);
                    org.assertj.core.api.Assertions.assertThat((Boolean) editor.evaluate("document.documentElement.scrollWidth <= innerWidth"))
                            .as("native document fits at %s on %s", width, path).isTrue();
                    if (path.startsWith("/admin/races?")) {
                        var homeLines = editor.locator("tbody tr").first().locator("strong").first().evaluate("""
                                element => {
                                    const range = document.createRange();
                                    range.selectNodeContents(element);
                                    return new Set(Array.from(range.getClientRects()).map(rect => Math.round(rect.y))).size;
                                }
                                """);
                        org.assertj.core.api.Assertions.assertThat(((Number) homeLines).intValue())
                                .as("short home-team name remains readable in the race table at %s", width).isLessThanOrEqualTo(2);
                        if (width == 375) {
                            editor.locator(".table-scroll").focus();
                            editor.keyboard().press("ArrowRight");
                            assertThat(editor.locator(".table-scroll")).not().hasJSProperty("scrollLeft", 0);
                        }
                    }
                    var textFits = editor.locator(".pool-entry-label").first().evaluate("element => element.scrollWidth <= element.clientWidth");
                    org.assertj.core.api.Assertions.assertThat(textFits).as("native label wraps at %s on %s", width, path).isEqualTo(true);
                }
                editor.navigate(publicUrl);
                assertThat(editor.locator(".pool-entry-label")).hasText(LABEL);
                assertThat(editor.locator(".race-card")).hasCount(2);
                var configured = editor.locator(".race-card").filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHas(editor.locator(".pool-entry-label")));
                assertThat(configured).containsText("MR");
                var draft = editor.locator(".race-card").filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasNot(editor.locator(".pool-entry-label")));
                assertThat(draft.locator(".match-meta")).hasCount(0);
                assertThat(editor.locator("img[src=x], [onerror]")).hasCount(0);
                org.assertj.core.api.Assertions.assertThat((Boolean) editor.evaluate("document.documentElement.scrollWidth <= innerWidth"))
                        .as("generated public document fits at %s", width).isTrue();
            }
            // when: use keyboard navigation and keep the saved context after native validation failure.
            editor.navigate(url("/admin/races/" + fixture.race().getId()));
            editor.locator("a[href='/admin/races/" + fixture.race().getId() + "/edit']").first().focus();
            editor.keyboard().press("Enter");
            String editUrl = url("/admin/races/" + fixture.race().getId() + "/edit");
            assertThat(editor).hasURL(editUrl);
            var response = nativeContext.request().post(url("/admin/races/save"), RequestOptions.create().setMaxRedirects(0)
                    .setForm(FormData.create().set("lobbyRulesProfile", "LEGACY").set("startType", "GRID_FALSE_START").set("slipstream", "REAL").set("id", fixture.race().getId().toString())
                            .set("matchdayId", fixture.matchday().getId().toString()).set("homeTeamId", fixture.homeTeam().getId().toString())
                            .set("awayTeamId", fixture.awayTeam().getId().toString()).set("carId", mr.toString())
                            .set("trackId", track.toString()).set("carPoolEntryId", entry.toString()).set("numberOfLaps", "bad-laps")));
            org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(302);
            editor.navigate(editUrl);
            // then
            assertThat(editor.locator("#numberOfLaps")).hasValue("bad-laps");
            assertThat(editor.locator("#carPoolEntryId option:checked")).hasText(LABEL);
            assertThat(editor.locator("img[src=x], [onerror]")).hasCount(0);
            org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
        }
    }

    @Test
    void givenLongCategoryContext_whenGeneratingRealSettingsPng_thenKeepBothNamesAndAllRowsInsideTheGraphic() throws Exception {
        // given
        var standings = mock(StandingsService.class);
        when(standings.calculateStandings(any(), any())).thenReturn(List.of());
        var graphic = new InspectableSettings(engine, standings, mock(PlayoffSeedRepository.class), graphicRoot.toString());
        Path cards = graphicRoot.resolve("team-cards").resolve(fixture.season().getId().toString());
        Files.createDirectories(cards);
        for (String team : List.of(fixture.homeTeam().getShortName(), fixture.awayTeam().getShortName())) {
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", cards.resolve(team + ".png").toFile());
        }
        // when
        tx.executeWithoutResult(status -> {
            try {
                graphic.generateSettings(raceRepository.findById(fixture.race().getId()).orElseThrow());
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
        Path png = graphicRoot.resolve("races").resolve(fixture.race().getId().toString()).resolve("settings.png");
        // then
        var image = ImageIO.read(png.toFile());
        org.assertj.core.api.Assertions.assertThat(image.getWidth()).isEqualTo(1920);
        org.assertj.core.api.Assertions.assertThat(image.getHeight()).isEqualTo(1080);
        Files.copy(png, Path.of(".screenshots/category-car-settings.png"), StandardCopyOption.REPLACE_EXISTING);
        page.setViewportSize(1920, 1080);
        page.setContent(graphic.html);
        assertThat(page.locator(".setting-row").first()).containsText("MR");
        assertThat(page.locator(".pool-entry-label")).containsText(LABEL);
        assertThat(page.locator("img[src=x], [onerror]")).hasCount(0);
        org.assertj.core.api.Assertions.assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= 1920")).isTrue();
        var pool = page.locator(".pool-entry-label").boundingBox();
        var next = page.locator(".setting-row").nth(1).boundingBox();
        org.assertj.core.api.Assertions.assertThat(pool.y + pool.height).isLessThanOrEqualTo(next.y);
        org.assertj.core.api.Assertions.assertThat(page.locator(".pool-entry-label")
                .evaluate("element => element.scrollWidth <= element.clientWidth")).isEqualTo(true);
    }

    private UUID car(String suffix, String drive) {
        var car = new Car("Test display " + suffix, drive);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.valueOf(drive));
        car.setModelYear(9889);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car).getId();
    }

    private void copyAssets() throws Exception {
        Path source = new ClassPathResource("static/site").getFile().toPath();
        Path destination = PUBLIC_ROOT.resolve("assets");
        try (var paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static class InspectableSettings extends SettingsGraphicService {
        String html;
        InspectableSettings(TemplateEngine engine, StandingsService standings, PlayoffSeedRepository seeds, String output) {
            super(engine, standings, seeds, output);
        }
        @Override
        protected void renderScreenshot(String content, Path output) throws IOException {
            html = content;
            super.renderScreenshot(content, output);
        }
    }

    @TestConfiguration
    static class PublicFiles implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/category-car-public/**").addResourceLocations(PUBLIC_ROOT.toUri().toString());
        }
    }
}
