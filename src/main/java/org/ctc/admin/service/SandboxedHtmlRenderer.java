package org.ctc.admin.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import java.nio.file.Path;

/**
 * Screenshots graphics HTML in a Chromium context without JavaScript and without network access.
 * Images and fonts must be embedded as {@code data:} URIs; every other request is aborted.
 */
public final class SandboxedHtmlRenderer {

	private SandboxedHtmlRenderer() {
	}

	public static void screenshot(String html, int width, int height, boolean omitBackground, Path outputFile) {
		try (Playwright pw = Playwright.create();
		     Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
		     BrowserContext context = browser.newContext(new Browser.NewContextOptions()
				     .setJavaScriptEnabled(false)
				     .setOffline(true)
				     .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
				     .setViewportSize(width, height))) {
			context.route("**/*", Route::abort);
			Page page = context.newPage();
			page.setContent(html);
			page.screenshot(new Page.ScreenshotOptions()
					.setPath(outputFile)
					.setFullPage(false)
					.setOmitBackground(omitBackground));
		}
	}
}
