package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.admin.service.TeamCardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
class TemplateEditorE2ETest extends PlaywrightConfig {
    private static final String DEFAULT = "<html><body><h1>Default test artwork</h1></body></html>";
    @MockitoBean TeamCardService cardService;

    @BeforeEach
    void setUp() throws IOException {
        setupPage();
        var source = new AtomicReference<>(DEFAULT);
        var custom = new AtomicBoolean(false);
        when(cardService.loadTemplate()).thenAnswer(invocation -> source.get());
        when(cardService.hasCustomTemplate()).thenAnswer(invocation -> custom.get());
        doAnswer(invocation -> { source.set(invocation.getArgument(0)); custom.set(true); return null; })
                .when(cardService).saveTemplate(anyString());
        doAnswer(invocation -> { source.set(DEFAULT); custom.set(false); return null; })
                .when(cardService).resetTemplate();
    }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenFailedPreview_whenRetried_thenErrorIsTextAndKeyboardCanLeaveTheEditor() {
        page.route("**/template-editors/team-cards/preview", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions().setStatus(400)
                        .setContentType("text/plain").setBody("<b>Test preview error</b>")));
        page.navigate(url("/admin/tools/template-editors"));
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Preview failed: <b>Test preview error</b>");
        assertThat(page.locator("#template-preview-status b")).hasCount(0);
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Refresh").setExact(true))).isEnabled();
        page.unroute("**/template-editors/team-cards/preview");
        var source = page.getByLabel("Template HTML + Thymeleaf", new Page.GetByLabelOptions().setExact(true));
        source.fill("<html><body><h1>Preview test artwork</h1></body></html>");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Refresh").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Preview updated from the current text.");
        assertThat(page.frameLocator("iframe").getByRole(AriaRole.HEADING)).containsText("Preview test artwork");
        assertThat(page.locator("iframe")).hasAttribute("sandbox", "allow-same-origin");
        source.focus();
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Template").setExact(true))).isFocused();
        page.keyboard().press("Shift+Tab");
        assertThat(source).isFocused();
        source.fill("<html><body>Changed after preview</body></html>");
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("The text has changed.");
        page.setViewportSize(390, 768);
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
    }

    @Test
    void givenNoJavaScript_whenTemplateSavedAndReset_thenNativeFormsPreserveTheSelectedTemplate() throws IOException {
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = context.newPage();
            nativePage.navigate(url("/admin/tools/template-editors"));
            String source = "<html><body>Saved test artwork</body></html>";
            nativePage.getByLabel("Template HTML + Thymeleaf", new Page.GetByLabelOptions().setExact(true)).fill(source);
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Template").setExact(true)).click();
            assertThat(nativePage).hasURL(url("/admin/tools/template-editors?tab=team-cards"));
            assertThat(nativePage.locator(".alert-success")).containsText("saved");
            assertThat(nativePage.locator(".editor-status-row")).containsText("Custom template");
            assertThat(nativePage.locator("#template-source")).hasValue(source);
            verify(cardService).saveTemplate(source);
            nativePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset").setExact(true)).click();
            assertThat(nativePage.locator("#template-source")).hasValue(DEFAULT);
            assertThat(nativePage.locator(".editor-status-row")).containsText("Default template");
            assertThat(nativePage.locator("nav[aria-label='Graphic templates'] [aria-current='page']")).hasText("Team Cards");
            verify(cardService).resetTemplate();
        }
    }
}
