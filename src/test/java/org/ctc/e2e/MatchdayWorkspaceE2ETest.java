package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchdayWorkspaceE2ETest extends PlaywrightConfig {

    @Autowired
    TestHelper helper;

    @Autowired
    MatchRepository matchRepository;

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void givenTwoSeasons_whenNavigateAndSwitch_thenOnlyTheChosenSeasonIsShown() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var first = helper.createFullSeasonFixture("Test-UI First " + suffix);
        var second = helper.createFullSeasonFixture("Test-UI Second " + suffix);
        page.navigate(url("/admin/races/" + first.race().getId()));

        // when
        page.locator(".sidebar a[href='/admin/matchdays?seasonId=" + first.season().getId() + "']").click();

        // then
        assertThat(page.locator("#workspace-season")).hasValue(first.season().getId().toString());
        assertThat(page.locator("table tbody")).containsText(first.matchday().getLabel());
        assertThat(page.locator("table tbody")).not().containsText(second.matchday().getLabel());

        // when
        page.locator("#workspace-season").selectOption(second.season().getId().toString());
        page.locator(".season-context-switch button").click();

        // then
        assertThat(page).hasURL(url("/admin/matchdays?seasonId=" + second.season().getId()));
        assertThat(page.locator("table tbody")).containsText(second.matchday().getLabel());
        assertThat(page.locator("table tbody")).not().containsText(first.matchday().getLabel());

        // when
        page.locator(".sidebar a[href='/admin/races?seasonId=" + second.season().getId() + "']").click();

        // then
        assertThat(page.locator("#racesTable tbody")).containsText(second.homeTeam().getShortName());
        assertThat(page.locator("#racesTable tbody")).not().containsText(first.homeTeam().getShortName());
    }

    @Test
    void givenOpenMatchAndBye_whenFilterAndSearch_thenOnlyMatchingPairingsRemainVisible() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var season = helper.createSeason("Test-UI Workspace " + suffix);
        var matchday = helper.createMatchdayInRegularPhase(season, "Test-UI Matchday", 1);
        var home = helper.createTeam("Test-UI Home " + suffix, "T-UI-HOM-" + suffix);
        var away = helper.createTeam("Test-UI Away " + suffix, "T-UI-AWY-" + suffix);
        var byeTeam = helper.createTeam("Test-UI Bye " + suffix, "T-UI-BYE-" + suffix);
        helper.createMatch(matchday, home, away);
        var bye = helper.createMatch(matchday, byeTeam, null);
        bye.setBye(true);
        matchRepository.save(bye);
        page.navigate(url("/admin/matchdays/" + matchday.getId()));

        // when
        page.locator("#matchday-status").selectOption("open");

        // then
        assertThat(page.locator(".match-row:visible")).hasCount(1);
        assertThat(page.locator(".match-row:visible")).containsText(home.getShortName());
        assertThat(page.locator("[data-testid='match-row-detail-link']:visible")).hasCount(1);

        // when
        page.locator("#matchday-search").fill("no-such-team");

        // then
        assertThat(page.locator(".match-row:visible")).hasCount(0);
        assertThat(page.locator("#matchday-filter-status")).hasText("No matches meet your filters.");

        // when
        page.locator("#matchday-search").fill("");
        page.locator("#matchday-status").selectOption("bye");

        // then
        assertThat(page.locator(".match-row:visible")).hasCount(1);
        assertThat(page.locator(".match-row:visible")).containsText(byeTeam.getShortName());

        // when
        page.locator("#matchday-status").selectOption("all");

        // then
        assertThat(page.locator(".match-row:visible")).hasCount(2);
    }
}
