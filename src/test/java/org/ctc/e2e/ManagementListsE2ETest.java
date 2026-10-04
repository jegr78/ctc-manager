package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class ManagementListsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired DriverRepository driverRepository;
    @Autowired SeasonRepository seasonRepository;
    @Autowired TeamRepository teamRepository;

    private final List<Driver> drivers = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Season> seasons = new ArrayList<>();

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() {
        teardownPage();
        seasons.forEach(helper::deleteSeasonCascade);
        driverRepository.deleteAll(drivers);
        for (int index = teams.size() - 1; index >= 0; index--) teamRepository.delete(teams.get(index));
    }

    @Test
    void givenDriversAcrossPages_whenFilterSortAndReset_thenResultsAndKeyboardFocusStayConsistent() {
        var prefix = "Test_Directory_" + UUID.randomUUID().toString().substring(0, 8);
        for (int index = 0; index < 27; index++) {
            var driver = helper.createDriver(prefix + "_" + String.format("%02d", index),
                    "Test Directory " + String.format("%02d", 26 - index));
            driver.setActive(index != 26);
            drivers.add(driverRepository.save(driver));
        }
        page.navigate(url("/admin/drivers"));
        var search = page.locator("[data-list-search]");
        search.fill("  " + prefix.toUpperCase() + "  ");
        assertThat(page.locator(".driver-row:visible")).hasCount(20);
        assertThat(page.locator("[data-list-count]")).hasText("27 drivers. Showing 1 to 20.");

        page.locator("#nextPage").click();
        assertThat(page.locator(".driver-row:visible")).hasCount(7);
        var nicknameSort = page.locator("th[data-col='1'] button");
        nicknameSort.focus();
        page.keyboard().press("Enter");
        assertThat(page.locator("th[data-col='1']")).hasAttribute("aria-sort", "ascending");
        assertThat(page.locator("#pageInfo")).hasText("Page 1 of 2");
        assertThat(page.locator(".driver-row:visible").first()).containsText(prefix + "_26");

        page.locator("[data-list-status]").selectOption("inactive");
        assertThat(page.locator(".driver-row:visible")).hasCount(1);
        search.fill(prefix + "_00");
        assertThat(page.locator("[data-list-empty]")).isVisible();
        assertThat(page.locator(".driver-row:visible")).hasCount(0);
        page.locator("[data-list-reset]").focus();
        page.keyboard().press("Enter");
        assertThat(search).isFocused();
        assertThat(search).hasValue("");
        assertThat(page.locator("[data-list-status]")).hasValue("all");
        assertThat(page.locator(".driver-row:visible")).hasCount(20);
        assertThat(page.locator("[data-list-empty]")).not().isVisible();
        search.fill(prefix);
        assertThat(page.locator(".driver-row:visible").first()).containsText(prefix + "_26");
        assertThat(page.locator("th[data-col='1']")).hasAttribute("aria-sort", "ascending");
    }

    @Test
    void givenSubTeam_whenSearch_thenItsParentGroupAndActionsRemainVisible() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var parent = helper.createTeam("Test Directory Parent " + suffix, "T-DIR-P-" + suffix);
        teams.add(parent);
        teams.add(helper.createSubTeam("Test Directory Sub " + suffix, "T-DIR-S-" + suffix, parent));
        page.navigate(url("/admin/teams"));
        page.locator("[data-list-search]").fill("t-dir-s-" + suffix);
        var group = page.locator("tbody[data-list-entry]:visible");
        assertThat(group).hasCount(1);
        assertThat(group).containsText(parent.getShortName());
        assertThat(group.locator(".sub-team-row")).hasCount(1);
        assertThat(group.locator("a[href='/admin/teams/" + parent.getId() + "/edit']")).isVisible();
        assertThat(page.locator("[data-list-count]")).hasText("1 team group");
        page.locator("[data-list-search]").fill("no-match-" + suffix);
        assertThat(page.locator("[data-list-empty]")).isVisible();
        page.locator("[data-list-reset]").click();
        assertThat(page.locator("[data-list-search]")).isFocused();
        assertThat(page.locator("a[href='/admin/teams/" + parent.getId() + "']")).isVisible();
    }

    @Test
    void givenActiveAndInactiveSeasons_whenCombineFilters_thenOnlyMatchingSeasonIsShown() {
        var prefix = "Test-Directory " + UUID.randomUUID().toString().substring(0, 8);
        var active = helper.createSeason(prefix + " Active");
        active.setActive(true);
        seasons.add(seasonRepository.save(active));
        var inactive = helper.createSeason(prefix + " Inactive");
        inactive.setActive(false);
        seasons.add(seasonRepository.save(inactive));
        page.navigate(url("/admin/seasons"));
        page.locator("[data-list-search]").fill(prefix);
        page.locator("[data-list-status]").selectOption("inactive");
        var row = page.locator("[data-list-entry]:visible");
        assertThat(row).hasCount(1);
        assertThat(row).containsText(inactive.getName());
        assertThat(page.locator("[data-list-count]")).hasText("1 season");
        page.locator("[data-list-status]").selectOption("active");
        assertThat(row).containsText(active.getName());
        page.locator("[data-list-reset]").click();
        assertThat(page.locator("[data-list-search]")).hasValue("");
        assertThat(page.locator("[data-list-status]")).hasValue("all");
    }
}
