package org.ctc.discord.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.exception.DiscordApiException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class DiscordForumService {

	private static final Comparator<Thread> THREAD_PICKER_ORDER = Comparator
			.comparing(Thread::pinned, Comparator.reverseOrder())
			.thenComparing(Thread::archived, Comparator.naturalOrder())
			.thenComparing(Thread::lastMessageId, Comparator.nullsLast(Comparator.reverseOrder()));

	private final DiscordRestClient restClient;
	private final DiscordGlobalConfigService globalConfigService;

	public List<Thread> listThreads(String forumChannelId) throws DiscordApiException {
		String guildId = globalConfigService.getOrInitialize().getGuildId();
		if (!hasText(guildId)) {
			log.warn("listThreads({}) called with no guildId configured — returning empty list", forumChannelId);
			return List.of();
		}
		List<Thread> active = restClient.listActiveThreads(guildId).stream()
				.filter(t -> Objects.equals(t.parentId(), forumChannelId))
				.toList();
		List<Thread> archived = restClient.listArchivedThreads(forumChannelId);
		return Stream.concat(active.stream(), archived.stream())
				.sorted(THREAD_PICKER_ORDER)
				.toList();
	}
}
