package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class SeasonConfigurationEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasons;
    @Autowired RaceScoringRepository scorings;
    @Autowired SeasonPhaseRepository phases;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenExistingSeason_whenInvalidNameIsCorrected_thenCatalogsAndSubmittedDetailsRemainAndSaveSucceeds() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Season Editor " + UUID.randomUUID().toString().substring(0, 8));
        page.navigate(url("/admin/seasons/" + fixture.season().getId() + "/edit"));

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#name").fill("");
        page.locator("#description").fill("Updated season details");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#name")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#description")).hasValue("Updated season details");
        assertThat(page.locator("#season-teams")).containsText(fixture.homeTeam().getShortName());
        assertThat(page.locator("label[for='availableCarsFilter']")).hasText("Search available cars");
        assertThat(page.locator("#carPool")).isVisible();
        assertThat(page.locator("#trackPool")).isVisible();

        // when
        page.locator("#name").fill(fixture.season().getName());
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url("/admin/seasons"));
        assertEquals("Updated season details", seasons.findById(fixture.season().getId()).orElseThrow().getDescription());
    }

    @Test
    void givenExistingPhase_whenInvalidRaceCountIsCorrected_thenPhaseTypeScoringAndDatesRemainAndSaveSucceeds() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test Phase Editor " + suffix);
        var phaseId = fixture.matchday().getPhase().getId();
        var scoring = scorings.save(new RaceScoring("Test Phase Editor Points " + suffix, "20,17,14", null, 0));
        var phaseUrl = "/admin/seasons/" + fixture.season().getId() + "/phases/" + phaseId;
        page.navigate(url(phaseUrl + "/edit"));
        page.locator("#raceScoringId").selectOption(scoring.getId().toString());
        page.locator("#startDate").fill("2028-01-01");
        page.locator("#endDate").fill("2028-06-30");

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#legs").evaluate("el => el.type = 'text'");
        page.locator("#legs").fill("invalid");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#legs-error")).isVisible();
        assertThat(page.locator("#phaseType")).isDisabled();
        assertThat(page.locator("input[name='phaseType']")).hasValue("REGULAR");
        assertThat(page.locator("#raceScoringId")).hasValue(scoring.getId().toString());
        assertThat(page.locator("#startDate")).hasValue("2028-01-01");
        assertThat(page.locator("#endDate")).hasValue("2028-06-30");

        // when
        page.locator("#legs").fill("2");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url(phaseUrl));
        page.navigate(url(phaseUrl + "/edit"));
        assertThat(page.locator("#legs")).hasValue("2");
        assertThat(page.locator("#raceScoringId")).hasValue(scoring.getId().toString());
        assertThat(page.locator("#startDate")).hasValue("2028-01-01");
        assertThat(page.locator("#endDate")).hasValue("2028-06-30");
    }

    @Test
    void givenGroupForm_whenBlankNameIsCorrected_thenSortOrderRemainsAndGroupIsCreated() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Group Editor " + UUID.randomUUID().toString().substring(0, 8));
        var phase = phases.findById(fixture.matchday().getPhase().getId()).orElseThrow();
        phase.setLayout(PhaseLayout.GROUPS);
        phases.save(phase);
        var phaseUrl = "/admin/seasons/" + fixture.season().getId() + "/phases/" + phase.getId();
        page.navigate(url(phaseUrl + "/groups/new"));

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#sortIndex").fill("7");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#sortIndex")).hasValue("7");
        assertThat(page.locator(".back-link")).hasAttribute("href", phaseUrl);

        // when
        page.locator("#name").fill("Test Editor Group");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url(phaseUrl));
        assertThat(page.locator(".season-overview")).containsText("Test Editor Group");
    }
}
