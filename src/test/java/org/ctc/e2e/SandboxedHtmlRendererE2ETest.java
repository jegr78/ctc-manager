package org.ctc.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.ctc.admin.service.SandboxedHtmlRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("e2e")
class SandboxedHtmlRendererE2ETest {

	@TempDir
	Path tempDir;

	private final List<String> hits = new CopyOnWriteArrayList<>();
	private HttpServer server;
	private String origin;

	@BeforeEach
	void startProofServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			hits.add(exchange.getRequestURI().getPath());
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		origin = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopProofServer() {
		server.stop(0);
	}

	@Test
	void givenTemplateWithScriptsAndRemoteResources_whenRendered_thenNothingReachesTheServerAndScriptsDoNotRun() throws Exception {
		// given
		String html = """
				<!DOCTYPE html>
				<html><head>
				<meta http-equiv="refresh" content="0;url=%1$s/refresh">
				<link rel="stylesheet" href="%1$s/stylesheet">
				<link rel="prefetch" href="%1$s/prefetch">
				<script src="%1$s/script"></script>
				<style>
				@font-face { font-family: Remote; src: url(%1$s/font); }
				body { margin: 0; background: #fff; font-family: Remote; }
				#remote { width: 10px; height: 10px; background-image: url(%1$s/background); }
				#local { position: absolute; left: 100px; top: 100px; width: 50px; height: 50px;
				         background-image: url(%2$s); background-size: cover; }
				</style>
				</head><body>
				<img src="%1$s/image" width="10" height="10">
				<iframe src="%1$s/frame"></iframe>
				<object data="%1$s/object"></object>
				<div id="remote">text</div>
				<div id="local"></div>
				<script>
				document.body.style.background = '#000';
				fetch('%1$s/fetch');
				new Image().src = '%1$s/script-image';
				</script>
				</body></html>
				""".formatted(origin, redPixelDataUri());
		Path output = tempDir.resolve("graphic.png");

		// when
		SandboxedHtmlRenderer.screenshot(html, 400, 300, false, output);

		// then
		assertThat(hits).as("requests that reached the loopback proof server").isEmpty();
		BufferedImage image = ImageIO.read(Files.newInputStream(output));
		assertThat(new Color(image.getRGB(300, 250))).as("page background, a script would turn it black")
				.isEqualTo(Color.WHITE);
		assertThat(new Color(image.getRGB(125, 125))).as("embedded data: image").isEqualTo(Color.RED);
	}

	private static String redPixelDataUri() throws IOException {
		var pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		pixel.setRGB(0, 0, Color.RED.getRGB());
		var png = new ByteArrayOutputStream();
		ImageIO.write(pixel, "png", png);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
	}
}
