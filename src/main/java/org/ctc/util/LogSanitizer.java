package org.ctc.util;

public final class LogSanitizer {

	private LogSanitizer() {}

	public static String sanitize(Object value) {
		// CodeQL java/log-injection recognises only the literal replaceAll("\\R", ...) as a taint barrier (not \p{Cntrl} or [\r\n]) — keep it the first pass.
		return String.valueOf(value)
				.replaceAll("\\R", "_")
				.replaceAll("[\\x00-\\x08\\x09\\x0E-\\x1F\\x7F]", "_");
	}
}
