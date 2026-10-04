package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.StandingsView;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.DriverRankingService.DriverRanking;
import org.ctc.domain.service.StandingsService.TeamStanding;
import org.ctc.domain.service.StandingsViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
class StandingsWorkspaceE2ETest extends PlaywrightConfig {
    @MockitoBean StandingsViewService viewService;
    private Season season;
    private SeasonPhase phase;
    private SeasonPhaseGroup group;
    private List<TeamStanding> standings;
    private List<DriverRanking> drivers;
    private boolean showGroup;
    private boolean showBuchholz;

    @BeforeEach
    void setUp() {
        setupPage();
        season = new Season("Test-Standings workspace");
        season.setId(UUID.randomUUID());
        phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
        phase.setId(UUID.randomUUID());
        phase.setLabel("Test regular phase");
        group = new SeasonPhaseGroup(phase, "Test group A", 0);
        group.setId(UUID.randomUUID());
        var alpha = new Team("Test Alpha Racing", "T-ALPHA");
        alpha.setId(UUID.randomUUID());
        var bravo = new Team("Test Bravo Racing", "T-BRAVO");
        bravo.setId(UUID.randomUUID());
        var first = new TeamStanding(bravo);
        first.addWin();
        first.addMatchPoints(12);
        first.setBuchholz(15);
        first.setGroup(group);
        var second = new TeamStanding(alpha);
        second.addWin();
        second.addLoss();
        second.addMatchPoints(3);
        second.setBuchholz(4);
        second.setGroup(group);
        standings = List.of(first, second);
        drivers = new ArrayList<>();
        for (int index = 26; index >= 0; index--) {
            var driver = new Driver("Test_Standings_" + String.format("%02d", index), "Test driver");
            driver.setId(UUID.randomUUID());
            var ranking = new DriverRanking(driver, index == 0 ? null : alpha);
            var result = new RaceResult(null, driver, index % 12 + 1, 1, false);
            result.setPointsTotal(index + 1);
            ranking.addResult(result);
            if (index == 26) ranking.markGuestAppearance();
            drivers.add(ranking);
        }
        when(viewService.buildView(nullable(UUID.class), nullable(UUID.class), nullable(String.class)))
                .thenAnswer(invocation -> {
                    boolean alltime = "alltime".equals(invocation.getArgument(2));
                    return new StandingsView(List.of(season), alltime, alltime ? null : season,
                            alltime ? "alltime" : season.getId().toString(), List.of(phase), alltime ? null : phase,
                            List.of(group), invocation.getArgument(1), standings, drivers,
                            !alltime, showGroup, showGroup, showBuchholz);
                });
    }

    @AfterEach
    void tearDown() { teardownPage(); }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void givenOptionalColumns_whenSortByPlayedAndPoints_thenCorrectValuesAndOriginalRanksRemain(boolean groups, boolean buchholz) {
        showGroup = groups;
        showBuchholz = buchholz;
        page.navigate(url("/admin/standings?phase=" + phase.getId()));
        var table = page.locator("#standingsTable");
        var played = table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Played"));
        played.focus();
        page.keyboard().press("Enter");
        assertThat(table.locator("tbody tr").first()).containsText("T-BRAVO");
        assertThat(table.locator("tbody tr").first().locator("td").first()).hasText("1");
        table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Points")).click();
        assertThat(table.locator("tbody tr").first()).containsText("T-ALPHA");
        assertThat(table.locator("tbody tr").first().locator("td").first()).hasText("2");
        assertThat(table.locator("th[aria-sort='ascending']")).containsText("Pts");
        page.getByLabel("Search teams", new Page.GetByLabelOptions().setExact(true)).fill("t-alpha");
        assertThat(table.locator("tbody tr:visible")).hasCount(1);
        assertThat(table.locator("tbody tr:visible td").first()).hasText("2");
        assertThat(page.locator("#driverRankingTable tbody tr:visible")).hasCount(20);
        if (buchholz) {
            table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Buchholz")).click();
            assertThat(table.locator("th[aria-sort='ascending']")).containsText("Buchholz");
        }
    }

