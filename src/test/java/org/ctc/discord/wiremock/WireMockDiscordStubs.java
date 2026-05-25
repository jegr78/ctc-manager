package org.ctc.discord.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

public final class WireMockDiscordStubs {

	private WireMockDiscordStubs() {
	}

	public static void stubCreateChannel(WireMockExtension wm, String guildId, long channelSnowflake) {
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/" + guildId + "/channels"))
				.willReturn(okJson("{\"id\":\"" + channelSnowflake + "\",\"name\":\"match-channel\",\"type\":0}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubCreateWebhook(WireMockExtension wm, long channelSnowflake, long webhookSnowflake,
			String token) {
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/" + channelSnowflake + "/webhooks"))
				.willReturn(okJson("{\"id\":\"" + webhookSnowflake + "\",\"token\":\"" + token
						+ "\",\"channel_id\":\"" + channelSnowflake + "\",\"name\":\"CTC Manager\"}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubExecuteWebhook(WireMockExtension wm, long webhookId, String token, long messageSnowflake) {
		wm.stubFor(post(urlPathEqualTo("/api/v10/webhooks/" + webhookId + "/" + token))
				.withQueryParam("wait", equalTo("true"))
				.willReturn(okJson("{\"id\":\"" + messageSnowflake + "\",\"channel_id\":\"channel-stub\"}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubExecuteWebhookForumThread(WireMockExtension wm, long webhookId, String token,
			long threadId, long messageSnowflake) {
		wm.stubFor(post(urlPathEqualTo("/api/v10/webhooks/" + webhookId + "/" + token))
				.withQueryParam("wait", equalTo("true"))
				.withQueryParam("thread_id", equalTo(String.valueOf(threadId)))
				.willReturn(okJson("{\"id\":\"" + messageSnowflake + "\",\"channel_id\":\"" + threadId + "\"}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubPatchMessage(WireMockExtension wm, long webhookId, String token, long messageSnowflake) {
		wm.stubFor(patch(urlPathEqualTo(
				"/api/v10/webhooks/" + webhookId + "/" + token + "/messages/" + messageSnowflake))
				.willReturn(okJson("{\"id\":\"" + messageSnowflake + "\",\"channel_id\":\"channel-stub\"}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubArchiveChannel(WireMockExtension wm, long channelSnowflake, long archiveCategoryId) {
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/" + channelSnowflake))
				.willReturn(okJson("{\"id\":\"" + channelSnowflake + "\",\"parent_id\":\"" + archiveCategoryId
						+ "\",\"name\":\"match-channel\",\"type\":0}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubListGuildRoles(WireMockExtension wm, String guildId) {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + guildId + "/roles"))
				.willReturn(okJson("[]")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubListGuildChannels(WireMockExtension wm, String guildId) {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + guildId + "/channels"))
				.willReturn(okJson("[]")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubFetchBotUser(WireMockExtension wm, String botUserSnowflake) {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"" + botUserSnowflake + "\",\"username\":\"CTC Bot\"}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}

	public static void stubFetchChannelNotArchived(WireMockExtension wm, long channelSnowflake) {
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + channelSnowflake))
				.willReturn(okJson("{\"id\":\"" + channelSnowflake
						+ "\",\"name\":\"forum-thread\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false,\"locked\":false}}")
						.withHeader("X-RateLimit-Remaining", "5")
						.withHeader("X-RateLimit-Reset-After", "0.5")));
	}
}
