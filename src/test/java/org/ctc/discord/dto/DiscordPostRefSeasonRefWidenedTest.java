package org.ctc.discord.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.junit.jupiter.api.Test;

class DiscordPostRefSeasonRefWidenedTest {

	@Test
	void givenSeasonOnly_whenSeasonFactory_thenSeasonRefWithNullPhaseId() {
		UUID seasonId = UUID.randomUUID();
		Season season = mock(Season.class);
		when(season.getId()).thenReturn(seasonId);

		DiscordPostRef ref = DiscordPostRef.season(season);

		assertThat(ref).isInstanceOf(DiscordPostRef.SeasonRef.class);
		DiscordPostRef.SeasonRef seasonRef = (DiscordPostRef.SeasonRef) ref;
		assertThat(seasonRef.seasonId()).isEqualTo(seasonId);
		assertThat(seasonRef.phaseId()).isNull();
	}

	@Test
	void givenSeasonAndPhase_whenSeasonPhaseFactory_thenSeasonRefCarriesBothIds() {
		UUID seasonId = UUID.randomUUID();
		UUID phaseId = UUID.randomUUID();
		Season season = mock(Season.class);
		SeasonPhase phase = mock(SeasonPhase.class);
		when(season.getId()).thenReturn(seasonId);
		when(phase.getId()).thenReturn(phaseId);

		DiscordPostRef ref = DiscordPostRef.seasonPhase(season, phase);

		assertThat(ref).isInstanceOf(DiscordPostRef.SeasonRef.class);
		DiscordPostRef.SeasonRef seasonRef = (DiscordPostRef.SeasonRef) ref;
		assertThat(seasonRef.seasonId()).isEqualTo(seasonId);
		assertThat(seasonRef.phaseId()).isEqualTo(phaseId);
	}

	@Test
	void givenSeasonRef_whenApplyTo_thenSetsSeasonIdOnly() {
		UUID seasonId = UUID.randomUUID();
		UUID phaseId = UUID.randomUUID();
		DiscordPostRef.SeasonRef ref = new DiscordPostRef.SeasonRef(seasonId, phaseId);
		DiscordPost row = mock(DiscordPost.class);

		ref.applyTo(row);

		verify(row).setSeasonId(seasonId);
		verifyNoMoreInteractions(row);
	}

	@Test
	void givenSeasonRefWithNullPhase_whenApplyTo_thenSetsSeasonIdAndPhaseUntouched() {
		UUID seasonId = UUID.randomUUID();
		DiscordPostRef.SeasonRef ref = new DiscordPostRef.SeasonRef(seasonId, null);
		DiscordPost row = mock(DiscordPost.class);

		ref.applyTo(row);

		verify(row).setSeasonId(seasonId);
		verifyNoMoreInteractions(row);
	}

	@Test
	void givenAllPermits_whenSealedSwitch_thenExhaustiveWithoutDefault() {
		DiscordPostRef[] refs = {
				new DiscordPostRef.MatchRef(UUID.randomUUID()),
				new DiscordPostRef.MatchdayRef(UUID.randomUUID()),
				new DiscordPostRef.RaceRef(UUID.randomUUID()),
				new DiscordPostRef.SeasonRef(UUID.randomUUID(), null),
		};
		for (DiscordPostRef r : refs) {
			String tag = switch (r) {
				case DiscordPostRef.MatchRef ignored -> "match";
				case DiscordPostRef.MatchdayRef ignored -> "matchday";
				case DiscordPostRef.RaceRef ignored -> "race";
				case DiscordPostRef.SeasonRef ignored -> "season";
			};
			assertThat(tag).isIn("match", "matchday", "race", "season");
		}
	}
}
