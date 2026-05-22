package org.ctc.discord;

import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordRestClient.BotUser;
import org.ctc.discord.exception.DiscordApiException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordBotIdentityCache {

	private final DiscordRestClient restClient;
	private final AtomicReference<String> cachedBotUserId = new AtomicReference<>();

	public String getBotUserId() throws DiscordApiException {
		String cached = cachedBotUserId.get();
		return cached != null ? cached : refresh();
	}

	public String refresh() throws DiscordApiException {
		BotUser user = restClient.fetchBotUser();
		cachedBotUserId.set(user.id());
		log.debug("Discord bot identity cache refreshed: userId={}", user.id());
		return user.id();
	}
}
