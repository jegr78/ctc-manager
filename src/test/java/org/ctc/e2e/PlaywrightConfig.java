package org.ctc.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {

	static Playwright playwright;
	protected static Browser browser;

	@LocalServerPort
	int port;

	BrowserContext context;
	protected Page page;

	@BeforeAll
	static void setupBrowser() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
	}

	@AfterAll
	static void teardownBrowser() {
		if (browser != null) {
			browser.close();
		}
		if (playwright != null) {
			playwright.close();
		}
	}

	protected void setupPage() {
		context = browser.newContext();
		page = context.newPage();
	}

	protected void teardownPage() {
		if (context != null) {
			context.close();
		}
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}
}
