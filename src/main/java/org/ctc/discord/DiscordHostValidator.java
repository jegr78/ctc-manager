package org.ctc.discord;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Positive SSRF whitelist guard used by {@link DiscordConfig#discordBotRestClient}
 * and {@link DiscordWebhookClient}. Inverted polarity from the v1.5
 * {@code FileStorageService.validateHostname} negative-blocklist pattern: only
 * hosts on {@code app.discord.allowed-hosts} are accepted; everything else throws
 * {@link IllegalArgumentException} with message {@code "Discord host blocked: " + host}.
 *
 * <p>Mitigates T-93-01 (Bot-Token leak via SSRF detour) and T-93-02 (Webhook-URL
 * leak via SSRF detour) per {@code 93-THREAT-MODEL.md}.
 */
@Component
public class DiscordHostValidator {

	private final Set<String> allowedHosts;

	public DiscordHostValidator(@Value("${app.discord.allowed-hosts:discord.com}") String allowedHostsCsv) {
		this.allowedHosts = Arrays.stream(allowedHostsCsv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(String::toLowerCase)
				.collect(Collectors.toUnmodifiableSet());
	}

	public void requireAllowed(String url) {
		String host = parseHost(url);
		if (host == null) {
			throw new IllegalArgumentException("Discord host blocked: <null>");
		}
		if (!allowedHosts.contains(host.toLowerCase())) {
			throw new IllegalArgumentException("Discord host blocked: " + host);
		}
	}

	private static String parseHost(String url) {
		try {
			return URI.create(url).getHost();
		} catch (IllegalArgumentException _) {
			return null;
		}
	}
}
