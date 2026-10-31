package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SandboxedHtmlRendererTest {

    @Test
    void whenRenderingLocalGraphic_thenDriverCannotInstallBrowsersFromNetwork() {
        Playwright playwright = mock(Playwright.class);
        BrowserType type = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext context = mock(BrowserContext.class);
        when(playwright.chromium()).thenReturn(type);
        when(type.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newContext(any(Browser.NewContextOptions.class))).thenReturn(context);
        when(context.newPage()).thenReturn(mock(Page.class));
        AtomicReference<Playwright.CreateOptions> options = new AtomicReference<>();
        try (var factory = mockStatic(Playwright.class)) {
            factory.when(Playwright::create).thenReturn(playwright);
            factory.when(() -> Playwright.create(any(Playwright.CreateOptions.class))).thenAnswer(call -> {
                options.set(call.getArgument(0));
                return playwright;
            });

            SandboxedHtmlRenderer.screenshot("<p>Local graphic</p>", 100, 100, false, Path.of("fixture.png"));

            assertThat(options.get()).as("local rendering must control driver installation").isNotNull();
            assertThat(options.get().env).containsEntry("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        }
    }
}
