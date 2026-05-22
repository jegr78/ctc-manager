package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ctc.discord.DiscordRestClient.BotUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordBotIdentityCacheTest {

	@Mock
	private DiscordRestClient restClient;

	@InjectMocks
	private DiscordBotIdentityCache cache;

	@Test
	void givenColdCache_whenGetBotUserId_thenFetchedFromRestClientAndCached() throws Exception {
		// given
		when(restClient.fetchBotUser()).thenReturn(new BotUser("123", "CTC-Bot", "0001"));

		// when
		String botUserId = cache.getBotUserId();

		// then
		assertThat(botUserId).isEqualTo("123");
		verify(restClient, times(1)).fetchBotUser();
	}

	@Test
	void givenCachedValue_whenGetBotUserId_thenNoSecondRestClientCall() throws Exception {
		// given
		when(restClient.fetchBotUser()).thenReturn(new BotUser("123", "CTC-Bot", "0001"));
		cache.getBotUserId();

		// when
		String botUserId = cache.getBotUserId();

		// then
		assertThat(botUserId).isEqualTo("123");
		verify(restClient, times(1)).fetchBotUser();
	}

	@Test
	void givenCachedValue_whenRefresh_thenRestClientReFetchedAndCacheUpdated() throws Exception {
		// given
		when(restClient.fetchBotUser())
				.thenReturn(new BotUser("123", "CTC-Bot", "0001"))
				.thenReturn(new BotUser("456", "CTC-Bot-Rotated", "0002"));
		cache.getBotUserId();

		// when
		String refreshed = cache.refresh();

		// then
		assertThat(refreshed).isEqualTo("456");
		assertThat(cache.getBotUserId()).isEqualTo("456");
		verify(restClient, times(2)).fetchBotUser();
	}
}
