package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Tag("e2e")
@Import(PublicNavigationE2ETest.NavigationFixtureConfig.class)
class PublicNavigationE2ETest extends PlaywrightConfig {
    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @ParameterizedTest
    @CsvSource({"index,Home", "teams,Teams", "drivers,Drivers", "archive,Archive",
            "alltime-standings,Standings", "alltime-driver-ranking,Driver Ranking"})
    void givenPublicPage_whenOpenMenu_thenCurrentDestinationIsIdentified(String destination, String label) {
        page.navigate(url("/public-navigation/" + destination + ".html"));
        page.locator(".site-menu > summary").click();
        assertThat(page.locator(".nav-links a[aria-current='page']")).hasText(label);
        assertThat(page.locator(".nav-links .nav-link-active")).hasText(label);
    }

    @Test
    void givenSmallScreen_whenDismissMenu_thenFocusAndPageRemainUsable() {
        page.setViewportSize(320, 480);
        page.navigate(url("/public-navigation/index.html"));
        var summary = page.locator(".site-menu > summary");
        summary.click();
        page.keyboard().press("Tab");
        assertThat(page.locator(".nav-links a").first()).isFocused();
        page.keyboard().press("Escape");
        assertThat(summary).isFocused();
        assertThat(page.locator(".site-menu")).not().hasAttribute("open", "");
        summary.click();
        page.locator(".nav-links a").last().focus();
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
        assertFalse((Boolean) page.locator(".nav-links a").last().evaluate("element => element.getBoundingClientRect().bottom > innerHeight"));
        page.keyboard().press("Tab");
        assertThat(page.locator(".site-menu")).not().hasAttribute("open", "");
        summary.click();
        page.locator(".footer p").click();
        assertThat(page.locator(".site-menu")).not().hasAttribute("open", "");
    }

    @Test
    void givenSeasonPage_whenNavigateAndReturn_thenContextAndClosedMenuArePreserved() {
        page.setViewportSize(390, 768);
        page.navigate(url("/public-navigation/season/fixture/matchdays.html"));
        assertThat(page.locator(".subnav a[aria-current='page']")).hasText("Matchdays");
        page.locator(".site-menu > summary").click();
        assertThat(page.locator(".nav-links a[aria-current='location']")).hasText("Current season");
        page.locator(".nav-links a", new Page.LocatorOptions().setHasText("Teams")).click();
        assertThat(page).hasURL(url("/public-navigation/teams.html"));
        page.goBack();
        assertThat(page.locator(".site-menu")).not().hasAttribute("open", "");
        page.keyboard().press("Tab");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Skip to main content").setExact(true)).focus();
        page.keyboard().press("Enter");
        assertThat(page.locator("#main-content")).isFocused();
    }

    @Test
    void givenNoJavaScript_whenUseNativeMenuAndSkipLink_thenNavigationRemainsAccessible() {
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(390, 768))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/public-navigation/index.html"));
            nativePage.locator(".site-menu > summary").focus();
            nativePage.keyboard().press("Enter");
            nativePage.locator(".nav-links a", new Page.LocatorOptions().setHasText("Drivers")).click();
            assertThat(nativePage).hasURL(url("/public-navigation/drivers.html"));
            nativePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Skip to main content").setExact(true)).focus();
            nativePage.keyboard().press("Enter");
            assertThat(nativePage.locator("#main-content")).isFocused();
            assertFalse((Boolean) nativePage.evaluate("document.documentElement.scrollWidth > innerWidth"));
        }
    }

    @Test
    void givenNarrowScreenAndLargeText_whenUseNavigation_thenHeadingsWrapAndMenuStaysWithinTheViewport() {
        page.setViewportSize(320, 480);
        page.navigate(url("/public-navigation/archive.html"));
        page.evaluate("""
                () => {
                    const elements = Array.from(document.querySelectorAll('h1,p,a,span,summary'));
                    const sizes = elements.map(element => parseFloat(getComputedStyle(element).fontSize));
                    elements.forEach((element,index) => element.style.fontSize = sizes[index] * 2 + 'px');
                    document.querySelector('h1').textContent = 'CommunityTeamCupWithAnUnusuallyLongSeasonName';
                }
                """);
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
        assertFalse((Boolean) page.locator("h1").evaluate("heading => heading.scrollWidth > heading.clientWidth"));
        page.locator(".site-menu > summary").click();
        page.locator(".nav-links a").last().focus();
        assertFalse((Boolean) page.locator(".nav-links a").last().evaluate("link => link.getBoundingClientRect().bottom > innerHeight"));
    }

    @TestConfiguration
    static class NavigationFixtureConfig {
        @Bean
        NavigationFixtureController navigationFixtureController(TemplateEngine engine) {
            return new NavigationFixtureController(engine);
        }
    }

    @Controller
    @TestComponent
    static class NavigationFixtureController {
        private final TemplateEngine engine;

        NavigationFixtureController(TemplateEngine engine) { this.engine = engine; }

        @GetMapping(value = {"/public-navigation/{page}.html", "/public-navigation/season/{slug}/{page}.html"}, produces = "text/html")
        @ResponseBody
        String navigationPage(HttpServletRequest request) {
            var uri = request.getRequestURI();
            var destination = uri.substring(uri.lastIndexOf('/') + 1).replace(".html", "");
            var context = new Context();
            context.setVariable("currentPage", destination.equals("index") ? "home" : destination);
            context.setVariable("rootPath", "/public-navigation");
            context.setVariable("assetsPath", "/site");
            context.setVariable("activeSeasonSlug", "fixture");
            context.setVariable("activeSeasonName", "Test season");
            context.setVariable("seasonSlug", uri.contains("/season/") ? "fixture" : null);
            context.setVariable("seasonName", "Test season");
            context.setVariable("hasPlayoff", true);
            context.setVariable("seasonEntries", List.of());
            return engine.process("site/archive", context);
        }
    }
}
