package org.ctc.discord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Role;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DiscordDevSeeder {

	private final DiscordDevSeedProperties properties;
	private final DiscordGlobalConfigService configService;
	private final DiscordGlobalConfigRepository configRepository;
	private final DiscordRestClient restClient;
	private final DiscordRoleCache roleCache;
	private final DiscordBotIdentityCache botIdentityCache;
	private final DiscordEmojiCache emojiCache;
	private final TeamRepository teamRepository;

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		if (!properties.enabled()) {
			log.info("Discord dev-seed disabled — skipping");
			return;
		}
		DiscordGlobalConfig cfg = configService.getOrInitialize();
		if (cfg == null) {
			log.warn("Discord dev-seed: configService returned null — skipping (test mock?)");
			return;
		}
		boolean templateBackfilled = backfillDefaultTemplates(cfg);
		if (!properties.hasGuildId()) {
			log.info("Discord dev-seed: no guild-id configured — skipping (set DISCORD_DEV_GUILD_ID to enable)");
			persistIfDirty(cfg, templateBackfilled);
			return;
		}
		if (cfg.getGuildId() != null && !cfg.getGuildId().isBlank()) {
			log.info("Discord dev-seed: DiscordGlobalConfig already populated (guildId={}) — skipping",
					cfg.getGuildId());
			persistIfDirty(cfg, templateBackfilled);
			return;
		}

		applyConfig(cfg);
		try {
			configRepository.save(cfg);
		} catch (RuntimeException e) {
			log.warn("Discord dev-seed: failed to persist DiscordGlobalConfig — aborting seed: {}",
					e.toString());
			return;
		}

		int assigned = -1;
		try {
			List<Role> roles = restClient.fetchGuildRoles(properties.guildId());
			roleCache.refresh(roles);
			botIdentityCache.refresh();
			assigned = assignTeamRoles(roles);
		} catch (DiscordApiException e) {
			log.warn("Discord dev-seed: roles/identity refresh failed — config persisted but role-cache + "
					+ "team-roleId assignments are not populated. Operator can click 'Refresh Server Roles' "
					+ "on /admin/discord-config once Discord is reachable. Cause: {}", e.toString());
		}

		int emojis = -1;
		try {
			emojis = emojiCache.refresh(restClient.fetchGuildEmojis(properties.guildId()));
		} catch (DiscordApiException e) {
			log.warn("Discord dev-seed: emoji refresh failed — operator can click 'Refresh Emoji Cache' "
					+ "on /admin/discord-config once Discord is reachable. Cause: {}", e.toString());
		}

		log.info("Discord dev-seed complete: guildId={}, currentMatchCategoryId={}, "
						+ "team-role-assignments={}, emoji-cache-entries={}",
				properties.guildId(), properties.currentMatchCategoryId(), assigned, emojis);
	}

	private boolean backfillDefaultTemplates(DiscordGlobalConfig cfg) {
		boolean dirty = false;
		if (cfg.getMatchdayPairingsTemplate() == null || cfg.getMatchdayPairingsTemplate().isBlank()) {
			cfg.setMatchdayPairingsTemplate(DiscordPostService.DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE);
			log.info("Discord dev-seed: seeded default Matchday-Pairings template");
			dirty = true;
		}
		return dirty;
	}

	private void persistIfDirty(DiscordGlobalConfig cfg, boolean dirty) {
		if (dirty) {
			try {
				configRepository.save(cfg);
			} catch (RuntimeException e) {
				log.warn("Discord dev-seed: failed to persist default-template backfill: {}", e.toString());
			}
		}
	}

	private void applyConfig(DiscordGlobalConfig cfg) {
		cfg.setGuildId(properties.guildId());
		if (properties.botAppId() != null && !properties.botAppId().isBlank()) {
			cfg.setBotApplicationId(properties.botAppId());
		}
		if (properties.currentMatchCategoryId() != null && !properties.currentMatchCategoryId().isBlank()) {
			cfg.setCurrentMatchCategoryId(properties.currentMatchCategoryId());
		}
		if (properties.raceResultsForumChannelId() != null && !properties.raceResultsForumChannelId().isBlank()) {
			cfg.setRaceResultsForumChannelId(properties.raceResultsForumChannelId());
		}
		if (properties.standingsForumChannelId() != null && !properties.standingsForumChannelId().isBlank()) {
			cfg.setStandingsForumChannelId(properties.standingsForumChannelId());
		}
		if (properties.vsEmojiName() != null && !properties.vsEmojiName().isBlank()) {
			cfg.setVsEmojiName(properties.vsEmojiName());
		}
		if (properties.announcementWebhookUrl() != null && !properties.announcementWebhookUrl().isBlank()) {
			cfg.setAnnouncementWebhookUrl(properties.announcementWebhookUrl());
		}
		if (properties.raceResultsForumWebhookUrl() != null && !properties.raceResultsForumWebhookUrl().isBlank()) {
			cfg.setRaceResultsForumWebhookUrl(properties.raceResultsForumWebhookUrl());
		}
		if (properties.standingsForumWebhookUrl() != null && !properties.standingsForumWebhookUrl().isBlank()) {
			cfg.setStandingsForumWebhookUrl(properties.standingsForumWebhookUrl());
		}
	}

	private int assignTeamRoles(List<Role> roles) {
		Map<String, String> roleIdByName = roles.stream()
				.collect(Collectors.toMap(Role::name, Role::id, (a, b) -> a));
		int assigned = 0;
		for (Team team : teamRepository.findAll()) {
			if (team.getDiscordRoleId() != null) {
				continue;
			}
			String roleId = roleIdByName.get(team.getShortName());
			if (roleId == null) {
				continue;
			}
			team.setDiscordRoleId(roleId);
			teamRepository.save(team);
			assigned++;
		}
		return assigned;
	}
}
