package org.ctc.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordForumService;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordSeasonViewService {

	private final DiscordGlobalConfigService discordGlobalConfigService;
	private final DiscordForumService discordForumService;
	private final DiscordPostService discordPostService;
	private final DiscordPostRepository discordPostRepository;
	private final SeasonManagementService seasonManagementService;
	private final SeasonPhaseService seasonPhaseService;
	private final StandingsService standingsService;

	public Map<String, Object> buildDiscordIntegrationModel(UUID seasonId) {
		Season season = seasonManagementService.findById(seasonId);
		DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
		String raceResultsForumId = config.getRaceResultsForumChannelId();
		String standingsForumId = config.getStandingsForumChannelId();
		boolean integrationActive = isNonBlank(raceResultsForumId) || isNonBlank(standingsForumId);

		Map<String, Object> model = new LinkedHashMap<>();

		ThreadListResult raceResults = loadThreadOptions(raceResultsForumId, "race-results");
		ThreadListResult standings = loadThreadOptions(standingsForumId, "standings");
		if (raceResults.warning() != null) {
			model.put("discordIntegrationWarning", raceResults.warning());
		} else if (standings.warning() != null) {
			model.put("discordIntegrationWarning", standings.warning());
		}

		model.put("discordIntegrationActive", integrationActive);
		model.put("raceResultsThreadOptions", raceResults.threads());
		model.put("standingsThreadOptions", standings.threads());
		model.put("linkedRaceResultsThread",
				resolveLinkedThread(season.getDiscordRaceResultsThreadId(), raceResults.threads()));
		model.put("linkedStandingsThread",
				resolveLinkedThread(season.getDiscordStandingsThreadId(), standings.threads()));

		List<SeasonPhase> allPhases = seasonPhaseService.findAllPhases(season.getId());
		boolean canPostStandings = discordPostService.canPostStandings(season, config).canPost();
		String channelId = canPostStandings
				? discordPostService.resolveAnnouncementChannelId(config.getStandingsForumWebhookUrl())
				: null;
		List<PhaseStandingsRow> phaseStandingsRows = new ArrayList<>(allPhases.size());
		for (SeasonPhase p : allPhases) {
			DiscordPost post = channelId != null
					? lookupPhaseScopedStandings(channelId, season.getId(), p.getId())
					: null;
			boolean stale = post != null && standingsService.hasNewerResultsSincePhaseScoped(
					p.getId(), post.getUpdatedAt());
			phaseStandingsRows.add(new PhaseStandingsRow(p, post, stale));
		}
		model.put("allPhases", allPhases);
		model.put("canPostStandings", canPostStandings);
		model.put("phaseStandingsRows", phaseStandingsRows);

		return model;
	}

	public record PhaseStandingsRow(SeasonPhase phase, DiscordPost post, boolean stale) {}

	private DiscordPost lookupPhaseScopedStandings(String channelId, UUID seasonId, UUID phaseId) {
		return discordPostRepository
				.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(
						channelId, DiscordPostType.STANDINGS, seasonId, phaseId)
				.orElse(null);
	}

	private ThreadListResult loadThreadOptions(String forumChannelId, String label) {
		if (!isNonBlank(forumChannelId)) {
			return new ThreadListResult(List.of(), null);
		}
		try {
			return new ThreadListResult(discordForumService.listThreads(forumChannelId), null);
		} catch (DiscordApiException e) {
			log.warn("Could not load {} forum threads (category={}): {}", label, e.category(), e.getMessage());
			return new ThreadListResult(List.of(),
					"Discord forum threads currently unreachable — list may be empty.");
		}
	}

	private static Thread resolveLinkedThread(String threadId, List<Thread> options) {
		if (!isNonBlank(threadId)) {
			return null;
		}
		return options.stream()
				.filter(t -> threadId.equals(t.id()))
				.findFirst()
				.orElse(new Thread(threadId, "(unknown)", null, 0, null, null));
	}

	private static boolean isNonBlank(String value) {
		return value != null && !value.isBlank();
	}

	private record ThreadListResult(List<Thread> threads, String warning) {}
}
