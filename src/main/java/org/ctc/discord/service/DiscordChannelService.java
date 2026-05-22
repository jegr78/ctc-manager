package org.ctc.discord.service;

import static org.ctc.discord.DiscordPermissions.BOT_ALLOW_MASK;
import static org.ctc.discord.DiscordPermissions.EVERYONE_DENY_VIEW_MASK;
import static org.ctc.discord.DiscordPermissions.OVERWRITE_TYPE_MEMBER;
import static org.ctc.discord.DiscordPermissions.OVERWRITE_TYPE_ROLE;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_ALLOW_MASK;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_DENY_MASK;
import static org.ctc.discord.DiscordPermissions.VIEW_CHANNEL;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordBotIdentityCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelService {

	private static final String WEBHOOK_NAME = "CTC Manager";
	private static final int CHANNEL_TYPE_TEXT = 0;

	private final DiscordRestClient restClient;
	private final DiscordGlobalConfigService configService;
	private final DiscordBotIdentityCache botIdentityCache;
	private final MatchRepository matchRepository;

	@Transactional
	public void createMatchChannel(Match match) throws DiscordApiException {
		DiscordGlobalConfig cfg = configService.getOrInitialize();
		assertPreconditions(match, cfg);

		String homeRoleId = match.getHomeTeam().getDiscordRoleId();
		String awayRoleId = match.getAwayTeam().getDiscordRoleId();
		String guildId = cfg.getGuildId();
		String name = channelName(match);
		String botUserId = botIdentityCache.getBotUserId();

		List<PermissionOverwrite> overwrites = List.of(
				new PermissionOverwrite(guildId, OVERWRITE_TYPE_ROLE,
						"0", String.valueOf(EVERYONE_DENY_VIEW_MASK)),
				new PermissionOverwrite(homeRoleId, OVERWRITE_TYPE_ROLE,
						String.valueOf(TEAM_MEMBER_ALLOW_MASK), String.valueOf(TEAM_MEMBER_DENY_MASK)),
				new PermissionOverwrite(awayRoleId, OVERWRITE_TYPE_ROLE,
						String.valueOf(TEAM_MEMBER_ALLOW_MASK), String.valueOf(TEAM_MEMBER_DENY_MASK)),
				new PermissionOverwrite(botUserId, OVERWRITE_TYPE_MEMBER,
						String.valueOf(BOT_ALLOW_MASK), "0"));

		ChannelCreateRequest req = new ChannelCreateRequest(
				name, CHANNEL_TYPE_TEXT, cfg.getCurrentMatchCategoryId(), overwrites);
		Channel channel = restClient.createChannel(guildId, req);
		Webhook webhook = restClient.createWebhook(channel.id(), WEBHOOK_NAME);

		try {
			assertPermissionAudit(channel.id(), homeRoleId, awayRoleId, botUserId);
		} catch (DiscordAuthException auditEx) {
			try {
				restClient.deleteChannel(channel.id());
			} catch (DiscordApiException cleanupEx) {
				log.warn("Audit-fail cleanup DELETE failed for channel {}: {}",
						channel.id(), cleanupEx.toString());
				throw new DiscordAuthException(
						DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE
								+ " Cleanup failed: please manually delete channel "
								+ channel.id() + " via Discord.",
						auditEx);
			}
			throw auditEx;
		}

		match.setDiscordChannelId(channel.id());
		match.setDiscordChannelWebhookUrl(webhook.url());
		matchRepository.save(match);
		log.info("Discord channel created for match {} → {} (channelId={})",
				match.getId(), channel.name(), channel.id());
	}

	private static void assertPreconditions(Match match, DiscordGlobalConfig cfg) {
		boolean missing = match.getHomeTeam() == null
				|| match.getAwayTeam() == null
				|| match.getHomeTeam().getDiscordRoleId() == null
				|| match.getAwayTeam().getDiscordRoleId() == null
				|| cfg.getCurrentMatchCategoryId() == null
				|| cfg.getCurrentMatchCategoryId().isBlank();
		if (missing) {
			throw new BusinessRuleException(
					"Channel creation requires both team Discord roles and a current match category.");
		}
	}

	private static String channelName(Match match) {
		int matchdayNumber = match.getMatchday().getSortIndex() + 1;
		return ("md" + matchdayNumber + "-"
				+ match.getHomeTeam().getShortName()
				+ "-vs-"
				+ match.getAwayTeam().getShortName())
				.toLowerCase(Locale.ROOT);
	}

	private void assertPermissionAudit(String channelId, String homeRoleId, String awayRoleId, String botUserId)
			throws DiscordApiException {
		Channel back = restClient.fetchChannel(channelId);
		List<PermissionOverwrite> overwrites = back.permissionOverwrites();
		if (overwrites == null || overwrites.size() != 4) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
		Set<String> rolesWithView = overwrites.stream()
				.filter(o -> o.type() == OVERWRITE_TYPE_ROLE)
				.filter(o -> (Long.parseLong(o.allow()) & VIEW_CHANNEL) != 0L)
				.map(PermissionOverwrite::id)
				.collect(Collectors.toSet());
		if (!rolesWithView.equals(Set.of(homeRoleId, awayRoleId))) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
		Set<String> membersWithView = overwrites.stream()
				.filter(o -> o.type() == OVERWRITE_TYPE_MEMBER)
				.filter(o -> (Long.parseLong(o.allow()) & VIEW_CHANNEL) != 0L)
				.map(PermissionOverwrite::id)
				.collect(Collectors.toSet());
		if (!membersWithView.equals(Set.of(botUserId))) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
	}
}
