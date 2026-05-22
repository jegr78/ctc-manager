package org.ctc.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.dto.Role;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.dto.ThreadCreateRequest;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class DiscordRestClient {

	private static final ParameterizedTypeReference<List<Role>> ROLE_LIST = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<List<Channel>> CHANNEL_LIST = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<List<Emoji>> EMOJI_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient bot;

	public DiscordRestClient(@Qualifier("discordBotRestClient") RestClient bot) {
		this.bot = bot;
	}

	public BotUser fetchBotUser() throws DiscordApiException {
		return execute(() -> bot.get()
				.uri("/users/@me")
				.retrieve()
				.body(BotUser.class));
	}

	public List<Role> fetchGuildRoles(String guildId) throws DiscordApiException {
		return execute(() -> bot.get()
				.uri("/guilds/{guildId}/roles", guildId)
				.retrieve()
				.body(ROLE_LIST));
	}

	public Map<String, String> fetchGuildEmojis(String guildId) throws DiscordApiException {
		List<Emoji> emojis = execute(() -> bot.get()
				.uri("/guilds/{guildId}/emojis", guildId)
				.retrieve()
				.body(EMOJI_LIST));
		Map<String, String> out = new HashMap<>(emojis.size());
		for (Emoji e : emojis) {
			out.put(e.name(), "<:" + e.name() + ":" + e.id() + ">");
		}
		return out;
	}

	public Channel createChannel(String guildId, ChannelCreateRequest request) throws DiscordApiException {
		return execute(() -> bot.post()
				.uri("/guilds/{guildId}/channels", guildId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Channel.class));
	}

	public Channel modifyChannel(String channelId, ChannelModifyRequest request) throws DiscordApiException {
		return execute(() -> bot.patch()
				.uri("/channels/{channelId}", channelId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Channel.class));
	}

	public List<Channel> listChannels(String guildId) throws DiscordApiException {
		return execute(() -> bot.get()
				.uri("/guilds/{guildId}/channels", guildId)
				.retrieve()
				.body(CHANNEL_LIST));
	}

	public List<Thread> listActiveThreads(String guildId) throws DiscordApiException {
		ThreadList result = execute(() -> bot.get()
				.uri("/guilds/{guildId}/threads/active", guildId)
				.retrieve()
				.body(ThreadList.class));
		return result == null ? List.of() : result.threads();
	}

	public List<Thread> listArchivedThreads(String channelId) throws DiscordApiException {
		ThreadList result = execute(() -> bot.get()
				.uri("/channels/{channelId}/threads/archived/public", channelId)
				.retrieve()
				.body(ThreadList.class));
		return result == null ? List.of() : result.threads();
	}

	public Thread createThread(String channelId, ThreadCreateRequest request) throws DiscordApiException {
		return execute(() -> bot.post()
				.uri("/channels/{channelId}/threads", channelId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Thread.class));
	}

	public Webhook createWebhook(String channelId, String name) throws DiscordApiException {
		return execute(() -> bot.post()
				.uri("/channels/{channelId}/webhooks", channelId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new WebhookCreateRequest(name))
				.retrieve()
				.body(Webhook.class));
	}

	public Channel fetchChannel(String channelId) throws DiscordApiException {
		return execute(() -> bot.get()
				.uri("/channels/{channelId}", channelId)
				.retrieve()
				.body(Channel.class));
	}

	public void deleteChannel(String channelId) throws DiscordApiException {
		execute(() -> {
			bot.delete()
					.uri("/channels/{channelId}", channelId)
					.retrieve()
					.toBodilessEntity();
			return null;
		});
	}

	private static <T> T execute(RestCall<T> call) throws DiscordApiException {
		try {
			return call.run();
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

	@FunctionalInterface
	private interface RestCall<T> {
		T run();
	}

	public record BotUser(String id, String username, String discriminator) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Emoji(String id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ThreadList(List<Thread> threads) {
	}

	private record WebhookCreateRequest(String name) {
	}
}
