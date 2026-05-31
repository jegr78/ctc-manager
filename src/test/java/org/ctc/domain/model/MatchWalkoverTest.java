package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchWalkoverTest {

	private Team team(String shortName) {
		Team t = new Team();
		t.setId(UUID.randomUUID());
		t.setShortName(shortName);
		return t;
	}

	@Test
	void givenNoWalkover_whenIsWalkoverFor_thenFalseForBothTeams() {
		Team home = team("H");
		Team away = team("A");
		Match match = new Match(null, home, away);

		assertThat(match.isWalkoverFor(home)).isFalse();
		assertThat(match.isWalkoverFor(away)).isFalse();
	}

	@Test
	void givenHomeWalkover_whenIsWalkoverFor_thenTrueForHomeOnly() {
		Team home = team("H");
		Team away = team("A");
		Match match = new Match(null, home, away);
		match.setWalkoverTeam(home);

		assertThat(match.isWalkoverFor(home)).isTrue();
		assertThat(match.isWalkoverFor(away)).isFalse();
	}

	@Test
	void givenAwayWalkover_whenIsWalkoverFor_thenTrueForAwayOnly() {
		Team home = team("H");
		Team away = team("A");
		Match match = new Match(null, home, away);
		match.setWalkoverTeam(away);

		assertThat(match.isWalkoverFor(away)).isTrue();
		assertThat(match.isWalkoverFor(home)).isFalse();
	}

	@Test
	void givenWalkover_whenIsWalkoverForNullTeam_thenFalse() {
		Team home = team("H");
		Match match = new Match(null, home, team("A"));
		match.setWalkoverTeam(home);

		assertThat(match.isWalkoverFor(null)).isFalse();
	}
}
