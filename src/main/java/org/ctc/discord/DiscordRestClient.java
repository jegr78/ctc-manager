package org.ctc.discord;

import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class DiscordRestClient {

	private final RestClient bot;

	public DiscordRestClient(@Qualifier("discordBotRestClient") RestClient bot) {
		this.bot = bot;
	}

	public BotUser fetchBotUser() throws DiscordApiException {
		try {
			return bot.get()
					.uri("/users/@me")
					.retrieve()
					.body(BotUser.class);
		} catch (RestClientResponseException e) {
			throw DiscordApiExceptionMapper.from(e);
		} catch (ResourceAccessException e) {
			throw unwrapInterceptorException(e);
		}
	}

	private static DiscordApiException unwrapInterceptorException(ResourceAccessException e) {
		Throwable cause = e.getCause();
		if (cause instanceof DiscordApiException dae) {
			return dae;
		}
		return new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
	}

	public record BotUser(String id, String username, String discriminator) {
	}
}
