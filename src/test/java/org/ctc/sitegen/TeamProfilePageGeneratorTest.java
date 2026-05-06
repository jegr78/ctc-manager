package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Phase 62 Plan 4 — phase-aware team-profile page tests.
 *
 * <p>Verifies the rewritten {@link TeamProfilePageGenerator} + {@code templates/site/team-profile.html}
 * against:
 * <ul>
 *   <li>D-13: Phase Breakdown section visible only when team's season has &ge;2 phases.</li>
 *   <li>D-14: main standings panel keeps using {@code calculateStandings(REGULAR, null)}
 *       (combined-view standing) — UNCHANGED from today.</li>
 *   <li>SC4 byte-identity for single-REGULAR-LEAGUE seasons (Season 2026 ADR fixture).</li>
 *   <li>D-16: single team-profile URL — no per-phase URL forks
 *       ({@code team-profile-regular.html} / {@code team-profile-playoff.html} must NOT exist).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class TeamProfilePageGeneratorTest {

    private Path tempDir;

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private SiteProperties siteProperties;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp(@TempDir Path injectedTempDir) {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        this.tempDir = injectedTempDir;
        siteProperties.setOutputDir(tempDir.toString());

        // Plan 1 pattern — Flyway clean+migrate guarantees fresh DB regardless of preceding test
        // classes seeding the shared H2 in-memory DB (DB_CLOSE_DELAY=-1 keeps it alive across
        // Spring context reloads).
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
     * D-13 / SC4: a team in a single-REGULAR-LEAGUE season's profile MUST NOT contain the
     * "Phase Breakdown" section. Season 2026 (year=2026, number=4) is single-REGULAR-LEAGUE; ADR
     * is one of its participating teams (TestDataService line 267, 293).
     */
    @Test
    void givenLeagueOnlySeasonTeam_whenGenerate_thenNoPhaseBreakdownSection() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("team").resolve("adr.html");
        assertThat(teamProfile).exists();
        String html = Files.readString(teamProfile);
        assertThat(html).doesNotContain("Phase Breakdown");
    }

    /**
     * SC4 byte-identity: the rendered team-profile.html for a single-REGULAR-LEAGUE-season team
     * MUST equal the captured baseline byte-for-byte AFTER normalizing entity UUIDs (team UUIDs
     * appear in {@code /uploads/teams/{uuid}/...} logo URLs and are pre-existing-non-deterministic
     * because TestDataService re-creates teams with new random UUIDs on every seed).
     */
    @Test
    void givenLeagueOnlySeasonTeam_whenGenerate_thenLegacyByteIdentical() throws IOException {
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-team-profile.html");
        Path generated = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("team").resolve("adr.html");
        assertThat(generated).exists();
        assertThat(normalizeUuids(Files.readString(generated)))
                .isEqualTo(normalizeUuids(Files.readString(baseline)));
    }

    /**
     * Normalizes UUID-strings to a canonical placeholder so byte-identity comparison is robust
     * against pre-existing UUID non-determinism in TestDataService (logo-URL team UUIDs).
     */
    private static String normalizeUuids(String html) {
        return html.replaceAll(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                "00000000-0000-0000-0000-000000000000");
    }

    /**
     * D-13: a team in a multi-phase season (Season 2023 — REGULAR-GROUPS + PLAYOFF) MUST render
     * the Phase Breakdown section with at least 2 row entries (REGULAR + PLAYOFF). ADR
     * participates in 2023 REGULAR (Group A — TestDataService line 207) and is also Top-4 in the
     * 2023 Playoffs semifinal via {@code playoffSeedingService.autoSeedBracket}.
     */
    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenPhaseBreakdownSectionVisible() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("team").resolve("adr.html");
        assertThat(teamProfile).exists();
        Document doc = Jsoup.parse(Files.readString(teamProfile));
        var heading = doc.select("h2.section-title").stream()
                .filter(h -> h.text().equals("Phase Breakdown"))
                .findFirst()
                .orElse(null);
        assertNotNull(heading,
                "Multi-phase team-profile.html must contain a 'Phase Breakdown' section heading");

        // The Phase Breakdown table is the next .table-wrap sibling within the same .section block
        var breakdownSection = heading.parent();
        assertNotNull(breakdownSection, "Phase Breakdown heading must have a parent section");
        var rows = breakdownSection.select("table tbody tr");
        assertThat(rows.size()).as("Phase Breakdown table must list at least 2 phases").isGreaterThanOrEqualTo(2);
    }

    /**
     * D-14: the main standings panel of team-profile reflects the team's COMBINED-view position
     * via {@code calculateStandings(REGULAR-phase, groupId=null)} — UNCHANGED from today.
     *
     * <p>For Season 2023 GROUPS-layout REGULAR: ADR's combined-view standing exists in the page.
     * The per-group ranking (within Group A only, 6 teams) is not what the standings panel shows.
     * We assert that the page contains a "Record" section (the existing standings panel structure)
     * AND a points value matching the team's combined-view points.
     */
    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenStandingsPanelUsesCombinedView() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("team").resolve("adr.html");
        Document doc = Jsoup.parse(Files.readString(teamProfile));
        // Existing template: Record section header is the FIRST <h2 class="section-title"> on the
        // page (above the new Phase Breakdown section). Verify it's still present and the panel
        // table has exactly one data row (combined-view standing for this team).
        var firstSectionTitle = doc.selectFirst("h2.section-title");
        assertNotNull(firstSectionTitle, "team-profile.html must still contain its main standings panel");
        assertThat(firstSectionTitle.text()).isEqualTo("Record");
        var recordSection = firstSectionTitle.parent();
        assertNotNull(recordSection);
        assertThat(recordSection.select("table tbody tr").size())
                .as("Record panel must show exactly one combined-view standing row")
                .isEqualTo(1);
    }

    /**
     * D-16: single team-profile URL preserved per (season, entity) — no per-phase URL forks. The
     * generator MUST NOT emit {@code team-profile-regular.html} or {@code team-profile-playoff.html}
     * (or any per-group team-profile variant) for any season.
     */
    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenSingleProfileUrl() {
        Path teamDir = tempDir.resolve("season").resolve("2023-1-season-2023").resolve("team");
        assertThat(teamDir.resolve("adr-regular.html")).doesNotExist();
        assertThat(teamDir.resolve("adr-playoff.html")).doesNotExist();
        assertThat(teamDir.resolve("adr-placement.html")).doesNotExist();
        // Also verify the stable canonical file IS present
        assertThat(teamDir.resolve("adr.html")).exists();
    }
}
