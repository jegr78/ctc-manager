package org.ctc.domain.util;

import static org.springframework.util.StringUtils.hasText;

import java.util.regex.Pattern;

public final class HexColor {

	private static final Pattern PATTERN = Pattern.compile("^#[0-9a-fA-F]{3,8}$");

	private HexColor() {}

	public static String sanitize(String value) {
		if (!hasText(value)) {
			return null;
		}
		String trimmed = value.trim();
		return PATTERN.matcher(trimmed).matches() ? trimmed : null;
	}
}
