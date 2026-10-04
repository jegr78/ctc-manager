package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Tag("e2e")
@Import(ErrorNavigationE2ETest.ErrorFixtureConfig.class)
class ErrorNavigationE2ETest extends PlaywrightConfig {
    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenMissingRecordWithoutJavaScript_whenOpenSeasons_thenNativeRecoveryWorksAndDetailsStayCollapsed() {
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = context.newPage();
            var response = nativePage.navigate(url("/admin/teams/" + UUID.randomUUID()));
            assertEquals(404, response.status());
            assertThat(nativePage.locator(".error-status strong")).hasText("404");
            assertThat(nativePage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Not Found").setExact(true))).isVisible();
            assertThat(nativePage.locator(".error-details[open]")).hasCount(0);
            assertThat(nativePage.locator("[data-error-back]")).not().isVisible();
            assertThat(nativePage.locator("a[href^='javascript:']")).hasCount(0);
            nativePage.locator(".error-details summary").click();
            assertThat(nativePage.locator(".error-details code")).hasText("EntityNotFoundException");
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Open seasons").setExact(true)).click();
            assertThat(nativePage).hasURL(url("/admin/seasons"));
        }
    }

    @Test
    void givenPreviousAdminPage_whenReturnByKeyboard_thenHistoryRestoresThePreviousPage() {
        page.navigate(url("/admin/access-denied"));
        assertThat(page.locator(".error-status strong")).hasText("403");
        assertThat(page.locator("[data-error-back]")).not().isVisible();
        assertThat(page.locator(".error-details")).hasCount(0);
        page.navigate(url("/admin/seasons"));
        page.evaluate("""
                target => {
                    const link = document.createElement('a');
                    link.id = 'test-missing-record';
                    link.href = target;
                    link.textContent = 'Test missing record';
                    document.querySelector('main').appendChild(link);
                }
                """, url("/admin/teams/" + UUID.randomUUID()));
        page.locator("#test-missing-record").click();
        var back = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Back to previous page").setExact(true));
        assertThat(back).isVisible();
        back.focus();
        page.keyboard().press("Enter");
        assertThat(page).hasURL(url("/admin/seasons"));
        page.navigate(url("/admin/access-denied"), new Page.NavigateOptions().setReferer("https://example.invalid/"));
        assertThat(page.locator("[data-error-back]")).not().isVisible();
    }

    @ParameterizedTest
    @CsvSource({"400,Validation Error", "409,Business Rule Violation"})
    void givenInputError_whenRendered_thenMessageIsEscapedAndFitsTheMobileViewport(int status, String heading) {
        page.setViewportSize(390, 768);
        var response = page.navigate(url("/admin/test-error/input/" + status));
        assertEquals(status, response.status());
        assertThat(page.locator("#error-title")).hasText(heading);
        assertThat(page.locator(".error-message-panel p")).hasText("<b>Test input must be reviewed</b>");
        assertThat(page.locator(".error-message-panel b")).hasCount(0);
        assertThat(page.locator(".error-details[open]")).hasCount(0);
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
    }

    @Test
    void givenServletError_whenFallbackRendered_thenBrandAndNativeReturnRemainAvailable() {
        var response = page.navigate(url("/admin/test-error/fallback"));
        assertEquals(400, response.status());
        assertThat(page.locator(".error-page-brand")).isVisible();
        assertThat(page.locator(".error-status strong")).hasText("400");
        assertThat(page.locator(".error-details")).hasCount(0);
        assertThat(page.locator("a[href^='javascript:']")).hasCount(0);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Open seasons").setExact(true)).click();
        assertThat(page).hasURL(url("/admin/seasons"));
    }

    @TestConfiguration
    static class ErrorFixtureConfig {
        @Bean
        ErrorFixtureController errorFixtureController() { return new ErrorFixtureController(); }
    }

    @Controller
    @TestComponent
    static class ErrorFixtureController {
        @GetMapping("/admin/test-error/input/{status}")
        public void inputError(@PathVariable int status) {
            if (status == 400) throw new ValidationException("<b>Test input must be reviewed</b>");
            throw new BusinessRuleException("<b>Test input must be reviewed</b>");
        }

        @GetMapping("/admin/test-error/fallback")
        public void servletError() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test servlet error"); }
    }
}
