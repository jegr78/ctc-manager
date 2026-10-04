package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class EntityEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired DriverRepository driverRepository;
    @Autowired TeamRepository teamRepository;
    private Driver driver;
    private Team team;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() {
        teardownPage();
        if (driver != null) driverRepository.deleteById(driver.getId());
        if (team != null) teamRepository.deleteById(team.getId());
    }

    @Test
    void givenDriver_whenRemoveAliasAndAddAnother_thenLabelsFocusAndSavedAliasesStayConsistent() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        driver = helper.createDriver("Test_Editor_" + suffix, "Test Editor " + suffix);
        page.navigate(url("/admin/drivers/" + driver.getId() + "/edit"));
        var add = page.locator("[data-add-alias]");
        add.click();
        assertThat(page.locator("#alias-0")).isFocused();
        page.locator("#alias-0").fill("Test_Old_First_" + suffix);
        add.click();
        page.locator("#alias-1").fill("Test_Old_Second_" + suffix);
        page.locator("[data-remove-alias]").first().click();
        assertThat(page.locator("#alias-0")).isFocused();
        assertThat(page.locator("#alias-0")).hasValue("Test_Old_Second_" + suffix);
        assertThat(page.locator("#alias-0")).hasAttribute("name", "aliases[0]");
        add.click();
        assertThat(page.locator("#alias-1")).isFocused();
        assertThat(page.locator("label[for='alias-1']")).hasText("PSN alias 2");
        page.locator("#alias-1").fill("Test_Old_Third_" + suffix);
        page.locator(".entity-editor-form button[type='submit']").click();
        assertThat(page).hasURL(url("/admin/drivers"));
        page.navigate(url("/admin/drivers/" + driver.getId() + "/edit"));
        assertThat(page.locator(".alias-input")).hasCount(2);
        assertThat(page.locator(".alias-input").first()).hasValue("Test_Old_Second_" + suffix);
        assertThat(page.locator(".alias-input").nth(1)).hasValue("Test_Old_Third_" + suffix);
    }

    @Test
    void givenTeam_whenSaveInvalidIdentity_thenEditorAndColorChangesSurviveValidation() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        team = helper.createTeam("Test Editor Team " + suffix, "T-EDIT-" + suffix);
        page.navigate(url("/admin/teams/" + team.getId() + "/edit"));
        page.locator("#primaryColor").fill("#123456");
        assertThat(page.locator(".color-pair:has(#primaryColor) input[type='color']")).hasValue("#123456");
        page.locator(".color-pair:has(#secondaryColor) input[type='color']").evaluate("el => { el.value = '#abcdef'; el.dispatchEvent(new Event('input', {bubbles: true})); }");
        assertThat(page.locator("#secondaryColor")).hasValue("#abcdef");
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#name").fill("");
        page.locator(".entity-editor-form button[type='submit']").click();
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#name")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#primaryColor")).hasValue("#123456");
        assertThat(page.locator("#teamLogo")).isVisible();
        assertThat(page.locator("#subShortName")).isVisible();
        page.locator("#name").fill(team.getName());
        page.locator(".entity-editor-form button[type='submit']").click();
        assertThat(page).hasURL(url("/admin/teams"));
        var saved = teamRepository.findById(team.getId()).orElseThrow();
        assertEquals("#123456", saved.getPrimaryColor());
        assertEquals("#abcdef", saved.getSecondaryColor());
    }
}
