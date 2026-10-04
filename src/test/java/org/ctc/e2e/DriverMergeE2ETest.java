package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class DriverMergeE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired DriverRepository driverRepository;
    @Autowired PsnAliasRepository aliasRepository;
    private Driver source;
    private Driver target;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() {
        teardownPage();
        if (source != null && driverRepository.existsById(source.getId())) driverRepository.deleteById(source.getId());
        if (target != null && driverRepository.existsById(target.getId())) driverRepository.deleteById(target.getId());
    }

    @Test
    void givenDriverWithAliasAndQuotedName_whenReviewAndConfirmMerge_thenDirectionAndDeletionAreExplicit() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        source = helper.createDriver("Test_Merge_O'" + suffix, "Test Merge Source " + suffix);
        target = helper.createDriver("Test_Merge_Target_" + suffix, "Test Merge Target " + suffix);
        var oldAlias = "Test_Merge_Alias_" + suffix;
        aliasRepository.save(new PsnAlias(source, oldAlias));
        page.navigate(url("/admin/drivers/" + source.getId() + "/merge"));
        assertThat(page.locator("#targetId option[value='" + source.getId() + "']")).hasCount(0);
        page.locator("button[type='submit']").click();
        assertThat(page).hasURL(url("/admin/drivers/" + source.getId() + "/merge"));
        page.locator("#targetId").selectOption(target.getId().toString());
        page.locator("button[type='submit']").click();
        assertThat(page.locator("[data-merge-source]")).containsText(source.getPsnId());
        assertThat(page.locator("[data-merge-target]")).containsText(target.getPsnId());
        assertThat(page.locator("[data-merge-source]")).containsText("Removed after merge");
        assertThat(page.locator("[data-merge-target]")).containsText("Kept after merge");
        assertThat(page.locator("tr:has(th:text-is('Existing PSN aliases')) td").first()).hasText("1");
        assertTrue(driverRepository.existsById(source.getId()));
        assertEquals(source.getId(), aliasRepository.findByAliasIgnoreCase(oldAlias).orElseThrow().getDriver().getId());
        page.setViewportSize(390, 768);
        assertEquals(false, page.evaluate("() => document.documentElement.scrollWidth > innerWidth"));
        var confirmations = new AtomicInteger();
        page.onDialog(dialog -> {
            assertEquals("confirm", dialog.type());
            if (confirmations.incrementAndGet() == 1) dialog.dismiss();
            else dialog.accept();
        });
        page.locator("button[type='submit']").click();
        assertEquals(1, confirmations.get());
        assertTrue(driverRepository.existsById(source.getId()));
        page.locator("button[type='submit']").click();
        assertThat(page).hasURL(url("/admin/drivers/" + target.getId()));
        assertEquals(2, confirmations.get());
        assertFalse(driverRepository.existsById(source.getId()));
        assertEquals(target.getId(), aliasRepository.findByAliasIgnoreCase(oldAlias).orElseThrow().getDriver().getId());
        assertEquals(target.getId(), aliasRepository.findByAliasIgnoreCase(source.getPsnId()).orElseThrow().getDriver().getId());
    }
}
