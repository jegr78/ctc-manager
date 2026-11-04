package org.ctc.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.dto.Role;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordDisabledException;
import org.ctc.integration.ExternalIntegrationDisabledException;
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
	private static final ParameterizedTypeReference<List<Webhook>> WEBHOOK_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient bot;

	public DiscordRestClient(@Qualifier("discordBotRestClient") RestClient bot) {
		this.bot = bot;
	}

    public void registerPicksGuildCommand(String applicationId, String guildId) throws DiscordApiException {
        execute(() -> bot.post().uri("/applications/{applicationId}/guilds/{guildId}/commands", applicationId, guildId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", 1, "name", "picks", "description", "Manage private team picks"))
                .retrieve().toBodilessEntity());
    }

    public AccessGuild fetchAccessGuild(String guildId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/guilds/{guildId}", guildId).retrieve().body(AccessGuild.class));
    }

    public AccessChannel fetchAccessChannel(String channelId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/channels/{channelId}", channelId).retrieve().body(AccessChannel.class));
    }

    public AccessMember fetchAccessMember(String guildId, String userId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/guilds/{guildId}/members/{userId}", guildId, userId).retrieve().body(AccessMember.class));
    }

    public List<PermissionRole> fetchPermissionRoles(String guildId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/guilds/{guildId}/roles", guildId).retrieve()
                .body(new ParameterizedTypeReference<List<PermissionRole>>() { }));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessGuild(String id, @JsonProperty("owner_id") String ownerId) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessChannel(String id, @JsonProperty("guild_id") String guildId, Integer type,
                                @JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessMember(BotUser user, List<String> roles) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionRole(String id, String permissions) { }

    public List<IncidentRole> fetchIncidentRoles(String guildId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/guilds/{guildId}/roles", guildId).retrieve()
                .body(new ParameterizedTypeReference<List<IncidentRole>>() { }));
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentRole(String id,String permissions,Boolean managed,IncidentRoleTags tags) { }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentRoleTags(@JsonProperty("bot_id") String botId) { }
    public IncidentChannel fetchIncidentChannel(String channelId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/channels/{channelId}",channelId).retrieve().body(IncidentChannel.class));
    }
    public IncidentChannel startIncidentMessageThread(String channelId,String starterId,String name) throws DiscordApiException {
        return execute(() -> bot.post().uri("/channels/{channelId}/messages/{messageId}/threads",channelId,starterId)
                .attribute(DiscordRateLimitInterceptor.UNCERTAIN_CREATION,true).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name",name,"auto_archive_duration",1440)).retrieve().body(IncidentChannel.class));
    }
    public IncidentChannel startIncidentForumThread(String channelId,String name,String content,List<String> tags) throws DiscordApiException {
        return execute(() -> bot.post().uri("/channels/{channelId}/threads",channelId)
                .attribute(DiscordRateLimitInterceptor.UNCERTAIN_CREATION,true).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name",name,"auto_archive_duration",1440,"applied_tags",List.copyOf(tags),
                        "message",Map.of("content",content,"allowed_mentions",Map.of("parse",List.of()))))
                .retrieve().body(IncidentChannel.class));
    }
    public IncidentThreadPage listIncidentArchivedThreads(String channelId,String before,int limit) throws DiscordApiException {
        return execute(() -> bot.get().uri(uri -> {
            var page=uri.path("/channels/{channelId}/threads/archived/public").queryParam("limit",limit);
            if(before!=null)page.queryParam("before",before);
            return page.build(channelId);
        }).retrieve().body(IncidentThreadPage.class));
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentChannel(String id,@JsonProperty("guild_id") String guildId,Integer type,@JsonProperty("parent_id") String parentId,String name,
            @JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites,Integer flags,@JsonProperty("available_tags") List<IncidentForumTag> availableTags,
            @JsonProperty("thread_metadata") IncidentThreadMetadata threadMetadata,BotMessage message) {
        public IncidentChannel {
            permissionOverwrites=permissionOverwrites==null?null:List.copyOf(permissionOverwrites);
            availableTags=availableTags==null?null:List.copyOf(availableTags);
        }
        @Override public String toString(){return "IncidentChannel[opaque]";}
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentForumTag(String id,String name,Boolean moderated) { }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentThreadMetadata(Boolean archived,Boolean locked,@JsonProperty("archive_timestamp") String archiveTimestamp) { }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record IncidentThreadPage(List<IncidentChannel> threads,@JsonProperty("has_more") Boolean hasMore) {
        public IncidentThreadPage {threads=threads==null?null:List.copyOf(threads);}
        @Override public String toString(){return "IncidentThreadPage[opaque]";}
    }

    public BotMessage createBotMessage(String channelId, String nonce, String content) throws DiscordApiException {
        return execute(() -> bot.post().uri("/channels/{channelId}/messages", channelId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content, "allowed_mentions", Map.of("parse", List.of()), "nonce", nonce, "enforce_nonce", true))
                .retrieve().body(BotMessage.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotMessage(String id, @JsonProperty("channel_id") String channelId, String content, BotUser author, String nonce) {
        @Override public String toString() { return "BotMessage[opaque]"; }
    }
    public List<BotMessage> listIncidentMessages(String channelId,String before,int limit) throws DiscordApiException {
        return execute(() -> bot.get().uri(uri -> {
            var page=uri.path("/channels/{channelId}/messages").queryParam("limit",limit);
            if(before!=null)page.queryParam("before",before);
            return page.build(channelId);
        }).retrieve().body(new ParameterizedTypeReference<List<BotMessage>>() { }));
    }
    public BotMessage fetchBotMessage(String channelId,String messageId) throws DiscordApiException {
        return execute(() -> bot.get().uri("/channels/{channelId}/messages/{messageId}",channelId,messageId).retrieve().body(BotMessage.class));
    }

	public BotUser fetchBotUser() throws DiscordApiException {
		return execute(() -> bot.get()
				.uri("/users/@me")
				.retrieve()
				.body(BotUser.class));
	}

	public List<Role> fetchGuildRoles(String guildId) throws DiscordApiException {
		List<Role> roles = execute(() -> bot.get()
				.uri("/guilds/{guildId}/roles", guildId)
				.retrieve()
				.body(ROLE_LIST));
		return roles == null ? List.of() : roles;
	}

	public Map<String, String> fetchGuildEmojis(String guildId) throws DiscordApiException {
		List<Emoji> emojis = execute(() -> bot.get()
				.uri("/guilds/{guildId}/emojis", guildId)
				.retrieve()
				.body(EMOJI_LIST));
		if (emojis == null) {
			return Map.of();
		}
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
		List<Channel> channels = execute(() -> bot.get()
				.uri("/guilds/{guildId}/channels", guildId)
				.retrieve()
				.body(CHANNEL_LIST));
		return channels == null ? List.of() : channels;
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

	public Webhook createWebhook(String channelId, String name, String avatar) throws DiscordApiException {
		return execute(() -> bot.post()
				.uri("/channels/{channelId}/webhooks", channelId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new WebhookCreateRequest(name, avatar))
				.retrieve()
				.body(Webhook.class));
	}

	public List<Webhook> listWebhooks(String channelId) throws DiscordApiException {
		List<Webhook> webhooks = execute(() -> bot.get()
				.uri("/channels/{channelId}/webhooks", channelId)
				.retrieve()
				.body(WEBHOOK_LIST));
		return webhooks == null ? List.of() : webhooks;
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
		if (cause instanceof ExternalIntegrationDisabledException) {
			return new DiscordDisabledException(cause);
		}
		if (cause instanceof DiscordApiException dae) {
			return dae;
		}
		return new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
	}

	@FunctionalInterface
	private interface RestCall<T> {
		T run();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BotUser(String id, String username, String discriminator) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Emoji(String id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ThreadList(List<Thread> threads) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record WebhookCreateRequest(String name, String avatar) {
	}
}
