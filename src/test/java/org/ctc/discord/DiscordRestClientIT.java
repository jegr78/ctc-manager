package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.dto.Role;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.dto.ThreadCreateRequest;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordCategoryFullException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordRestClientIT {

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
	private DiscordRestClient client;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	@Test
	void given200_whenFetchBotUser_thenReturnsBotUser() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));

		assertThat(client.fetchBotUser().username()).isEqualTo("CTC-Bot");
	}

	@Test
	void given200_whenFetchGuildRoles_thenReturnsRoles() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/roles"))
				.willReturn(okJson("[{\"id\":\"r1\",\"name\":\"Admin\",\"position\":3},{\"id\":\"r2\",\"name\":\"Team\",\"position\":2}]")));

		var roles = client.fetchGuildRoles("g1");

		assertThat(roles).extracting(Role::name).containsExactly("Admin", "Team");
	}

	@Test
	void given200_whenFetchGuildEmojis_thenReturnsLongFormMap() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/emojis"))
				.willReturn(okJson("[{\"id\":\"100\",\"name\":\"flag_de\"},{\"id\":\"101\",\"name\":\"trophy\"}]")));

		var emojis = client.fetchGuildEmojis("g1");

		assertThat(emojis).containsEntry("flag_de", "<:flag_de:100>").containsEntry("trophy", "<:trophy:101>");
	}

	@Test
	void given200_whenCreateChannel_thenReturnsChannel() throws Exception {
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"matchday-1\",\"type\":0,\"parent_id\":\"cat1\"}")));

		Channel ch = client.createChannel("g1", new ChannelCreateRequest("matchday-1", 0, "cat1"));

		assertThat(ch.id()).isEqualTo("c1");
		assertThat(ch.name()).isEqualTo("matchday-1");
		wm.verify(postRequestedFor(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.withRequestBody(equalToJson("{\"name\":\"matchday-1\",\"type\":0,\"parent_id\":\"cat1\"}")));
	}

	@Test
	void given200_whenModifyChannel_thenReturnsUpdatedChannel() throws Exception {
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"archive-c1\",\"type\":0,\"parent_id\":\"archive-cat\"}")));

		Channel ch = client.modifyChannel("c1", new ChannelModifyRequest("archive-c1", "archive-cat"));

		assertThat(ch.name()).isEqualTo("archive-c1");
		assertThat(ch.parentId()).isEqualTo("archive-cat");
	}

	@Test
	void given200_whenListChannels_thenReturnsChannels() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson("[{\"id\":\"c1\",\"name\":\"a\",\"type\":0,\"parent_id\":null},{\"id\":\"c2\",\"name\":\"b\",\"type\":4,\"parent_id\":null}]")));

		var channels = client.listChannels("g1");

		assertThat(channels).extracting(Channel::id).containsExactly("c1", "c2");
	}

	@Test
	void given200_whenListActiveThreads_thenReturnsThreads() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/threads/active"))
				.willReturn(okJson("{\"threads\":[{\"id\":\"t1\",\"name\":\"forum-1\",\"parent_id\":\"f1\"}]}")));

		var threads = client.listActiveThreads("g1");

		assertThat(threads).extracting(Thread::id).containsExactly("t1");
	}

	@Test
	void given200_whenListArchivedThreads_thenReturnsThreads() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/f1/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[{\"id\":\"t9\",\"name\":\"old-forum\",\"parent_id\":\"f1\"}]}")));

		var threads = client.listArchivedThreads("f1");

		assertThat(threads).extracting(Thread::name).containsExactly("old-forum");
	}

	@Test
	void given200_whenCreateThread_thenReturnsThread() throws Exception {
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/f1/threads"))
				.willReturn(okJson("{\"id\":\"t100\",\"name\":\"matchday-1\",\"parent_id\":\"f1\"}")));

		Thread th = client.createThread("f1", new ThreadCreateRequest("matchday-1", 11));

		assertThat(th.id()).isEqualTo("t100");
	}

	@Test
	void given401_whenFetchBotUser_thenThrowsDiscordAuthException() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		assertThatThrownBy(() -> client.fetchBotUser()).isInstanceOf(DiscordAuthException.class);
	}

	@Test
	void given404_whenFetchGuildRoles_thenThrowsDiscordNotFoundException() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g404/roles"))
				.willReturn(aResponse().withStatus(404)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unknown Guild\",\"code\":10004}")));

		assertThatThrownBy(() -> client.fetchGuildRoles("g404")).isInstanceOf(DiscordNotFoundException.class);
	}

	@Test
	void given400Code30013_whenCreateChannel_thenThrowsDiscordCategoryFullException() {
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/gFull/channels"))
				.willReturn(aResponse().withStatus(400)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Maximum number of channels in category reached\",\"code\":30013}")));

		assertThatThrownBy(() -> client.createChannel("gFull", new ChannelCreateRequest("x", 0, "cat1")))
				.isInstanceOf(DiscordCategoryFullException.class);
	}

	@Test
	void given5xxExhausted_whenFetchBotUser_thenThrowsDiscordTransientException() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> client.fetchBotUser()).isInstanceOf(DiscordTransientException.class);
	}

	@Test
	void givenChannelId_whenCreateWebhook_thenReturnsWebhook() throws Exception {
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(okJson("{\"id\":\"w1\",\"token\":\"tok-abc\","
						+ "\"url\":\"https://discord.com/api/webhooks/w1/tok-abc\",\"channel_id\":\"c1\"}")));

		Webhook hook = client.createWebhook("c1", "CTC Manager");

		assertThat(hook.id()).isEqualTo("w1");
		assertThat(hook.token()).isEqualTo("tok-abc");
		assertThat(hook.url()).isEqualTo("https://discord.com/api/webhooks/w1/tok-abc");
		assertThat(hook.channelId()).isEqualTo("c1");
		wm.verify(postRequestedFor(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.withRequestBody(equalToJson("{\"name\":\"CTC Manager\"}")));
	}

	@Test
	void givenChannelId_whenFetchChannel_thenReturnsChannelWithPermissionOverwrites() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",\"type\":0,"
						+ "\"parent_id\":\"cat1\","
						+ "\"permission_overwrites\":["
						+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
						+ "{\"id\":\"home\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
						+ "{\"id\":\"away\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"}"
						+ "]}")));

		Channel ch = client.fetchChannel("c1");

		assertThat(ch.id()).isEqualTo("c1");
		assertThat(ch.permissionOverwrites()).hasSize(3);
		assertThat(ch.permissionOverwrites()).extracting("id").containsExactly("g1", "home", "away");
		wm.verify(getRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
	}

	@Test
	void givenChannelId_whenDeleteChannel_thenInvokesDelete() throws Exception {
		wm.stubFor(delete(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(204)));

		client.deleteChannel("c1");

		wm.verify(deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
	}

	@Test
	void givenChannelId_whenDeleteChannelReturns500_thenDiscordTransientExceptionThrown() {
		wm.stubFor(delete(urlPathEqualTo("/api/v10/channels/c500"))
				.willReturn(aResponse().withStatus(500)));

		assertThatThrownBy(() -> client.deleteChannel("c500"))
				.isInstanceOf(DiscordTransientException.class);
	}
}
