package org.ctc.discord.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordTimestamps;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordPostServiceRefBranchesTest {

	private static final String CHANNEL_ID = "chan-1";
	private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/12345/abctoken";

	private DiscordWebhookClient webhookClient;
	private DiscordPostRepository repository;
	private DiscordPostService service;

	@BeforeEach
	void setUp() throws Exception {
		webhookClient = mock(DiscordWebhookClient.class);
		repository = mock(DiscordPostRepository.class);
		DiscordHostValidator hostValidator = mock(DiscordHostValidator.class);
		service = new DiscordPostService(
				webhookClient,
				mock(DiscordRestClient.class),
				repository,
				hostValidator,
				mock(DiscordGlobalConfigService.class),
				Clock.systemUTC(),
				mock(TeamCardService.class),
				mock(SeasonTeamRepository.class),
				mock(SettingsGraphicService.class),
				mock(LineupGraphicService.class),
				mock(RaceLineupRepository.class),
				mock(MatchResultsGraphicService.class),
				mock(ResultsGraphicService.class),
				mock(ProvisionalScoresGraphicService.class),
				mock(DiscordTimestamps.class),
				mock(org.ctc.discord.DiscordEmojiCache.class),
				mock(org.ctc.admin.service.MatchdayResultsGraphicService.class),
				mock(org.ctc.admin.service.PowerRankingsGraphicService.class),
				mock(org.ctc.admin.service.StandingsGraphicService.class),
				mock(org.ctc.admin.service.MatchdayPairingsGraphicService.class),
				"uploads");

		WebhookMessage msg = new WebhookMessage("msg-1", CHANNEL_ID);
		when(webhookClient.execute(any(), any(), any())).thenReturn(msg);
		when(repository.save(any(DiscordPost.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void givenMatchRef_whenPostOrEdit_thenDispatchesToFindByMatchId() throws Exception {
		UUID matchId = UUID.randomUUID();
		when(repository.findByChannelIdAndPostTypeAndMatchId(
				eq(CHANNEL_ID), eq(DiscordPostType.MATCH_RESULTS), eq(matchId)))
				.thenReturn(Optional.empty());

		service.postOrEdit(CHANNEL_ID, WEBHOOK_URL, DiscordPostType.MATCH_RESULTS,
				WebhookPayload.empty(), List.of(), new DiscordPostRef.MatchRef(matchId));

		verify(repository).findByChannelIdAndPostTypeAndMatchId(CHANNEL_ID, DiscordPostType.MATCH_RESULTS, matchId);
	}

	@Test
	void givenRaceRef_whenPostOrEdit_thenDispatchesToFindByRaceId() throws Exception {
		UUID raceId = UUID.randomUUID();
		when(repository.findByChannelIdAndPostTypeAndRaceId(
				eq(CHANNEL_ID), eq(DiscordPostType.RACE_RESULTS), eq(raceId)))
				.thenReturn(Optional.empty());

		service.postOrEdit(CHANNEL_ID, WEBHOOK_URL, DiscordPostType.RACE_RESULTS,
				WebhookPayload.empty(), List.of(), new DiscordPostRef.RaceRef(raceId));

		verify(repository).findByChannelIdAndPostTypeAndRaceId(CHANNEL_ID, DiscordPostType.RACE_RESULTS, raceId);
	}

	@Test
	void givenSeasonRef_whenPostOrEdit_thenDispatchesToFindBySeasonId() throws Exception {
		UUID seasonId = UUID.randomUUID();
		when(repository.findByChannelIdAndPostTypeAndSeasonId(
				eq(CHANNEL_ID), eq(DiscordPostType.STANDINGS), eq(seasonId)))
				.thenReturn(Optional.empty());

		service.postOrEdit(CHANNEL_ID, WEBHOOK_URL, DiscordPostType.STANDINGS,
				WebhookPayload.empty(), List.of(), new DiscordPostRef.SeasonRef(seasonId, null));

		verify(repository).findByChannelIdAndPostTypeAndSeasonId(CHANNEL_ID, DiscordPostType.STANDINGS, seasonId);
	}

	@Test
	void givenMatchdayRef_whenPostOrEdit_thenDispatchesToFindByMatchdayId() throws Exception {
		UUID matchdayId = UUID.randomUUID();
		when(repository.findByChannelIdAndPostTypeAndMatchdayId(
				eq(CHANNEL_ID), eq(DiscordPostType.MATCHDAY_OVERVIEW), eq(matchdayId)))
				.thenReturn(Optional.empty());

		service.postOrEdit(CHANNEL_ID, WEBHOOK_URL, DiscordPostType.MATCHDAY_OVERVIEW,
				WebhookPayload.empty(), List.of(), new DiscordPostRef.MatchdayRef(matchdayId));

		verify(repository).findByChannelIdAndPostTypeAndMatchdayId(
				CHANNEL_ID, DiscordPostType.MATCHDAY_OVERVIEW, matchdayId);
	}

	@Test
	void givenNullThreadId_whenPostOrEditViaSixArgOverload_thenDelegatesWithoutCallingRestClient() throws Exception {
		UUID matchId = UUID.randomUUID();
		when(repository.findByChannelIdAndPostTypeAndMatchId(any(), any(), any()))
				.thenReturn(Optional.empty());

		service.postOrEdit(CHANNEL_ID, WEBHOOK_URL, DiscordPostType.MATCH_RESULTS,
				WebhookPayload.empty(), List.of(), new DiscordPostRef.MatchRef(matchId));

		verify(webhookClient).execute(eq(WEBHOOK_URL), any(WebhookPayload.class), eq((String) null));
	}
}
