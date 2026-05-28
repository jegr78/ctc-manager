package org.ctc.discord.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.exception.DiscordApiException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCategoryResolver {

	private static final Pattern ARCHIVE_NAME =
			Pattern.compile("^Match Days Archive (?<year>\\d{4})(?: \\((?<num>\\d+)\\))?$");
	private static final int CATEGORY_TYPE = 4;
	private static final int CATEGORY_LIMIT = 50;

	private final DiscordRestClient restClient;
	private final DiscordGlobalConfigService configService;

	public List<ArchiveCategory> resolveArchiveCategoriesFor(int year) throws DiscordApiException {
		String guildId = configService.getOrInitialize().getGuildId();
		List<Channel> all = restClient.listChannels(guildId);
		return all.stream()
				.filter(c -> c.type() == CATEGORY_TYPE)
				.map(c -> matchYear(c, year, all))
				.flatMap(Optional::stream)
				.sorted(Comparator.comparingInt(ArchiveCategory::num))
				.toList();
	}

	public Optional<ArchiveCategory> defaultSelection(List<ArchiveCategory> categories) {
		return categories.stream()
				.filter(c -> c.currentChannelCount() < CATEGORY_LIMIT)
				.max(Comparator.comparingInt(ArchiveCategory::num));
	}

	private static Optional<ArchiveCategory> matchYear(Channel cat, int year, List<Channel> all) {
		Matcher m = ARCHIVE_NAME.matcher(cat.name());
		if (!m.matches()) {
			return Optional.empty();
		}
		int parsedYear = Integer.parseInt(m.group("year"));
		if (parsedYear != year) {
			return Optional.empty();
		}
		String numGroup = m.group("num");
		int num = numGroup == null ? 1 : Integer.parseInt(numGroup);
		int count = (int) all.stream().filter(c -> cat.id().equals(c.parentId())).count();
		return Optional.of(new ArchiveCategory(cat.id(), cat.name(), num, count));
	}
}
