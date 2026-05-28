package org.ctc.discord;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DiscordDevSeedProperties.class)
public class DiscordConfig {

	@Bean
	public Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean(name = "discordUserAgent")
	public String discordUserAgent(@Value("${app.version:dev}") String appVersion) {
		return "CTC-Manager (https://github.com/jegr78/ctc-manager, " + appVersion + ")";
	}

	@Bean(name = "discordBotRestClient")
	public RestClient discordBotRestClient(
			@Value("${app.discord.base-url:https://discord.com/api/v10}") String baseUrl,
			@Value("${app.discord.bot-token:}") String botToken,
			@org.springframework.beans.factory.annotation.Qualifier("discordUserAgent") String discordUserAgent,
			DiscordRateLimitInterceptor rateLimitInterceptor,
			DiscordHostValidator hostValidator) {
		hostValidator.requireAllowed(baseUrl);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + botToken)
				.defaultHeader(HttpHeaders.USER_AGENT, discordUserAgent)
				.requestInterceptor(rateLimitInterceptor)
				.build();
	}
}
