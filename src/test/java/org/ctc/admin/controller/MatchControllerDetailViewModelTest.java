package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordCategoryResolver;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.MatchService;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MatchControllerDetailViewModelTest {

	private static final List<String> EXPECTED_KEYS = List.of(
			"match", "archiveCategories", "defaultSelectionId", "pageTitle",
			"teamCardsPost", "settingsPost", "lineupsPost", "provisionalPost",
			"matchHasCompleteSettings", "matchHasCompleteLineups", "matchHasProvisionalData",
			"matchResultsPost", "matchResultsStale", "schedulePost",
			"matchCanRenderResults", "scheduleVisible",
			"discordAnnouncementsConfigured", "matchPreviewPost", "matchPreviewPreFlight");

	@Test
	void givenValidMatchId_whenBuildMatchDetailModel_thenAllControllerKeysPresent() throws Exception {
		// given
		MatchRepository matchRepository = mock(MatchRepository.class);
		DiscordCategoryResolver categoryResolver = mock(DiscordCategoryResolver.class);
		DiscordPostService discordPostService = mock(DiscordPostService.class);
		DiscordPostRepository discordPostRepository = mock(DiscordPostRepository.class);
		DiscordGlobalConfigService discordGlobalConfigService = mock(DiscordGlobalConfigService.class);

		MatchService service = new MatchService(
				matchRepository,
				mock(MatchdayRepository.class),
				mock(TeamRepository.class),
				mock(RaceRepository.class),
				mock(ScoringService.class),
				categoryResolver,
				discordPostService,
				discordPostRepository,
				discordGlobalConfigService,
				mock(ApplicationEventPublisher.class),
				Clock.systemUTC());

		UUID matchId = UUID.randomUUID();
		Match match = new Match();
		match.setId(matchId);
		Team home = new Team();
		home.setShortName("HOM");
		Team away = new Team();
		away.setShortName("AWY");
		match.setHomeTeam(home);
		match.setAwayTeam(away);
		match.setRaces(new ArrayList<>());
		Season season = new Season("DetailViewModel Test Season");
		Matchday md = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		match.setMatchday(md);

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
		when(categoryResolver.resolveArchiveCategoriesFor(any(Integer.class))).thenReturn(List.of());
		when(categoryResolver.defaultSelection(any())).thenReturn(Optional.empty());

		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/1/tok");
		when(discordGlobalConfigService.getOrInitialize()).thenReturn(config);
		when(discordPostService.resolveAnnouncementChannelId(any())).thenReturn("ch-1");
		when(discordPostService.matchHasCompleteSettings(match)).thenReturn(false);
		when(discordPostService.matchHasCompleteLineups(match)).thenReturn(false);
		when(discordPostService.matchHasProvisionalData(match)).thenReturn(false);
		when(discordPostService.matchCanRenderResults(match)).thenReturn(false);
		when(discordPostService.canPostMatchPreview(match)).thenReturn(new MatchPreviewPreFlightResult(false, "x"));
		when(discordPostRepository.findByChannelIdAndPostTypeAndMatchId(any(), any(), eq(matchId)))
				.thenReturn(Optional.empty());

		// when
		Map<String, Object> model = service.buildMatchDetailModel(matchId);

		// then
		for (String key : EXPECTED_KEYS) {
			assertThat(model).as("expected model attribute '%s'", key).containsKey(key);
		}
		assertThat(model.get("pageTitle")).isEqualTo("Match: HOM vs AWY");
	}

	@Test
	void givenMatchWithoutDiscordChannel_whenBuildMatchDetailModel_thenPreflightCallsSkippedAndFlagsFalse() throws Exception {
		MatchRepository matchRepository = mock(MatchRepository.class);
		DiscordCategoryResolver categoryResolver = mock(DiscordCategoryResolver.class);
		DiscordPostService discordPostService = mock(DiscordPostService.class);
		DiscordPostRepository discordPostRepository = mock(DiscordPostRepository.class);
		DiscordGlobalConfigService discordGlobalConfigService = mock(DiscordGlobalConfigService.class);

		MatchService service = new MatchService(
				matchRepository,
				mock(MatchdayRepository.class),
				mock(TeamRepository.class),
				mock(RaceRepository.class),
				mock(ScoringService.class),
				categoryResolver,
				discordPostService,
				discordPostRepository,
				discordGlobalConfigService,
				mock(ApplicationEventPublisher.class),
				Clock.systemUTC());

		UUID matchId = UUID.randomUUID();
		Match match = new Match();
		match.setId(matchId);
		Team home = new Team();
		home.setShortName("HOM");
		Team away = new Team();
		away.setShortName("AWY");
		match.setHomeTeam(home);
		match.setAwayTeam(away);
		match.setRaces(new ArrayList<>());
		Season season = new Season("NoChannel Test Season");
		Matchday md = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		match.setMatchday(md);
		match.setDiscordChannelId(null);

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
		when(categoryResolver.resolveArchiveCategoriesFor(any(Integer.class))).thenReturn(List.of());
		when(categoryResolver.defaultSelection(any())).thenReturn(Optional.empty());

		DiscordGlobalConfig config = new DiscordGlobalConfig();
		when(discordGlobalConfigService.getOrInitialize()).thenReturn(config);
		when(discordPostService.matchCanRenderResults(match)).thenReturn(false);
		when(discordPostService.canPostMatchPreview(match)).thenReturn(new MatchPreviewPreFlightResult(false, "x"));

		Map<String, Object> model = service.buildMatchDetailModel(matchId);

		assertThat(model.get("matchHasCompleteSettings")).isEqualTo(false);
		assertThat(model.get("matchHasCompleteLineups")).isEqualTo(false);
		assertThat(model.get("matchHasProvisionalData")).isEqualTo(false);
		verify(discordPostService, never()).matchHasCompleteSettings(any());
		verify(discordPostService, never()).matchHasCompleteLineups(any());
		verify(discordPostService, never()).matchHasProvisionalData(any());
	}
}
