package org.ctc.admin.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightScreenshotter implements Function<String, byte[]> {

	@Override
	public byte[] apply(String html) {
		try {
			Path pngFile = Files.createTempFile("provisional-", ".png");
			try {
				SandboxedHtmlRenderer.screenshot(html, 1920, 1080, false, pngFile);
				return Files.readAllBytes(pngFile);
			} finally {
				Files.deleteIfExists(pngFile);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
