package org.ctc.domain.service;

import static org.ctc.util.LogSanitizer.sanitize;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

	private final MatchRepository matchRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final RaceRepository raceRepository;

	public void calculatePoints(RaceResult result, RaceScoring scoring) {
		int[] racePoints = scoring.getRacePointsArray();
		int[] qualiPoints = scoring.getQualiPointsArray();

		int rp = result.getPosition() >= 1 && result.getPosition() <= racePoints.length
				? racePoints[result.getPosition() - 1] : 0;
		int qp = result.getQualiPosition() >= 1 && result.getQualiPosition() <= qualiPoints.length
				? qualiPoints[result.getQualiPosition() - 1] : 0;
		int fp = result.isFastestLap() ? scoring.getFastestLapPoints() : 0;

		result.setPointsRace(rp);
		result.setPointsQuali(qp);
		result.setPointsFl(fp);
		result.setPointsTotal(rp + qp + fp);

		log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
				sanitize(result.getDriver() != null ? result.getDriver().getPsnId() : "unknown"),
				rp, qp, fp, result.getPointsTotal());
	}

	public void calculatePoints(List<RaceResult> results, RaceScoring scoring) {
		results.forEach(r -> calculatePoints(r, scoring));
	}

	public int calculateTeamTotal(List<RaceResult> teamResults) {
		return teamResults.stream()
				.mapToInt(RaceResult::getPointsTotal)
				.sum();
	}

	/**
	 * Re-derives the scores of {@code race}'s match or playoff matchup from its persisted legs,
	 * even when {@code race} itself has no results. Call it after removing result rows.
	 */
	@Transactional
	public void recomputeMatchScoresFromAllLegs(Race race) {
		if (race.getMatch() != null) {
			recomputeMatchScores(race.getMatch());
		}
		if (race.getPlayoffMatchup() != null) {
			recomputePlayoffMatchupScores(race.getPlayoffMatchup());
		}
	}

	/**
	 * Re-sums the match from the results of its current legs; without any scored leg the match is
	 * unplayed again ({@code null} scores) rather than a 0:0 draw.
	 */
	@Transactional
	public void recomputeMatchScores(Match match) {
		if (match.isBye() || match.getWalkoverTeam() != null) {
			return;
		}
		if (match.getHomeTeam() == null) {
			log.warn("Skipping match-score recompute for match {} — homeTeam is null", match.getId());
			return;
		}
		int[] totals = sumLegs(raceRepository.findByMatchId(match.getId()), match.getHomeTeam().getId());
		match.setHomeScore(totals == null ? null : totals[0]);
		match.setAwayScore(totals == null ? null : totals[1]);
		matchRepository.save(match);
		log.info("Recomputed match scores for match {}: {} : {}", match.getId(), match.getHomeScore(), match.getAwayScore());
	}

	/** Playoff counterpart of {@link #recomputeMatchScores(Match)}; rejects any change to a decided matchup's totals. */
	@Transactional
	public void recomputePlayoffMatchupScores(PlayoffMatchup matchup) {
		if (matchup.getTeam1() == null) {
			log.warn("Skipping playoff-matchup score recompute for matchup {} — team1 is null", matchup.getId());
			return;
		}
		int[] totals = sumLegs(raceRepository.findByPlayoffMatchupId(matchup.getId()), matchup.getTeam1().getId());
		Integer home = totals == null ? null : totals[0];
		Integer away = totals == null ? null : totals[1];
		if (!Objects.equals(home, matchup.getHomeScore()) || !Objects.equals(away, matchup.getAwayScore())) {
			PlayoffDecisionGuard.requireOpen(matchup);
		}
		matchup.setHomeScore(home);
		matchup.setAwayScore(away);
		playoffMatchupRepository.save(matchup);
	}

	/** Returns [home, away] over all scored legs, or {@code null} when no leg has results. */
	private int[] sumLegs(List<Race> legs, UUID homeTeamId) {
		int home = 0;
		int away = 0;
		boolean scored = false;
		for (Race leg : legs) {
			if (leg.getResults().isEmpty()) {
				continue;
			}
			scored = true;
			home += leg.getResults().stream()
					.filter(r -> isDriverInTeam(r, leg.getId(), homeTeamId))
					.mapToInt(RaceResult::getPointsTotal).sum();
			away += leg.getResults().stream()
					.filter(r -> !isDriverInTeam(r, leg.getId(), homeTeamId))
					.mapToInt(RaceResult::getPointsTotal).sum();
		}
		return scored ? new int[]{home, away} : null;
	}

	/**
	 * Aggregates race result scores onto the parent Match or PlayoffMatchup.
	 * Call this after saving race results to keep match scores in sync.
	 * Uses database query to ensure all legs are included, even when lazy-loaded collections are incomplete.
	 */
	@Transactional
	public void aggregateMatchScores(Race race) {
		if (race.getResults().isEmpty()) {
			return;
		}
		recomputeMatchScoresFromAllLegs(race);
	}

	/**
	 * Calculates [team1Points, team2Points] from race results.
	 * Used by PlayoffService.determineWinner and PlayoffBracketViewService.buildMatchupView.
	 */
	public int[] calculateTeamTotals(List<RaceResult> results, UUID raceId, UUID team1Id) {
		int team1Total = 0;
		int team2Total = 0;
		for (RaceResult result : results) {
			if (isDriverInTeam(result, raceId, team1Id)) {
				team1Total += result.getPointsTotal();
			} else {
				team2Total += result.getPointsTotal();
			}
		}
		return new int[]{team1Total, team2Total};
	}

	/**
	 * Checks if a driver belongs to the given team for a specific race.
	 * Uses RaceLineup (Source of Truth) with fallback to SeasonDriver for legacy data.
	 */
	public boolean isDriverInTeam(RaceResult result, UUID raceId, UUID teamId) {
		var lineup = raceLineupRepository.findByRaceIdAndDriverId(raceId, result.getDriver().getId());
		if (lineup.isPresent()) {
			UUID lineupTeamId = lineup.get().getTeam().getId();
			return lineupTeamId.equals(teamId)
					|| (lineup.get().getTeam().getParentTeam() != null
					&& lineup.get().getTeam().getParentTeam().getId().equals(teamId));
		}
		// Fallback for legacy data without RaceLineup — filter by current season.
		var race = raceRepository.findById(raceId).orElse(null);
		if (race == null || race.getMatchday() == null) {
			return false;
		}
		var seasonId = race.getMatchday().getSeason().getId();
		return result.getDriver().getSeasonDrivers().stream()
				.filter(sd -> sd.getSeason().getId().equals(seasonId))
				.anyMatch(sd -> sd.getTeam().getId().equals(teamId));
	}
}