    @Test
    void givenSortedDrivers_whenPageSearchAndReset_thenOrderingAndRankingStayConsistent() {
        page.navigate(url("/admin/standings?phase=" + phase.getId()));
        var table = page.locator("#driverRankingTable");
        var rows = table.locator("tbody tr:visible");
        table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Driver")).click();
        assertThat(rows.first()).containsText("Test_Standings_00");
        assertThat(rows.first().locator("td").first()).hasText("27");
        page.locator("#nextPage").click();
        assertThat(rows).hasCount(7);
        assertThat(rows.first()).containsText("Test_Standings_20");
        assertThat(rows.first().locator("td").first()).hasText("7");
        page.getByLabel("Search drivers", new Page.GetByLabelOptions().setExact(true)).fill("Test_Standings_26");
        assertThat(rows).hasCount(1);
        assertThat(rows.first().locator("td").first()).hasText("1");
        assertThat(rows.locator(".guest-marker")).isVisible();
        page.getByLabel("Search drivers", new Page.GetByLabelOptions().setExact(true)).fill("missing-driver");
        assertThat(page.locator("#noResults")).isVisible();
        var root = page.locator("[data-admin-list]").last();
        root.locator("[data-list-reset]").click();
        assertThat(page.getByLabel("Search drivers", new Page.GetByLabelOptions().setExact(true))).isFocused();
        assertThat(rows.first()).containsText("Test_Standings_00");
        assertThat(page.locator("#pageInfo")).hasText("Page 1 of 2");
        table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Average Points")).click();
        assertThat(rows.first()).containsText("Test_Standings_00");
        page.setViewportSize(390, 768);
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
    }

    @Test
    void givenEqualRoundedAverages_whenSort_thenUnderlyingValuesDetermineTheOrder() {
        var higher = averageRanking("Test_Higher_average", 43, 21);
        var lower = averageRanking("Test_Lower_average", 10, 5);
        drivers = List.of(higher, lower);
        page.navigate(url("/admin/standings?phase=" + phase.getId()));
        var table = page.locator("#driverRankingTable");
        var averages = table.locator("td[data-sort-value]");
        assertThat(averages.first()).hasText(averages.last().innerText());
        table.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Sort by Average Points")).click();
        assertThat(table.locator("tbody tr").first()).containsText("Test_Lower_average");
        assertThat(table.locator("tbody tr").first().locator("td").first()).hasText("2");
    }

    private DriverRanking averageRanking(String name, int points, int races) {
        var driver = new Driver(name, "Test average");
        driver.setId(UUID.randomUUID());
        var ranking = new DriverRanking(driver, null);
        for (int index = 0; index < races; index++) {
            var result = new RaceResult(null, driver, 1, 1, false);
            result.setPointsTotal(index == 0 ? points : 0);
            ranking.addResult(result);
        }
        return ranking;
    }

    @Test
    void givenNoJavaScript_whenChooseAlltimeAndGroup_thenNativeNavigationAndAllRowsRemainAvailable() {
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = context.newPage();
            nativePage.navigate(url("/admin/standings?phase=" + phase.getId()));
            assertThat(nativePage.locator("#driverRankingTable tbody tr:visible")).hasCount(27);
            assertThat(nativePage.locator("[data-list-tools]").first()).not().isVisible();
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Test group A").setExact(true)).click();
            assertThat(nativePage).hasURL(url("/admin/standings?phase=" + phase.getId() + "&group=" + group.getId()));
            assertThat(nativePage.locator("nav[aria-label='Standings groups'] [aria-current='page']")).hasText("Test group A");
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("All-time standings").setExact(true)).click();
            nativePage.getByLabel("Season", new Page.GetByLabelOptions().setExact(true)).selectOption("alltime");
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show standings").setExact(true)).click();
            assertThat(nativePage).hasURL(url("/admin/standings?seasonId=alltime"));
            assertThat(nativePage.getByLabel("Season", new Page.GetByLabelOptions().setExact(true))).hasValue("alltime");
            assertThat(nativePage.locator("#driverRankingTable tbody tr:visible")).hasCount(27);
            assertThat(nativePage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Team standings, all-time").setExact(true))).isVisible();
            nativePage.getByLabel("Season", new Page.GetByLabelOptions().setExact(true)).selectOption(season.getId().toString());
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show standings").setExact(true)).click();
            assertThat(nativePage).hasURL(url("/admin/standings?seasonId=" + season.getId()));
            assertThat(nativePage.locator("nav[aria-label='Standings phases'] [aria-current='page']")).hasText("Test regular phase");
        }
    }
}
