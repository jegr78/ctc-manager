package org.ctc.discord;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class DiscordConfig {

	private static final String USER_AGENT_VALUE =
			"CTC-Manager (https://github.com/jegr78/ctc-manager, 1.13)";

	@Bean
	public Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean(name = "discordBotRestClient")
	public RestClient discordBotRestClient(
			@Value("${app.discord.base-url:https://discord.com/api/v10}") String baseUrl,
			@Value("${app.discord.bot-token:}") String botToken,
			DiscordRateLimitInterceptor rateLimitInterceptor,
			DiscordHostValidator hostValidator) {
		hostValidator.requireAllowed(baseUrl);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + botToken)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
				.requestInterceptor(rateLimitInterceptor)
				.build();
	}
}
