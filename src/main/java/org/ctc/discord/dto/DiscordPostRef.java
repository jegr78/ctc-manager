package org.ctc.discord.dto;

import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.jspecify.annotations.Nullable;

public sealed interface DiscordPostRef
		permits DiscordPostRef.MatchRef,
				DiscordPostRef.MatchdayRef,
				DiscordPostRef.RaceRef,
				DiscordPostRef.SeasonRef {

	void applyTo(DiscordPost row);

	UUID matchId();

	UUID matchdayId();

	UUID raceId();

	UUID seasonId();

	static DiscordPostRef match(Match m) {
		return new MatchRef(m.getId());
	}

	static DiscordPostRef matchday(Matchday m) {
		return new MatchdayRef(m.getId());
	}

	static DiscordPostRef race(Race r) {
		return new RaceRef(r.getId());
	}

	static DiscordPostRef season(Season s) {
		return new SeasonRef(s.getId(), null);
	}

	static DiscordPostRef seasonPhase(Season s, SeasonPhase p) {
		return new SeasonRef(s.getId(), p.getId());
	}

	record MatchRef(UUID id) implements DiscordPostRef {
		@Override
		public void applyTo(DiscordPost row) {
			row.setMatchId(id);
		}

		@Override
		public UUID matchId() {
			return id;
		}

		@Override
		public UUID matchdayId() {
			return null;
		}

		@Override
		public UUID raceId() {
			return null;
		}

		@Override
		public UUID seasonId() {
			return null;
		}
	}

	record MatchdayRef(UUID id) implements DiscordPostRef {
		@Override
		public void applyTo(DiscordPost row) {
			row.setMatchdayId(id);
		}

		@Override
		public UUID matchId() {
			return null;
		}

		@Override
		public UUID matchdayId() {
			return id;
		}

		@Override
		public UUID raceId() {
			return null;
		}

		@Override
		public UUID seasonId() {
			return null;
		}
	}

	record RaceRef(UUID id) implements DiscordPostRef {
		@Override
		public void applyTo(DiscordPost row) {
			row.setRaceId(id);
		}

		@Override
		public UUID matchId() {
			return null;
		}

		@Override
		public UUID matchdayId() {
			return null;
		}

		@Override
		public UUID raceId() {
			return id;
		}

		@Override
		public UUID seasonId() {
			return null;
		}
	}

	record SeasonRef(UUID seasonId, @Nullable UUID phaseId) implements DiscordPostRef {
		@Override
		public void applyTo(DiscordPost row) {
			row.setSeasonId(seasonId);
			if (phaseId != null) {
				row.setPhaseId(phaseId);
			}
		}

		@Override
		public UUID matchId() {
			return null;
		}

		@Override
		public UUID matchdayId() {
			return null;
		}

		@Override
		public UUID raceId() {
			return null;
		}
	}
}
