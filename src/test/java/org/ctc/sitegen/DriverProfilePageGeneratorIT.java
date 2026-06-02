package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.sitegen.model.GenerationContext;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Phase 62 Plan 4 — phase-aware driver-profile page tests.
 *
 * <p>Verifies the rewritten {@link DriverProfilePageGenerator} + {@code templates/site/driver-profile.html}
 * against:
 * <ul>
 *   <li>D-15: per-phase results sectioning visible only when driver's season has &ge;2 phases.</li>
 *   <li>SC4 byte-identity for single-REGULAR-LEAGUE seasons (Season 2026 ADR_Driver01 fixture).</li>
 *   <li>D-16: single driver-profile URL — no per-phase URL forks
 *       ({@code driver-profile-regular.html} / {@code driver-profile-playoff.html} must NOT exist).</li>
 *   <li>Per-phase heading order: REGULAR &rarr; PLAYOFF &rarr; PLACEMENT (LinkedHashMap insertion).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverProfilePageGeneratorIT {

    private static final List<String> PER_PHASE_HEADINGS = List.of(
            "Regular Season Results", "Playoff Results", "Placement Phase Results");

    static final Path tempDir = SitegenTestDir.create("driver-profile");

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;
    @Autowired private DriverProfilePageGenerator driverProfilePageGenerator;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private SiteSlugger siteSlugger;
    @Autowired private DriverRepository driverRepository;
    @Autowired private RaceLineupRepository raceLineupRepository;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp() {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");

        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .locations("classpath:db/migration")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        testDataService.seed();
        try {
            siteGeneratorService.generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * D-15 / SC4: a driver in a single-REGULAR-LEAGUE season's profile MUST NOT contain any of the
     * per-phase result headings. Season 2026 ADR_Driver01 (PSN slug "adr-driver01") is the
     * canonical single-LEAGUE driver fixture.
     */
    @Test
    void givenLeagueOnlySeasonDriver_whenGenerate_thenNoPhaseHeadings() throws IOException {
        Path driverProfile = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("driver").resolve("adr-driver01.html");
        assertThat(driverProfile).exists();
        String html = Files.readString(driverProfile);
        for (String heading : PER_PHASE_HEADINGS) {
            assertThat(html).as("LEAGUE-only driver profile must not contain '%s'", heading)
                    .doesNotContain(heading);
        }
    }

    /**
     * A pure guest — a driver who appears only in a RaceLineup for the season with no
     * SeasonDriver row — must still get a public driver-profile page (D-05). Driven directly
     * because the full SiteGeneratorService filters out "Test" seasons from the public site.
     */
    @Test
    @Transactional(readOnly = true)
    void givenPureGuestDriver_whenGenerate_thenProfilePageExists() throws IOException {
        // given — Test-Season 2026 (year=2026, number=99) seeded with pure guest Test_Guest_1
        var season = seasonRepository.findByYearAndNumber(2026, 99).getFirst();
        var slug = siteSlugger.slugify(season.getDisplayLabel());
        var ctx = new GenerationContext(tempDir, season, slug, season.getName(), false, null);
        var result = new SiteGeneratorService.GenerationResult();

        // when
        driverProfilePageGenerator.generate(ctx, result);

        // then — a profile page exists for the lineup-only guest
        Path driverProfile = tempDir.resolve("season").resolve(slug)
                .resolve("driver").resolve(siteSlugger.slugify("Test_Guest_1") + ".html");
        assertThat(driverProfile).exists();
        String html = Files.readString(driverProfile);
        assertThat(html).contains("Test_Guest_1");
    }

    /**
     * MARK-06: a guest race on the public driver-profile is marked with the star glyph and an
     * inline "as guest for &lt;SubTeamName&gt;" sub-label naming the actual fielding sub-team.
     */
    @Test
    @Transactional(readOnly = true)
    void givenPureGuestDriver_whenGenerate_thenGuestRaceMarkedWithStarAndSubLabel() throws IOException {
        // given
        var season = seasonRepository.findByYearAndNumber(2026, 99).getFirst();
        var slug = siteSlugger.slugify(season.getDisplayLabel());
        var ctx = new GenerationContext(tempDir, season, slug, season.getName(), false, null);
        var genResult = new SiteGeneratorService.GenerationResult();
        var guest = driverRepository.findByPsnId("Test_Guest_1").orElseThrow();
        // The actual fielding sub-team names, scoped to the season (same source the generator uses).
        var guestSubTeams = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId()).stream()
                .filter(RaceLineup::isGuest)
                .filter(rl -> rl.getDriver().getId().equals(guest.getId()))
                .map(rl -> rl.getTeam().getShortName())
                .distinct().toList();

        // when
        driverProfilePageGenerator.generate(ctx, genResult);

        // then — star glyph + inline sub-label naming the actual fielding sub-team
        Path profile = tempDir.resolve("season").resolve(slug)
                .resolve("driver").resolve(siteSlugger.slugify("Test_Guest_1") + ".html");
        String html = Files.readString(profile);
        assertThat(html).contains("guest-marker");
        assertThat(html).contains("&#x2605;");
        assertThat(html).contains("as guest for");
        assertThat(guestSubTeams).isNotEmpty();
        assertThat(guestSubTeams).anyMatch(html::contains);
    }

    /**
     * SC4 byte-identity: the rendered driver-profile.html for a single-REGULAR-LEAGUE-season
     * driver MUST equal the captured baseline byte-for-byte.
     */
    @Test
    void givenLeagueOnlySeasonDriver_whenGenerate_thenLegacyByteIdentical() throws IOException {
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-driver-profile.html");
        Path generated = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("driver").resolve("adr-driver01.html");
        assertThat(generated).exists();
        assertThat(Files.readString(generated)).isEqualTo(Files.readString(baseline));
    }

    /**
     * D-15: a driver who participated in REGULAR + PLAYOFF (Season 2023 — ADR_Driver01 was assigned
     * via assignSeasonDrivers and the playoff results were created via createPlayoffRaces for
     * the 2023 semifinal Top-4 teams) MUST see per-phase headings on the page.
     *
     * <p>2023 has no PLACEMENT phase, so only REGULAR + PLAYOFF headings are expected.
     */
    @Test
    void givenMultiPhaseSeasonDriver_whenGenerate_thenPerPhaseHeadingsVisible() throws IOException {
        Path driverProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("driver").resolve("adr-driver01.html");
        assertThat(driverProfile).exists();
        Document doc = Jsoup.parse(Files.readString(driverProfile));
        var headingTexts = doc.select("h2.section-title, h3.section-title").stream()
                .map(e -> e.text())
                .toList();
        assertThat(headingTexts).contains("Regular Season Results");
        assertThat(headingTexts).contains("Playoff Results");
        // 2023 has no PLACEMENT phase
        assertThat(headingTexts).doesNotContain("Placement Phase Results");
    }

    /**
     * D-15: under each per-phase heading, only results from that phase should appear. The legacy
     * "Race History" section MUST disappear in favor of the per-phase sections.
     */
    @Test
    void givenMultiPhaseSeasonDriver_whenGenerate_thenResultsSectionedByPhase() throws IOException {
        Path driverProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("driver").resolve("adr-driver01.html");
        Document doc = Jsoup.parse(Files.readString(driverProfile));
        // Combined "Race History — ..." legacy heading must NOT appear when phase breakdown is on
        boolean legacyHeadingPresent = doc.select("h2.section-title, h3.section-title").stream()
                .anyMatch(h -> h.text().startsWith("Race History"));
        assertThat(legacyHeadingPresent)
                .as("Multi-phase driver-profile must NOT render the legacy 'Race History — ...' heading")
                .isFalse();
        // At least one results table should be present (per-phase). Each per-phase section has its
        // own results table — pick the first one as a smoke check.
        assertThat(doc.select("table tbody tr").size())
                .as("Per-phase results sectioning must still emit at least one result row")
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * D-16: single driver-profile URL preserved per (season, entity) — no per-phase URL forks. The
     * generator MUST NOT emit {@code driver-profile-regular.html} or {@code driver-profile-playoff.html}
     * (or any other phase-suffixed variant) for any season.
     */
    @Test
    void givenMultiPhaseSeasonDriver_whenGenerate_thenSingleProfileUrl() {
        Path driverDir = tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver");
        assertThat(driverDir.resolve("adr-driver01-regular.html")).doesNotExist();
        assertThat(driverDir.resolve("adr-driver01-playoff.html")).doesNotExist();
        assertThat(driverDir.resolve("adr-driver01-placement.html")).doesNotExist();
        // Also verify the stable canonical file IS present
        assertThat(driverDir.resolve("adr-driver01.html")).exists();
    }

    /**
     * D-15: when multiple per-phase sections are emitted, they must appear in canonical order:
     * REGULAR &rarr; PLAYOFF &rarr; PLACEMENT (LinkedHashMap insertion order from the helper).
     */
    @Test
    void givenMultiPhaseSeasonDriver_whenGenerate_thenPhaseHeadingsOrderedRegularPlayoffPlacement() throws IOException {
        Path driverProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("driver").resolve("adr-driver01.html");
        Document doc = Jsoup.parse(Files.readString(driverProfile));
        var perPhaseHeadings = doc.select("h2.section-title, h3.section-title").stream()
                .map(e -> e.text())
                .filter(PER_PHASE_HEADINGS::contains)
                .toList();
        // Expected order: REGULAR before PLAYOFF (2023 has no PLACEMENT)
        assertThat(perPhaseHeadings).containsExactly("Regular Season Results", "Playoff Results");
    }
}
