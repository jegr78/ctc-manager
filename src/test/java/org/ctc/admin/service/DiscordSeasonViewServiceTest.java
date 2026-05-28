package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordForumService;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.model.Season;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Test;

class DiscordSeasonViewServiceTest {

	private static final List<String> EXPECTED_KEYS = List.of(
			"discordIntegrationActive",
			"raceResultsThreadOptions", "standingsThreadOptions",
			"linkedRaceResultsThread", "linkedStandingsThread",
			"allPhases", "canPostStandings",
			"standingsPostByPhase", "standingsStaleByPhase");

	@Test
	void givenIntegrationConfigured_whenBuildDiscordIntegrationModel_thenAllControllerKeysPresent() throws Exception {
		// given
		DiscordGlobalConfigService configService = mock(DiscordGlobalConfigService.class);
		DiscordForumService forumService = mock(DiscordForumService.class);
		DiscordPostService postService = mock(DiscordPostService.class);
		DiscordPostRepository postRepository = mock(DiscordPostRepository.class);
		SeasonManagementService seasonManagementService = mock(SeasonManagementService.class);
		SeasonPhaseService seasonPhaseService = mock(SeasonPhaseService.class);
		StandingsService standingsService = mock(StandingsService.class);

		DiscordSeasonViewService service = new DiscordSeasonViewService(
				configService, forumService, postService, postRepository,
				seasonManagementService, seasonPhaseService, standingsService);

		UUID seasonId = UUID.randomUUID();
		Season season = new Season("Test");
		season.setId(seasonId);
		when(seasonManagementService.findById(seasonId)).thenReturn(season);

		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setRaceResultsForumChannelId("forum-rr");
		config.setStandingsForumChannelId("forum-st");
		when(configService.getOrInitialize()).thenReturn(config);
		when(forumService.listThreads(anyString())).thenReturn(List.of());
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of());
		when(postService.canPostStandings(any(), any())).thenReturn(new MatchPreviewPreFlightResult(false, "x"));

		// when
		Map<String, Object> model = service.buildDiscordIntegrationModel(seasonId);

		// then
		for (String key : EXPECTED_KEYS) {
			assertThat(model).as("expected model attribute '%s'", key).containsKey(key);
		}
		assertThat(model.get("discordIntegrationActive")).isEqualTo(true);
		assertThat(model.get("canPostStandings")).isEqualTo(false);
	}

	@Test
	void givenIntegrationInactive_whenBuildDiscordIntegrationModel_thenSensibleDefaults() throws Exception {
		// given
		DiscordGlobalConfigService configService = mock(DiscordGlobalConfigService.class);
		DiscordForumService forumService = mock(DiscordForumService.class);
		DiscordPostService postService = mock(DiscordPostService.class);
		DiscordPostRepository postRepository = mock(DiscordPostRepository.class);
		SeasonManagementService seasonManagementService = mock(SeasonManagementService.class);
		SeasonPhaseService seasonPhaseService = mock(SeasonPhaseService.class);
		StandingsService standingsService = mock(StandingsService.class);

		DiscordSeasonViewService service = new DiscordSeasonViewService(
				configService, forumService, postService, postRepository,
				seasonManagementService, seasonPhaseService, standingsService);

		UUID seasonId = UUID.randomUUID();
		Season season = new Season("Test");
		season.setId(seasonId);
		when(seasonManagementService.findById(seasonId)).thenReturn(season);
		when(configService.getOrInitialize()).thenReturn(new DiscordGlobalConfig());
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of());
		when(postService.canPostStandings(any(), any())).thenReturn(new MatchPreviewPreFlightResult(false, "x"));

		// when
		Map<String, Object> model = service.buildDiscordIntegrationModel(seasonId);

		// then
		assertThat(model.get("discordIntegrationActive")).isEqualTo(false);
		assertThat(model.get("raceResultsThreadOptions")).isEqualTo(List.<Thread>of());
		assertThat(model.get("standingsThreadOptions")).isEqualTo(List.<Thread>of());
		assertThat(model.get("linkedRaceResultsThread")).isNull();
		assertThat(model.get("linkedStandingsThread")).isNull();
	}

	@Test
	void givenForumApiFailure_whenBuildDiscordIntegrationModel_thenWarningAttributeSet() throws Exception {
		// given
		DiscordGlobalConfigService configService = mock(DiscordGlobalConfigService.class);
		DiscordForumService forumService = mock(DiscordForumService.class);
		DiscordPostService postService = mock(DiscordPostService.class);
		DiscordPostRepository postRepository = mock(DiscordPostRepository.class);
		SeasonManagementService seasonManagementService = mock(SeasonManagementService.class);
		SeasonPhaseService seasonPhaseService = mock(SeasonPhaseService.class);
		StandingsService standingsService = mock(StandingsService.class);

		DiscordSeasonViewService service = new DiscordSeasonViewService(
				configService, forumService, postService, postRepository,
				seasonManagementService, seasonPhaseService, standingsService);

		UUID seasonId = UUID.randomUUID();
		Season season = new Season("Test");
		season.setId(seasonId);
		when(seasonManagementService.findById(seasonId)).thenReturn(season);
		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setRaceResultsForumChannelId("forum-rr");
		when(configService.getOrInitialize()).thenReturn(config);
		when(forumService.listThreads(anyString())).thenThrow(new DiscordNotFoundException("404", null));
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of());
		when(postService.canPostStandings(any(), any())).thenReturn(new MatchPreviewPreFlightResult(false, "x"));

		// when
		Map<String, Object> model = service.buildDiscordIntegrationModel(seasonId);

		// then
		assertThat(model).containsKey("discordIntegrationWarning");
	}
}
