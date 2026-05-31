package org.ctc.admin.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Slf4j
public abstract class AbstractGraphicService {

	protected static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
	protected static final String CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png";

	protected static final int TEAM_DRIVERS = 6;

	protected final TemplateEngine templateEngine;
	protected final Path uploadDir;

	protected AbstractGraphicService(TemplateEngine templateEngine, String uploadDir) {
		this.templateEngine = templateEngine;
		this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
	}

	protected void renderScreenshot(String html, Path outputFile) throws IOException {
		Path tempFile = Files.createTempFile("graphic-", ".html");
		Files.writeString(tempFile, html);

		try (Playwright pw = Playwright.create();
		     Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
		     Page page = browser.newPage(new Browser.NewPageOptions()
					 .setViewportSize(1920, 1080))) {
			page.navigate("file://" + tempFile.toAbsolutePath());
			page.screenshot(new Page.ScreenshotOptions()
					.setPath(outputFile)
					.setFullPage(false));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	protected void renderScreenshotTransparent(String html, Path outputFile) throws IOException {
		Path tempFile = Files.createTempFile("graphic-", ".html");
		Files.writeString(tempFile, html);

		try (Playwright pw = Playwright.create();
		     Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
		     Page page = browser.newPage(new Browser.NewPageOptions()
					 .setViewportSize(1920, 1080))) {
			page.navigate("file://" + tempFile.toAbsolutePath());
			page.screenshot(new Page.ScreenshotOptions()
					.setPath(outputFile)
					.setFullPage(false)
					.setOmitBackground(true));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	protected String encodeCardBase64(String cardUrl) {
		if (cardUrl == null || !cardUrl.startsWith("/uploads/")) {
			return null;
		}
		try {
			Path cardFile = uploadDir.resolve(cardUrl.substring("/uploads/".length())).normalize();
			if (!cardFile.startsWith(uploadDir)) {
				return null;
			}
			if (Files.exists(cardFile)) {
				byte[] bytes = Files.readAllBytes(cardFile);
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
			}
		} catch (IOException e) {
			log.warn("Failed to encode card: {}", cardUrl, e);
		}
		return null;
	}

	protected String encodeClasspathResource(String classpathLocation, String mimeType) {
		try {
			var resource = new ClassPathResource(classpathLocation);
			if (resource.exists()) {
				try (var is = resource.getInputStream()) {
					byte[] bytes = is.readAllBytes();
					return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
				}
			}
		} catch (IOException e) {
			log.warn("Failed to encode classpath resource: {}", classpathLocation, e);
		}
		return null;
	}

	protected String sanitizeFilename(String name) {
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	protected String processStringTemplate(String template, Context ctx) {
		var engine = new SpringTemplateEngine();
		var resolver = new StringTemplateResolver();
		resolver.setTemplateMode(TemplateMode.HTML);
		engine.setTemplateResolver(resolver);
		return engine.process(template, ctx);
	}

	protected String buildCardPath(String seasonId, String teamShortName) {
		return "/uploads/team-cards/" + seasonId + "/" + sanitizeFilename(teamShortName) + ".png";
	}
}
