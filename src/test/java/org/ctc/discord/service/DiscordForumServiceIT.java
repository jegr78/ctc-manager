package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordForumServiceIT {

	private static final String GUILD_ID = "111111111111111111";
	private static final String FORUM_ID = "222222222222222222";

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	DiscordForumService service;

	@Autowired
	DiscordGlobalConfigService configService;

	@BeforeEach
	void seedGuildId() {
		DiscordGlobalConfig config = configService.getOrInitialize();
		config.setGuildId(GUILD_ID);
	}

	@Test
	void givenActiveAndArchivedThreadsAcrossForums_whenListThreads_thenReturnsSortedFilteredList() throws DiscordApiException {
		String activePayload = """
				{
				  "threads": [
				    {"id":"1","name":"pinned","parent_id":"%s","flags":2,"last_message_id":"500"},
				    {"id":"2","name":"active-new","parent_id":"%s","flags":0,"last_message_id":"400"},
				    {"id":"3","name":"active-old","parent_id":"%s","flags":0,"last_message_id":"200"},
				    {"id":"99","name":"other-forum","parent_id":"other","flags":0,"last_message_id":"999"}
				  ]
				}
				""".formatted(FORUM_ID, FORUM_ID, FORUM_ID);
		String archivedPayload = """
				{
				  "threads": [
				    {"id":"4","name":"arch-new","parent_id":"%s","flags":0,"last_message_id":"300","thread_metadata":{"archived":true}},
				    {"id":"5","name":"arch-old","parent_id":"%s","flags":0,"last_message_id":"100","thread_metadata":{"archived":true}}
				  ]
				}
				""".formatted(FORUM_ID, FORUM_ID);

		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(okJson(activePayload)));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_ID + "/threads/archived/public"))
				.willReturn(okJson(archivedPayload)));

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).extracting(Thread::id).containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	void givenEmptyActiveAndArchivedPayloads_whenListThreads_thenReturnsEmpty() throws DiscordApiException {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(okJson("{\"threads\":[]}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_ID + "/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[]}")));

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).isEmpty();
	}

	@Test
	void givenAuthFailureOnListActive_whenListThreads_thenThrowsAuthException() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(aResponse().withStatus(401).withBody("{}")));

		assertThatThrownBy(() -> service.listThreads(FORUM_ID))
				.isInstanceOf(DiscordAuthException.class);
	}

	@Test
	void givenServerErrorOnListArchived_whenListThreads_thenThrowsTransientException() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(okJson("{\"threads\":[]}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_ID + "/threads/archived/public"))
				.willReturn(aResponse().withStatus(500).withBody("")));

		assertThatThrownBy(() -> service.listThreads(FORUM_ID))
				.isInstanceOf(DiscordTransientException.class);
	}
}
