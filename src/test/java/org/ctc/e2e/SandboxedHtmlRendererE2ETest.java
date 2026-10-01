package org.ctc.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.ctc.admin.service.SandboxedHtmlRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("e2e")
class SandboxedHtmlRendererE2ETest {

	private static final int WIDTH = 400;
	private static final int HEIGHT = 300;

	@TempDir
	Path tempDir;

	private final AtomicInteger connections = new AtomicInteger();
	private ServerSocket server;
	private String origin;
	private String html;

	@BeforeEach
	void setUp() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread.ofVirtual().start(this::acceptConnections);
		Path localFile = tempDir.resolve("local.png");
		Files.write(localFile, redPng());
		origin = "http://127.0.0.1:" + server.getLocalPort();
		html = template(origin, localFile.toUri().toString(),
				"data:image/png;base64," + Base64.getEncoder().encodeToString(redPng()));
	}

	@AfterEach
	void tearDown() throws IOException {
		server.close();
	}

	@Test
	void givenTemplateWithScriptsAndRemoteResources_whenRenderedUnsandboxed_thenTheProofServerIsReached() throws Exception {
		// given
		Path page = Files.writeString(tempDir.resolve("graphic.html"), html);
		Path output = tempDir.resolve("unsandboxed.png");

		// when
		try (Playwright pw = Playwright.create();
		     Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
		     Page unsandboxed = browser.newPage(new Browser.NewPageOptions().setViewportSize(WIDTH, HEIGHT))) {
			unsandboxed.navigate(page.toUri().toString());
			unsandboxed.waitForTimeout(500);
			unsandboxed.screenshot(new Page.ScreenshotOptions().setPath(output));
		}

		// then
		assertThat(connections.get()).as("connections to the proof server without the sandbox").isPositive();
		BufferedImage image = ImageIO.read(output.toFile());
		assertThat(new Color(image.getRGB(220, 40))).as("file: image without the sandbox").isEqualTo(Color.RED);
	}

	@Test
	void givenTemplateWithScriptsAndRemoteResources_whenRendered_thenNothingConnectsAndOnlyDataUrisLoad() throws Exception {
		// given
		Path output = tempDir.resolve("graphic.png");

		// when
		SandboxedHtmlRenderer.screenshot(html, WIDTH, HEIGHT, false, output);
		Thread.sleep(500);

		// then
		assertThat(connections.get()).as("connections to the loopback proof server").isZero();
		BufferedImage image = ImageIO.read(output.toFile());
		assertThat(new Color(image.getRGB(300, 250))).as("page background, a script would turn it black")
				.isEqualTo(Color.WHITE);
		assertThat(new Color(image.getRGB(220, 40))).as("file: image").isEqualTo(Color.WHITE);
		assertThat(new Color(image.getRGB(125, 125))).as("embedded data: image").isEqualTo(Color.RED);
	}

	@Test
	void givenTemplateWithMetaRefresh_whenRendered_thenTheRedirectDoesNotConnect() throws Exception {
		// given
		String redirect = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=" + origin
				+ "/refresh\"></head><body>x</body></html>";

		// when
		SandboxedHtmlRenderer.screenshot(redirect, WIDTH, HEIGHT, false, tempDir.resolve("redirect.png"));
		Thread.sleep(500);

		// then
		assertThat(connections.get()).as("connections caused by the meta refresh").isZero();
	}

	private void acceptConnections() {
		while (!server.isClosed()) {
			try (Socket socket = server.accept()) {
				connections.incrementAndGet();
				OutputStream out = socket.getOutputStream();
				out.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				out.flush();
			} catch (IOException _) {
				// closed by tearDown or by the client
			}
		}
	}

	private static String template(String origin, String fileUrl, String dataUrl) {
		return """
				<!DOCTYPE html>
				<html><head>
				<link rel="preconnect" href="%1$s">
				<link rel="dns-prefetch" href="%1$s">
				<link rel="prefetch" href="%1$s/prefetch">
				<link rel="stylesheet" href="%1$s/stylesheet">
				<script src="%1$s/script"></script>
				<style>
				@import url(%1$s/import.css);
				@font-face { font-family: Remote; src: url(%1$s/font); }
				body { margin: 0; background: #fff; font-family: Remote; }
				#remote { width: 10px; height: 10px; background-image: url(%1$s/background); }
				#file { position: absolute; left: 200px; top: 20px; width: 40px; height: 40px; }
				#local { position: absolute; left: 100px; top: 100px; width: 50px; height: 50px;
				         background-image: url(%3$s); background-size: cover; }
				</style>
				</head><body>
				<img src="%1$s/image" width="10" height="10">
				<img id="file" src="%2$s">
				<iframe src="%1$s/frame" width="10" height="10"></iframe>
				<object data="%1$s/object" width="10" height="10"></object>
				<embed src="%1$s/embed" width="10" height="10">
				<svg width="10" height="10"><image href="%1$s/svg-image" width="10" height="10"/><use href="%1$s/svg-use#x"/></svg>
				<div id="remote">text</div>
				<div id="local"></div>
				<script>
				document.body.style.background = '#000';
				fetch('%1$s/fetch');
				new Image().src = '%1$s/script-image';
				</script>
				</body></html>
				""".formatted(origin, fileUrl, dataUrl);
	}

	private static byte[] redPng() throws IOException {
		var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		var png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		return png.toByteArray();
	}
}
