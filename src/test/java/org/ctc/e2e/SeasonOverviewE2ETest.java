package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.UUID;
import org.ctc.TestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class SeasonOverviewE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenSeasonTeams_whenEditOrReplace_thenDialogsHaveChoicesAndRestoreKeyboardFocus() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test-Overview " + suffix);
        var candidate = helper.createTeam("Test-Overview successor " + suffix, "T-OV-NEXT-" + suffix);
        page.navigate(url("/admin/seasons/" + fixture.season().getId()));
        page.locator("#season-setup > summary").click();
        var edit = page.locator(".season-team-edit").first();

        // when
        edit.click();

        // then
        assertThat(page.locator("dialog#seasonTeamModal")).isVisible();
        assertThat(page.locator("#modalRating")).isFocused();

        // when
        page.locator("#modalRating").fill("42");
        page.keyboard().press("Escape");

        // then
        assertThat(page.locator("dialog#seasonTeamModal")).not().isVisible();
        assertThat(edit).isFocused();

        // when
        edit.click();

        // then
        var rating = edit.getAttribute("data-rating");
        assertThat(page.locator("#modalRating")).hasValue(rating == null ? "" : rating);

        // when
        page.keyboard().press("Escape");
        var replace = page.locator(".replace-team-btn").first();
        replace.click();

        // then
        assertThat(page.locator("dialog#replaceTeamModal")).isVisible();
        assertThat(page.locator("#replace-successor option[value='" + candidate.getId() + "']")).hasCount(1);

        // when
        page.locator("#replaceTeamModal button[data-dialog-close]").click();

        // then
        assertThat(page.locator("dialog#replaceTeamModal")).not().isVisible();
        assertThat(replace).isFocused();
    }
}
