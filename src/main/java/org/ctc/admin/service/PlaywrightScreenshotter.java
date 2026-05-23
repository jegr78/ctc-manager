package org.ctc.admin.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightScreenshotter implements Function<String, byte[]> {

	@Override
	public byte[] apply(String html) {
		try {
			Path htmlFile = Files.createTempFile("provisional-", ".html");
			Path pngFile = Files.createTempFile("provisional-", ".png");
			try {
				Files.writeString(htmlFile, html);
				try (Playwright pw = Playwright.create();
				     Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
				     Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1920, 1080))) {
					page.navigate("file://" + htmlFile.toAbsolutePath());
					page.screenshot(new Page.ScreenshotOptions().setPath(pngFile).setFullPage(false));
				}
				return Files.readAllBytes(pngFile);
			} finally {
				Files.deleteIfExists(htmlFile);
				Files.deleteIfExists(pngFile);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
