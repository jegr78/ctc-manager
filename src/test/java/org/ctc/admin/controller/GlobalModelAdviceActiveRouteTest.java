package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GlobalModelAdviceActiveRouteTest {

	private final GlobalModelAdvice advice = new GlobalModelAdvice();

	@ParameterizedTest
	@CsvSource({
			"/admin/seasons,seasons",
			"/admin/seasons/abc-def,seasons",
			"/admin/seasons/abc/phases,seasons",
			"/admin/matchdays,matchdays",
			"/admin/matchdays/123/pairings,matchdays",
			"/admin/matches,matchdays",
			"/admin/matches/abc-def,matchdays",
			"/admin/races,races",
			"/admin/playoffs,playoffs",
			"/admin/playoff-matchups/abc,playoffs",
			"/admin/teams,teams",
			"/admin/teams/abc-def,teams",
			"/admin/drivers,drivers",
			"/admin/drivers/import,drivers",
			"/admin/cars,cars",
			"/admin/tracks,tracks",
			"/admin/race-scorings,race-scorings",
			"/admin/match-scorings,match-scorings",
			"/admin/standings,standings",
			"/admin/tools/power-rankings,power-rankings",
			"/admin/tools/team-cards,team-cards",
			"/admin/tools/template-editors,template-editors",
			"/admin/import,import",
			"/admin/gt7-sync,gt7-sync",
			"/admin/backup,backup",
			"/admin/discord-config,discord-config",
			"/admin/discord-config/save,discord-config",
			"/admin/discord/posts,discord-posts",
			"/admin/discord/posts/filter,discord-posts"
	})
	void givenAdminUri_whenActiveRoute_thenReturnsExpectedKey(String uri, String expected) {
		assertThat(advice.activeRoute(requestWith(uri))).isEqualTo(expected);
	}

	@Test
	void givenGenerateUri_whenActiveRoute_thenReturnsNullForSidebarButton() {
		assertThat(advice.activeRoute(requestWith("/admin/generate"))).isNull();
	}

	@Test
	void givenNonAdminUri_whenActiveRoute_thenReturnsNull() {
		assertThat(advice.activeRoute(requestWith("/public"))).isNull();
		assertThat(advice.activeRoute(requestWith("/"))).isNull();
	}

	@Test
	void givenTeamCardsBeforeTeams_whenActiveRoute_thenReturnsTeamCardsNotTeams() {
		assertThat(advice.activeRoute(requestWith("/admin/tools/team-cards"))).isEqualTo("team-cards");
	}

	@Test
	void givenDiscordConfigBeforeDiscordPosts_whenActiveRoute_thenReturnsDiscordConfig() {
		assertThat(advice.activeRoute(requestWith("/admin/discord-config"))).isEqualTo("discord-config");
	}

	@Test
	void givenNullUri_whenActiveRoute_thenReturnsNull() {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRequestURI()).thenReturn(null);
		assertThat(advice.activeRoute(req)).isNull();
	}

	private static HttpServletRequest requestWith(String uri) {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRequestURI()).thenReturn(uri);
		return req;
	}
}
