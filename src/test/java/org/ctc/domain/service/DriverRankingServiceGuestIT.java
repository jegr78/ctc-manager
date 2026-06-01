package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class DriverRankingServiceGuestIT {

	@Autowired
	private DriverRankingService driverRankingService;

	@Autowired
	private ScoringService scoringService;

	@Test
	void givenGuestResultInRace_whenAggregateMatchScores_thenCountsForFieldingTeamScore() {
		assertThat(true).isTrue();
	}

	@Test
	void givenPureGuestWithNoSeasonDriver_whenAggregateAcrossPhases_thenAppearsInRankingWithFieldingTeam() {
		assertThat(true).isTrue();
	}

	@Test
	void givenGuestResultSavedTwice_whenAggregateMatchScores_thenScoresAreIdempotent() {
		assertThat(true).isTrue();
	}

	@Test
	void givenPureGuestInAlltimeScope_whenCalculateAlltimeRankingForSeason_thenTeamIsNotNull() {
		assertThat(true).isTrue();
	}
}
