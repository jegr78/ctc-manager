package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired MatchRepository matches;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenMatchEdit_whenOverlongRoleIsCorrected_thenTeaserRemainsAndMetadataSaves() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Match Editor " + UUID.randomUUID().toString().substring(0, 8));
        var path = "/admin/matches/" + fixture.match().getId();
        page.navigate(url(path + "/edit"));
        page.locator("#discordTeaser").fill("Test match broadcast preview");
        page.locator("#streamLink").fill("https://example.test/live");
        page.locator("#lobbyHost").fill("Test host");
        page.locator("#streamer").evaluate("el => el.removeAttribute('maxlength')");
        page.locator("#streamer").fill("x".repeat(101));

        // when
        page.locator("[data-testid='match-edit-save']").click();

        // then
        assertThat(page.locator("#streamer-error")).isVisible();
        assertThat(page.locator("#streamer")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#discordTeaser")).hasValue("Test match broadcast preview");
        assertThat(page.locator("#streamLink")).hasValue("https://example.test/live");
        assertThat(page.getByLabel("Forfeiting team")).hasAttribute("aria-describedby", "walkoverTeamId-hint");
        assertThat(page.locator("#walkoverTeamId-hint")).containsText("The selected team forfeits");

        // when
        page.locator("#streamer").fill("Test streamer");
        page.locator("[data-testid='match-edit-save']").click();

        // then
        assertThat(page).hasURL(url(path));
        var saved = matches.findById(fixture.match().getId()).orElseThrow();
        assertEquals("Test match broadcast preview", saved.getDiscordTeaser());
        assertEquals("Test streamer", saved.getStreamer());
        assertEquals("Test host", saved.getLobbyHost());
    }

    @Test
    void givenNewMatch_whenByeIsToggledAndCreated_thenOpponentSelectionReturnsAndByeHasNoOpponent() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Bye Editor " + UUID.randomUUID().toString().substring(0, 8));
        var matchday = helper.createMatchdayInRegularPhase(fixture.season(), "Test Bye Editor Matchday", 1);
        page.setViewportSize(390, 768);
        page.navigate(url("/admin/matches/new?matchdayId=" + matchday.getId()));
        page.locator("#homeTeamId").selectOption(fixture.homeTeam().getId().toString());
        page.locator("#awayTeamId").selectOption(fixture.awayTeam().getId().toString());

        // when
        page.locator("#bye").check();

        // then
        assertThat(page.locator("#awayTeamGroup")).isHidden();
        assertThat(page.locator("#awayTeamId")).isDisabled();

        // when
        page.locator("#bye").uncheck();

        // then
        assertThat(page.locator("#awayTeamId")).isEnabled();
        assertThat(page.locator("#awayTeamId")).hasValue(fixture.awayTeam().getId().toString());

        // when
        page.locator("#bye").check();
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url("/admin/matchdays/" + matchday.getId()));
        var created = matches.findByMatchdayId(matchday.getId()).getFirst();
        assertTrue(created.isBye());
        assertNull(created.getAwayTeam());
        page.navigate(url("/admin/matches/" + created.getId() + "/edit"));
        assertThat(page.locator("#match-decision")).hasCount(0);
    }
}
