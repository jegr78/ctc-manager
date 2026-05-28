package org.ctc.domain.util;

import java.util.regex.Pattern;

public final class HexColor {

	private static final Pattern PATTERN = Pattern.compile("^#[0-9a-fA-F]{3,8}$");

	private HexColor() {}

	public static String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return PATTERN.matcher(trimmed).matches() ? trimmed : null;
	}
}
