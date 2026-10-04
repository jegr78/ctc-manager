package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Route;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class PowerRankingsEditorE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasonRepository;
    @Autowired SeasonTeamRepository seasonTeamRepository;
    @Autowired TeamRepository teamRepository;
    private Season season;
    private final List<Team> teams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        setupPage();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        season = helper.createSeason("Test-Rankings " + suffix, 2088, 91);
        for (int i = 0; i < 3; i++) {
            var team = helper.createTeam("Test Rankings Team " + i + " " + suffix, "T-PR-" + i + "-" + suffix);
            teams.add(team);
            var assignment = new SeasonTeam(season, team);
            assignment.setRating(1500 - i * 100);
            seasonTeamRepository.save(assignment);
        }
    }

    @AfterEach
    void tearDown() {
        teardownPage();
        if (season != null) seasonRepository.deleteById(season.getId());
        teams.forEach(team -> teamRepository.deleteById(team.getId()));
    }

    @Test
    void givenRatedTeams_whenReorderResetAndSubmitWithEnter_thenFocusRanksAndDownloadOrderStayConsistent() {
        page.navigate(url("/admin/tools/power-rankings"));
        page.locator("#seasonSelect").selectOption("2088|91");
        page.locator("#seasonForm button[type=submit]").click();
        assertThat(page.locator("#seasonSelect")).hasValue("2088|91");
        var first = page.locator("[data-team-id='" + teams.get(0).getId() + "']");
        var second = page.locator("[data-team-id='" + teams.get(1).getId() + "']");
        assertThat(first.locator("[data-rank-up]")).isDisabled();
        second.locator("[data-rank-up]").focus();
        page.keyboard().press("Enter");
        assertThat(second.locator(".ranking-rank")).hasText("1");
        assertThat(second.locator("[data-rank-down]")).isFocused();
        assertThat(page.locator("#ranking-status")).containsText("position 1 of 3");
        page.locator("[data-reset-ranking]").click();
        assertThat(first.locator(".ranking-rank")).hasText("1");
        assertThat(page.locator("[data-reset-ranking]")).isFocused();
        var last = page.locator("[data-team-id='" + teams.get(2).getId() + "']");
        last.scrollIntoViewIfNeeded();
        var handle = first.locator(".ranking-drag-handle").boundingBox();
        var destination = last.boundingBox();
        page.mouse().move(handle.x + handle.width / 2, handle.y + handle.height / 2);
        page.mouse().down();
        page.mouse().move(destination.x + 10, destination.y + destination.height - 5, new Mouse.MoveOptions().setSteps(10));
        page.mouse().up();
        assertThat(first.locator(".ranking-rank")).hasText("3");
        page.setViewportSize(390, 768);
        assertEquals(false, page.evaluate("() => document.documentElement.scrollWidth > innerWidth"));
        page.route("**/admin/tools/power-rankings/download", route -> route.fulfill(new Route.FulfillOptions().setStatus(204)));
        page.locator("#subtitle").fill("Test ranking & final");
        var request = page.waitForRequest("**/admin/tools/power-rankings/download", () -> page.locator("#subtitle").press("Enter"));
        var body = URLDecoder.decode(request.postData(), StandardCharsets.UTF_8);
        var expectedIds = List.of(teams.get(1).getId(), teams.get(2).getId(), teams.get(0).getId());
        assertEquals(expectedIds.stream().map(id -> "teamIds=" + id).toList(),
                List.of(body.split("&")).stream().filter(part -> part.startsWith("teamIds=")).toList());
        assertTrue(body.contains("subtitle=Test ranking & final"));
        assertTrue(body.contains("year=2088&number=91"));
    }

    @Test
    void givenJavaScriptDisabled_whenChooseSeasonAndDownload_thenNativeFormKeepsAllTeamsInRatingOrder() {
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/tools/power-rankings"));
            assertThat(nativePage.locator("#seasonForm")).not().isVisible();
            nativePage.locator("[data-season-link][href$='year=2088&number=91']").click();
            assertThat(nativePage.locator(".ranking-item")).hasCount(3);
            assertThat(nativePage.locator("[data-rank-up]").first()).not().isVisible();
            nativePage.route("**/admin/tools/power-rankings/download", route -> route.fulfill(new Route.FulfillOptions().setStatus(204)));
            var request = nativePage.waitForRequest("**/admin/tools/power-rankings/download", () -> nativePage.locator("#downloadForm button[type=submit]").click());
            var body = URLDecoder.decode(request.postData(), StandardCharsets.UTF_8);
            assertEquals(teams.stream().map(team -> "teamIds=" + team.getId()).toList(),
                    List.of(body.split("&")).stream().filter(part -> part.startsWith("teamIds=")).toList());
        }
    }
}
