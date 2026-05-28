package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.Test;

class StandingsServiceStalenessSnapshotTest {

	private StandingsService newService(SeasonTeamRepository seasonTeamRepository) {
		return new StandingsService(
				mock(MatchRepository.class),
				mock(SeasonRepository.class),
				mock(RaceRepository.class),
				mock(SeasonPhaseService.class),
				mock(PhaseTeamRepository.class),
				mock(RaceResultRepository.class),
				seasonTeamRepository);
	}

	private DiscordPost postUpdatedAt(LocalDateTime when) {
		DiscordPost p = new DiscordPost();
		p.setUpdatedAt(when);
		return p;
	}

	private Match matchWith(LocalDateTime matchUpdated, LocalDateTime... raceUpdated) {
		Match m = new Match();
		m.setUpdatedAt(matchUpdated);
		List<Race> races = new ArrayList<>();
		for (LocalDateTime rt : raceUpdated) {
			Race r = new Race();
			r.setUpdatedAt(rt);
			races.add(r);
		}
		m.setRaces(races);
		return m;
	}

	@Test
	void givenMatchUpdatedAfterPost_whenIsMatchdayScheduleStale_thenTrue() {
		// given
		StandingsService service = newService(mock(SeasonTeamRepository.class));
		LocalDateTime postTime = LocalDateTime.of(2026, 5, 1, 10, 0);
		LocalDateTime matchTime = postTime.plusHours(1);
		Matchday md = new Matchday();
		md.setMatches(List.of(matchWith(matchTime)));

		// when / then
		assertThat(service.isMatchdayScheduleStale(md, postUpdatedAt(postTime))).isTrue();
	}

	@Test
	void givenPostUpdatedAfterMatch_whenIsMatchdayScheduleStale_thenFalse() {
		// given
		StandingsService service = newService(mock(SeasonTeamRepository.class));
		LocalDateTime postTime = LocalDateTime.of(2026, 5, 1, 12, 0);
		LocalDateTime matchTime = postTime.minusHours(1);
		Matchday md = new Matchday();
		md.setMatches(List.of(matchWith(matchTime)));

		// when / then
		assertThat(service.isMatchdayScheduleStale(md, postUpdatedAt(postTime))).isFalse();
	}

	@Test
	void givenMatchdayUpdatedAfterPost_whenIsMatchdayPairingsStale_thenTrue() {
		// given
		StandingsService service = newService(mock(SeasonTeamRepository.class));
		LocalDateTime postTime = LocalDateTime.of(2026, 5, 1, 10, 0);
		Matchday md = new Matchday();
		md.setUpdatedAt(postTime.plusHours(1));

		// when / then
		assertThat(service.isMatchdayPairingsStale(md, postUpdatedAt(postTime))).isTrue();
	}

	@Test
	void givenPostUpdatedAtNull_whenIsPowerRankingsStale_thenFalse() {
		// given
		StandingsService service = newService(mock(SeasonTeamRepository.class));
		Season season = new Season("S");
		season.setId(UUID.randomUUID());
		DiscordPost post = new DiscordPost();
		post.setUpdatedAt(null);

		// when / then
		assertThat(service.isPowerRankingsStale(season, post)).isFalse();
	}

	@Test
	void givenSeasonTeamUpdatedAfterPost_whenIsPowerRankingsStale_thenTrue() {
		// given
		SeasonTeamRepository seasonTeamRepository = mock(SeasonTeamRepository.class);
		StandingsService service = newService(seasonTeamRepository);
		Season season = new Season("S");
		season.setId(UUID.randomUUID());
		LocalDateTime postTime = LocalDateTime.of(2026, 5, 1, 10, 0);
		SeasonTeam st = new SeasonTeam();
		st.setUpdatedAt(postTime.plusHours(1));
		when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of(st));

		// when / then
		assertThat(service.isPowerRankingsStale(season, postUpdatedAt(postTime))).isTrue();
	}

	@Test
	void givenAllInputsPresent_whenSnapshotMatchdayStaleness_thenAllFourBooleansComputed() {
		// given
		SeasonTeamRepository seasonTeamRepository = mock(SeasonTeamRepository.class);
		StandingsService service = newService(seasonTeamRepository);
		LocalDateTime postTime = LocalDateTime.of(2026, 5, 1, 10, 0);
		Season season = new Season("S");
		season.setId(UUID.randomUUID());
		Matchday md = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		md.setUpdatedAt(postTime.plusHours(1));
		md.setMatches(List.of(matchWith(postTime.plusHours(1))));
		when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of());

		// when
		StandingsService.MatchdayStalenessSnapshot snap = service.snapshotMatchdayStaleness(
				md, postUpdatedAt(postTime), postUpdatedAt(postTime),
				postUpdatedAt(postTime), postUpdatedAt(postTime));

		// then
		assertThat(snap.matchdayPairingsStale()).isTrue();
		assertThat(snap.matchdayScheduleStale()).isTrue();
		assertThat(snap.powerRankingsStale()).isFalse();
	}
}
