package org.ctc.admin.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.MatchdayStalenessSnapshot;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscordMatchdayViewService {

	private final DiscordGlobalConfigService discordGlobalConfigService;
	private final DiscordPostRepository discordPostRepository;
	private final DiscordPostService discordPostService;
	private final StandingsService standingsService;

	public Map<String, Object> buildMatchdayDiscordModel(Matchday matchday) {
		DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		boolean threadLinked = isNonBlank(matchday.getSeason().getDiscordRaceResultsThreadId());
		boolean matchdayDiscordActive = threadLinked && isNonBlank(webhookUrl);

		String announcementWebhookUrl = config.getAnnouncementWebhookUrl();
		boolean matchdayAnnouncementActive = isNonBlank(announcementWebhookUrl);

		DiscordPost overviewPost = lookupPost(matchdayDiscordActive, webhookUrl,
				DiscordPostType.MATCHDAY_OVERVIEW, matchday);
		DiscordPost powerRankingsPost = lookupPost(matchdayDiscordActive, webhookUrl,
				DiscordPostType.POWER_RANKINGS, matchday);
		DiscordPost pairingsPost = lookupPost(matchdayAnnouncementActive, announcementWebhookUrl,
				DiscordPostType.MATCHDAY_PAIRINGS, matchday);
		DiscordPost schedulePost = lookupPost(matchdayAnnouncementActive, announcementWebhookUrl,
				DiscordPostType.MATCHDAY_SCHEDULE, matchday);

		MatchdayStalenessSnapshot staleness = standingsService.snapshotMatchdayStaleness(
				matchday, overviewPost, powerRankingsPost, pairingsPost, schedulePost);

		MatchPreviewPreFlightResult resultsPreFlight = discordPostService.canPostMatchdayResults(matchday, config);
		MatchPreviewPreFlightResult rankingsPreFlight = discordPostService.canPostPowerRankings(matchday, config);
		MatchPreviewPreFlightResult pairingsPreFlight = discordPostService.canPostMatchdayPairings(matchday, config);
		MatchPreviewPreFlightResult schedulePreFlight = discordPostService.canPostMatchdaySchedule(matchday, config);

		Map<String, Object> model = new LinkedHashMap<>();
		model.put("matchdayDiscordActive", matchdayDiscordActive);
		model.put("matchdayAnnouncementActive", matchdayAnnouncementActive);
		model.put("canPostMatchdayResults", resultsPreFlight.canPost());
		model.put("matchdayResultsDisabledReason", resultsPreFlight.disabledReason());
		model.put("canPostPowerRankings", rankingsPreFlight.canPost());
		model.put("powerRankingsDisabledReason", rankingsPreFlight.disabledReason());
		model.put("canPostMatchdayPairings", pairingsPreFlight.canPost());
		model.put("matchdayPairingsDisabledReason", pairingsPreFlight.disabledReason());
		model.put("canPostMatchdaySchedule", schedulePreFlight.canPost());
		model.put("matchdayScheduleDisabledReason", schedulePreFlight.disabledReason());
		model.put("matchdayOverviewPost", overviewPost);
		model.put("powerRankingsPost", powerRankingsPost);
		model.put("matchdayPairingsPost", pairingsPost);
		model.put("matchdaySchedulePost", schedulePost);
		model.put("matchdayResultsStale", staleness.matchdayResultsStale());
		model.put("powerRankingsStale", staleness.powerRankingsStale());
		model.put("matchdayPairingsStale", staleness.matchdayPairingsStale());
		model.put("matchdayScheduleStale", staleness.matchdayScheduleStale());
		return model;
	}

	private DiscordPost lookupPost(boolean active, String webhookUrl, DiscordPostType type, Matchday matchday) {
		if (!active) {
			return null;
		}
		String channelId = discordPostService.resolveAnnouncementChannelId(webhookUrl);
		return discordPostRepository
				.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, matchday.getId())
				.orElse(null);
	}

	private static boolean isNonBlank(String s) {
		return s != null && !s.isBlank();
	}
}
