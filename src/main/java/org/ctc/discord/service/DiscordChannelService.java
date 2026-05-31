package org.ctc.discord.service;

import static org.ctc.discord.DiscordPermissions.BOT_ALLOW_MASK;
import static org.ctc.discord.DiscordPermissions.EVERYONE_DENY_VIEW_MASK;
import static org.ctc.discord.DiscordPermissions.OVERWRITE_TYPE_MEMBER;
import static org.ctc.discord.DiscordPermissions.OVERWRITE_TYPE_ROLE;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_ALLOW_MASK;
import static org.ctc.discord.DiscordPermissions.TEAM_MEMBER_DENY_MASK;
import static org.ctc.discord.DiscordPermissions.VIEW_CHANNEL;
import static org.springframework.util.StringUtils.hasText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordBotIdentityCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.event.ChannelCreatedEvent;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.MatchRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelService {

	private static final String WEBHOOK_NAME = "CTC Manager";
	private static final int CHANNEL_TYPE_TEXT = 0;
	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
	private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]");
	private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
	private static final Pattern EDGE_DASH = Pattern.compile("^-|-$");

	private final DiscordRestClient restClient;
	private final DiscordGlobalConfigService configService;
	private final DiscordBotIdentityCache botIdentityCache;
	private final MatchRepository matchRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void createMatchChannel(Match match) throws DiscordApiException {
		DiscordGlobalConfig cfg = configService.getOrInitialize();
		assertPreconditions(match, cfg);

		String homeRoleId = match.getHomeTeam().getEffectiveDiscordRoleId();
		String awayRoleId = match.getAwayTeam().getEffectiveDiscordRoleId();
		String guildId = cfg.getGuildId();
		String name = channelName(match);
		String botUserId = botIdentityCache.getBotUserId();

		Set<String> teamRoleIds = new LinkedHashSet<>();
		teamRoleIds.add(homeRoleId);
		teamRoleIds.add(awayRoleId);

		List<PermissionOverwrite> overwrites = new ArrayList<>();
		overwrites.add(new PermissionOverwrite(guildId, OVERWRITE_TYPE_ROLE,
				"0", String.valueOf(EVERYONE_DENY_VIEW_MASK)));
		for (String teamRoleId : teamRoleIds) {
			overwrites.add(new PermissionOverwrite(teamRoleId, OVERWRITE_TYPE_ROLE,
					String.valueOf(TEAM_MEMBER_ALLOW_MASK), String.valueOf(TEAM_MEMBER_DENY_MASK)));
		}
		overwrites.add(new PermissionOverwrite(botUserId, OVERWRITE_TYPE_MEMBER,
				String.valueOf(BOT_ALLOW_MASK), "0"));

		ChannelCreateRequest req = new ChannelCreateRequest(
				name, CHANNEL_TYPE_TEXT, cfg.getCurrentMatchCategoryId(), List.copyOf(overwrites));
		Channel channel = restClient.createChannel(guildId, req);
		Webhook webhook;
		try {
			webhook = restClient.createWebhook(channel.id(), WEBHOOK_NAME);
		} catch (DiscordApiException webhookEx) {
			try {
				restClient.deleteChannel(channel.id());
			} catch (DiscordApiException cleanupEx) {
				log.warn("Webhook-fail cleanup DELETE failed for channel {}: {}",
						channel.id(), cleanupEx.toString());
				throw new DiscordTransientException(
						DiscordApiExceptionMapper.TRANSIENT_MESSAGE
								+ " Cleanup failed: please manually delete channel "
								+ channel.id() + " via Discord.",
						webhookEx);
			}
			throw webhookEx;
		}

		try {
			assertPermissionAudit(channel.id(), teamRoleIds, botUserId);
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

		eventPublisher.publishEvent(new ChannelCreatedEvent(match.getId()));
	}

	@Transactional
	public void linkExistingChannel(Match match, String channelId) throws DiscordApiException {
		restClient.fetchChannel(channelId);

		List<Webhook> existing = restClient.listWebhooks(channelId);
		String webhookUrl = null;
		for (Webhook webhook : existing) {
			if (WEBHOOK_NAME.equals(webhook.name()) && hasText(webhook.url())) {
				webhookUrl = webhook.url();
				break;
			}
		}
		if (webhookUrl == null) {
			webhookUrl = restClient.createWebhook(channelId, WEBHOOK_NAME).url();
		}

		match.setDiscordChannelId(channelId);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		matchRepository.save(match);
		// No ChannelCreatedEvent: linking a prepared channel must not auto-post Team Cards
		// (DiscordAutoPostListener.onChannelCreated does) — the operator uses the explicit button.
		log.info("Discord channel linked to match {} → channelId={}", match.getId(), channelId);
	}

	private static void assertPreconditions(Match match, DiscordGlobalConfig cfg) {
		boolean missing = match.getHomeTeam() == null
				|| match.getAwayTeam() == null
				|| match.getHomeTeam().getEffectiveDiscordRoleId() == null
				|| match.getAwayTeam().getEffectiveDiscordRoleId() == null
				|| !hasText(cfg.getCurrentMatchCategoryId());
		if (missing) {
			throw new BusinessRuleException(
					"Channel creation requires both team Discord roles and a current match category.");
		}
	}

	static String channelName(Match match) {
		Matchday matchday = match.getMatchday();
		if (matchday == null || matchday.getPhase() == null
				|| match.getHomeTeam() == null || match.getAwayTeam() == null) {
			throw new BusinessRuleException(
					"Channel name requires matchday with phase and both teams.");
		}
		int matchdayNumber = matchday.getSortIndex() + 1;
		String phaseAbbrev = phaseAbbrev(matchday.getPhase().getPhaseType());
		SeasonPhaseGroup group = matchday.getGroup();
		String groupToken = "";
		if (group != null) {
			String slug = groupSlug(group);
			if (!slug.isEmpty()) {
				groupToken = slug + "-";
			}
		}
		String name = ("md" + matchdayNumber + "-"
				+ phaseAbbrev + "-"
				+ groupToken
				+ match.getHomeTeam().getShortName()
				+ "-vs-"
				+ match.getAwayTeam().getShortName())
				.toLowerCase(Locale.ROOT);
		if (name.length() > 100) {
			throw new BusinessRuleException(
					"Discord channel name exceeds 100 characters: " + name + " (" + name.length() + ")");
		}
		return name;
	}

	private static String phaseAbbrev(PhaseType type) {
		// No default branch: PhaseType is exhaustive — adding a new value must force a re-mapping here.
		return switch (type) {
			case REGULAR -> "rs";
			case PLAYOFF -> "po";
			case PLACEMENT -> "pm";
		};
	}

	private static String groupSlug(SeasonPhaseGroup group) {
		String decomposed = Normalizer.normalize(group.getName(), Normalizer.Form.NFD);
		String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
		String lowered = stripped.toLowerCase(Locale.ROOT);
		String slugged = NON_SLUG_CHARS.matcher(lowered).replaceAll("-");
		String collapsed = MULTI_DASH.matcher(slugged).replaceAll("-");
		return EDGE_DASH.matcher(collapsed).replaceAll("");
	}

	private void assertPermissionAudit(String channelId, Set<String> expectedTeamRoleIds, String botUserId)
			throws DiscordApiException {
		int expectedSize = 2 + expectedTeamRoleIds.size();
		Channel back = restClient.fetchChannel(channelId);
		List<PermissionOverwrite> overwrites = back.permissionOverwrites();
		if (overwrites == null || overwrites.size() != expectedSize) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
		Set<String> rolesWithView = overwrites.stream()
				.filter(o -> o.type() == OVERWRITE_TYPE_ROLE)
				.filter(o -> (parseAllow(o.allow()) & VIEW_CHANNEL) != 0L)
				.map(PermissionOverwrite::id)
				.collect(Collectors.toSet());
		if (!rolesWithView.equals(expectedTeamRoleIds)) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
		Set<String> membersWithView = overwrites.stream()
				.filter(o -> o.type() == OVERWRITE_TYPE_MEMBER)
				.filter(o -> (parseAllow(o.allow()) & VIEW_CHANNEL) != 0L)
				.map(PermissionOverwrite::id)
				.collect(Collectors.toSet());
		if (!membersWithView.equals(Set.of(botUserId))) {
			throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
		}
	}

	private static long parseAllow(String allow) {
		if (!hasText(allow)) {
			return 0L;
		}
		try {
			return Long.parseLong(allow.trim());
		} catch (NumberFormatException ex) {
			log.warn("Discord audit returned non-numeric allow value: {}", allow);
			return 0L;
		}
	}
}
