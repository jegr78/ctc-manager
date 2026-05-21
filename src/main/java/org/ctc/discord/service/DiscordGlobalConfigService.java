package org.ctc.discord.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class DiscordGlobalConfigService {

	private final DiscordGlobalConfigRepository repo;

	@Transactional
	public DiscordGlobalConfig getOrInitialize() {
		DiscordGlobalConfig existing = repo.findFirstByOrderByIdAsc();
		if (existing != null) {
			return existing;
		}
		log.warn("discord_global_config seed row missing — inserting empty defaults");
		DiscordGlobalConfig fresh = new DiscordGlobalConfig();
		return repo.save(fresh);
	}

	@Transactional
	public DiscordGlobalConfig save(DiscordConfigForm form) {
		DiscordGlobalConfig current = getOrInitialize();
		current.setGuildId(nullSafe(form.getGuildId()));
		current.setAnnouncementWebhookUrl(nullSafe(form.getAnnouncementWebhookUrl()));
		current.setRaceResultsForumChannelId(nullSafe(form.getRaceResultsForumChannelId()));
		current.setStandingsForumChannelId(nullSafe(form.getStandingsForumChannelId()));
		current.setVsEmojiName(nullSafe(form.getVsEmojiName()));
		current.setBotApplicationId(form.getBotApplicationId());
		current.setCurrentMatchCategoryId(nullSafe(form.getCurrentMatchCategoryId()));
		DiscordGlobalConfig saved = repo.save(current);
		log.info("Updated discord_global_config (id={}, guildId={})", saved.getId(), saved.getGuildId());
		return saved;
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
