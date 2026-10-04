package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
class AdminNavigationE2ETest extends PlaywrightConfig {
    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenMobileMenu_whenOpenCycleFocusAndClose_thenFocusStaysInTheMenuAndReturnsToTheTrigger() {
        page.setViewportSize(390, 768);
        page.navigate(url("/admin/seasons"));
        var sidebar = page.locator("#sidebar");
        var toggle = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Toggle navigation").setExact(true));
        var close = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close navigation").setExact(true));
        assertTrue((Boolean) sidebar.evaluate("element => element.inert"));
        toggle.click();
        assertThat(page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Admin navigation").setExact(true))).isVisible();
        assertThat(close).isFocused();
        assertTrue((Boolean) page.locator("main").evaluate("element => element.inert"));
        page.keyboard().press("Shift+Tab");
        assertThat(sidebar.locator("a").last()).isFocused();
        page.keyboard().press("Tab");
        assertThat(close).isFocused();
        page.keyboard().press("Enter");
        assertThat(toggle).isFocused();
        assertThat(toggle).hasAttribute("aria-expanded", "false");
        assertTrue((Boolean) sidebar.evaluate("element => element.inert"));
        assertFalse((Boolean) page.locator("main").evaluate("element => element.inert"));
        toggle.click();
        page.keyboard().press("Escape");
        assertThat(toggle).isFocused();
        toggle.click();
        page.locator(".sidebar-overlay").click(new com.microsoft.playwright.Locator.ClickOptions().setPosition(380, 300));
        assertThat(toggle).isFocused();
        assertFalse((Boolean) page.evaluate("document.body.classList.contains('admin-nav-open')"));
    }

    @Test
    void givenOpenMenu_whenResizeAndNavigate_thenDesktopAndMobileStatesRemainUsable() {
        page.setViewportSize(390, 768);
        page.navigate(url("/admin/seasons"));
        page.locator(".sidebar-toggle").click();
        page.setViewportSize(1366, 768);
        assertThat(page.locator("#sidebar")).not().hasAttribute("aria-modal", "true");
        assertFalse((Boolean) page.locator("#sidebar").evaluate("element => element.inert"));
        assertFalse((Boolean) page.locator("main").evaluate("element => element.inert"));
        assertThat(page.locator(".sidebar-toggle")).not().isVisible();
        assertThat(page.locator(".sidebar-close")).not().isVisible();
        page.locator("#sidebar a[href='/admin/teams']").click();
        assertThat(page).hasURL(url("/admin/teams"));
        assertThat(page.locator("#sidebar a[aria-current='page']")).hasText("Teams");
        page.setViewportSize(390, 768);
        page.locator(".sidebar-toggle").click();
        page.locator("#sidebar a[href='/admin/drivers']").click();
        assertThat(page).hasURL(url("/admin/drivers"));
        assertThat(page.locator(".sidebar-toggle")).hasAttribute("aria-expanded", "false");
        assertFalse((Boolean) page.locator("main").evaluate("element => element.inert"));
        page.goBack();
        assertThat(page).hasURL(url("/admin/teams"));
        assertThat(page.locator(".sidebar-toggle")).hasAttribute("aria-expanded", "false");
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
    }

    @Test
    void givenDesktopNavigation_whenSkipToContent_thenMainReceivesFocusAndCurrentPageIsIdentified() {
        page.setViewportSize(1366, 768);
        page.navigate(url("/admin/seasons"));
        assertThat(page.locator("#sidebar a[aria-current='page']")).hasText("All seasons");
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Skip to content").setExact(true))).isFocused();
        page.keyboard().press("Enter");
        assertThat(page.locator("#main-content")).isFocused();
        page.locator("#sidebar a[href='/admin/generate']").click();
        assertThat(page.locator("#sidebar a[aria-current='page']")).hasText("Generate Site");
    }

    @Test
    void givenMobileWithoutJavaScript_whenChooseNavigationLink_thenMenuAndDestinationRemainAccessible() {
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(390, 768))) {
            var nativePage = context.newPage();
            nativePage.navigate(url("/admin/seasons"));
            assertThat(nativePage.locator(".sidebar-toggle")).not().isVisible();
            assertThat(nativePage.locator(".sidebar-close")).not().isVisible();
            var teams = nativePage.locator("#sidebar a[href='/admin/teams']");
            assertThat(teams).isVisible();
            teams.click();
            assertThat(nativePage).hasURL(url("/admin/teams"));
            assertThat(nativePage.locator("#sidebar a[aria-current='page']")).hasText("Teams");
            assertFalse((Boolean) nativePage.evaluate("document.documentElement.scrollWidth > innerWidth"));
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Skip to content").setExact(true)).focus();
            nativePage.keyboard().press("Enter");
            assertThat(nativePage.locator("#main-content")).isFocused();
        }
    }
}
