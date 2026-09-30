package org.ctc.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the {@code %replace} rule of every logback pattern the way logback's
 * {@code ReplacingCompositeConverter} does, to each webhook URL format the Discord config accepts.
 */
class LogbackWebhookMaskTest {

	private static final String TOKEN = "SyntheticToken_abc-123";
	private static final Pattern REPLACE_RULE =
			Pattern.compile("%replace\\(%m%n%wEx\\)\\{'([^']*)', '([^']*)'}");

	@Test
	void whenLogbackConfigsRead_thenConsoleFileAndTestPatternsShareOneMask() throws IOException {
		// when
		var rules = new ArrayList<List<String>>();
		rules.addAll(rules(Path.of("src/main/resources/logback-spring.xml")));
		rules.addAll(rules(Path.of("src/test/resources/logback-test.xml")));

		// then
		assertThat(rules).as("console + file + test pattern").hasSize(3);
		assertThat(rules).as("every appender must mask with the same rule").containsOnly(rules.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://discord.com/api/webhooks/123/" + TOKEN,
			"https://discord.com/api/v10/webhooks/123/" + TOKEN,
			"https://discord.com/v9/webhooks/123/" + TOKEN,
			"https://discordapp.com/webhooks/123/" + TOKEN,
			"https://discord.com/api/v10/webhooks/123/" + TOKEN + "?wait=true&thread_id=5"})
	void givenAcceptedWebhookUrl_whenLoggedInMessageOrExceptionText_thenTheTokenIsMasked(String url) throws IOException {
		// given
		var rule = rules(Path.of("src/main/resources/logback-spring.xml")).getFirst();
		var line = "execute failed for " + url + "\norg.springframework.web.client.ResourceAccessException: "
				+ "I/O error on POST request for \"" + url + "\": timeout";

		// when
		var masked = Pattern.compile(rule.get(0)).matcher(line).replaceAll(rule.get(1));

		// then
		assertThat(masked).as(masked).doesNotContain(TOKEN).contains("***/***");
	}

	private static List<List<String>> rules(Path config) throws IOException {
		Matcher matcher = REPLACE_RULE.matcher(Files.readString(config));
		var rules = new ArrayList<List<String>>();
		while (matcher.find()) {
			rules.add(List.of(matcher.group(1), matcher.group(2)));
		}
		return rules;
	}
}
