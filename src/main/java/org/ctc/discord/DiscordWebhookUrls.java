package org.ctc.discord;

import java.util.regex.Pattern;

public final class DiscordWebhookUrls {

	private static final Pattern TOKEN = Pattern.compile("(/webhooks/[^/?#\\s]+/)[^/?#\\s]+");

	private DiscordWebhookUrls() {
	}

	/** Replaces the executable token of a webhook URL with {@code ***}, keeping host, version and id. */
	public static String redact(String url) {
		return url == null ? null : TOKEN.matcher(url).replaceAll("$1***");
	}
}
