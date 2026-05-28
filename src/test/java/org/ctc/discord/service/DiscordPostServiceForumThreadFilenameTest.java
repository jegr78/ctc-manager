package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.domain.model.Match;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;

class DiscordPostServiceForumThreadFilenameTest {

	@Test
	void givenNullMatch_whenTeamSlug_thenReturnsRaceFallback() {
		assertThat(DiscordPostService.teamSlugOrFallback(null)).isEqualTo("race");
	}

	@Test
	void givenMatchWithNullHomeTeam_whenTeamSlug_thenReturnsRaceFallback() {
		Match match = new Match();
		match.setAwayTeam(team("AWAY"));
		assertThat(DiscordPostService.teamSlugOrFallback(match)).isEqualTo("race");
	}

	@Test
	void givenMatchWithNullAwayTeam_whenTeamSlug_thenReturnsRaceFallback() {
		Match match = new Match();
		match.setHomeTeam(team("HOME"));
		assertThat(DiscordPostService.teamSlugOrFallback(match)).isEqualTo("race");
	}

	@Test
	void givenMatchWithBothTeams_whenTeamSlug_thenReturnsHomeVsAwayShortNames() {
		Match match = new Match();
		match.setHomeTeam(team("ALF"));
		match.setAwayTeam(team("BRC"));
		assertThat(DiscordPostService.teamSlugOrFallback(match)).isEqualTo("ALF-vs-BRC");
	}

	private static Team team(String shortName) {
		Team team = new Team();
		team.setShortName(shortName);
		return team;
	}
}
