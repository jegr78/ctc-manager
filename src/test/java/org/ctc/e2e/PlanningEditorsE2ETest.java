package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class PlanningEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired MatchdayRepository matchdays;
    @Autowired SeasonPhaseRepository phases;
    @Autowired SeasonPhaseGroupRepository groups;
    @Autowired PhaseTeamRepository roster;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenPairingsEditor_whenOverlongWindowIsCorrected_thenDeadlineRemainsAndScheduleSaves() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Planning " + UUID.randomUUID().toString().substring(0, 8));
        var path = "/admin/matchdays/" + fixture.matchday().getId();
        page.navigate(url(path + "/edit-pairings"));
        page.locator("#pickDeadline").fill("2028-05-20T20:00");
        page.locator("#scheduledWeekend").evaluate("el => el.removeAttribute('maxlength')");
        page.locator("#scheduledWeekend").fill("x".repeat(65));

        // when
        page.locator("[data-testid='save-pairings']").click();

        // then
        assertThat(page.locator("#scheduledWeekend-error")).isVisible();
        assertThat(page.locator("#scheduledWeekend")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#pickDeadline")).hasValue("2028-05-20T20:00");

        // when
        page.locator("#scheduledWeekend").fill("22-24 May");
        page.locator("[data-testid='save-pairings']").click();

        // then
        assertThat(page).hasURL(url(path));
        var saved = matchdays.findById(fixture.matchday().getId()).orElseThrow();
        assertEquals(LocalDateTime.of(2028, 5, 20, 20, 0), saved.getPickDeadline());
        assertEquals("22-24 May", saved.getScheduledWeekend());
    }

    @Test
    void givenUnequalGroups_whenGenerationIsRejectedThenGroupChanged_thenInputsRemainAndOnlySelectedGroupIsGenerated() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var season = helper.createSeason("Test Generator Editor " + suffix);
        var phase = phases.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
        phase.setLayout(PhaseLayout.GROUPS);
        phases.save(phase);
        var groupA = groups.save(new SeasonPhaseGroup(phase, "Test Group A", 0));
        var groupB = groups.save(new SeasonPhaseGroup(phase, "Test Group B", 1));
        for (int index = 0; index < 5; index++) {
            var team = helper.createTeam("Test Generator Team " + suffix + index, "TGE" + suffix + index);
            var entry = new PhaseTeam(phase, team);
            entry.setGroup(index < 2 ? groupA : groupB);
            roster.save(entry);
        }
        var existing = helper.createMatchdayInRegularPhase(season, "Test Existing Group B", 1);
        existing.setGroup(groupB);
        matchdays.save(existing);
        page.navigate(url("/admin/seasons/" + season.getId() + "/generate"));
        assertThat(page.locator("#generator-roster")).containsText("2 teams");
        assertThat(page.locator("#numberOfRounds")).hasValue("1");
        page.locator("#groupId").selectOption(groupB.getId().toString());
        assertThat(page.locator("#generator-roster")).containsText("3 teams");
        assertThat(page.locator("#generator-roster")).containsText("round-robin: 3");

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#numberOfRounds").fill("0");
        page.locator("#homeAndAway").check();
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#numberOfRounds-error")).isVisible();
        assertThat(page.locator("#groupId")).hasValue(groupB.getId().toString());
        assertThat(page.locator("#homeAndAway")).isChecked();

        // when
        page.locator("#numberOfRounds").fill("2");
        assertThat(page.locator("#summary")).hasText("Total matchdays: 4");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator(".alert-error")).containsText("already has matchdays");
        assertThat(page.locator("#numberOfRounds")).hasValue("2");
        assertThat(page.locator("#groupId")).hasValue(groupB.getId().toString());
        assertThat(page.locator("#homeAndAway")).isChecked();

        // when
        page.locator("#groupId").selectOption(groupA.getId().toString());
        assertThat(page.locator("#summary")).hasText("Total matchdays: 2");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url("/admin/seasons/" + season.getId() + "/phases/" + phase.getId()));
        assertEquals(2, matchdays.findByGroupId(groupA.getId()).size());
        assertEquals(1, matchdays.findByGroupId(groupB.getId()).size());
    }
}
