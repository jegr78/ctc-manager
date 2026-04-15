package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

	private final RaceLineupRepository raceLineupRepository;
	private final RaceRepository raceRepository;

	public void calculatePoints(RaceResult result, RaceScoring scoring) {
		int[] racePoints = scoring.getRacePointsArray();
		int[] qualiPoints = scoring.getQualiPointsArray();

		int rp = (result.getPosition() >= 1 && result.getPosition() <= racePoints.length)
				? racePoints[result.getPosition() - 1] : 0;
		int qp = (result.getQualiPosition() >= 1 && result.getQualiPosition() <= qualiPoints.length)
				? qualiPoints[result.getQualiPosition() - 1] : 0;
		int fp = result.isFastestLap() ? scoring.getFastestLapPoints() : 0;

		result.setPointsRace(rp);
		result.setPointsQuali(qp);
		result.setPointsFl(fp);
		result.setPointsTotal(rp + qp + fp);

		log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
				result.getDriver() != null ? result.getDriver().getPsnId() : "unknown",
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
	 * Aggregates race result scores onto the parent Match or PlayoffMatchup.
	 * Call this after saving race results to keep match scores in sync.
	 * Uses database query to ensure all legs are included, even when lazy-loaded collections are incomplete.
	 */
	@Transactional
	public void aggregateMatchScores(Race race) {
		if (race.getResults().isEmpty()) return;
		if (race.isBye()) return;

		if (race.getMatch() != null && race.getMatch().getHomeTeam() != null) {
			Match match = race.getMatch();
			UUID hId = match.getHomeTeam().getId();

			// Load all races for this match from database to ensure completeness
			// (important for CSV import where races are added sequentially)
			var legs = raceRepository.findByMatchId(match.getId());

			int matchHome = 0, matchAway = 0;
			for (Race leg : legs) {
				if (leg.getResults().isEmpty()) continue;
				matchHome += leg.getResults().stream()
						.filter(r -> isDriverInTeam(r, leg.getId(), hId))
						.mapToInt(RaceResult::getPointsTotal).sum();
				matchAway += leg.getResults().stream()
						.filter(r -> !isDriverInTeam(r, leg.getId(), hId))
						.mapToInt(RaceResult::getPointsTotal).sum();
			}
			match.setHomeScore(matchHome);
			match.setAwayScore(matchAway);
			log.info("Aggregated match scores: {} {} : {} {}",
					match.getHomeTeam().getShortName(), matchHome, matchAway,
					match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?");
		}

		if (race.getPlayoffMatchup() != null && race.getPlayoffMatchup().getTeam1() != null) {
			PlayoffMatchup matchup = race.getPlayoffMatchup();
			UUID t1Id = matchup.getTeam1().getId();

			// Load all races for this matchup from database for consistency
			var legs = raceRepository.findByPlayoffMatchupId(matchup.getId());

			int mHome = 0, mAway = 0;
			for (Race leg : legs) {
				if (leg.getResults().isEmpty()) continue;
				mHome += leg.getResults().stream()
						.filter(r -> isDriverInTeam(r, leg.getId(), t1Id))
						.mapToInt(RaceResult::getPointsTotal).sum();
				mAway += leg.getResults().stream()
						.filter(r -> !isDriverInTeam(r, leg.getId(), t1Id))
						.mapToInt(RaceResult::getPointsTotal).sum();
			}
			matchup.setHomeScore(mHome);
			matchup.setAwayScore(mAway);
		}
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
		// Fallback for legacy data without RaceLineup — filter by current season (per D-11)
		var race = raceRepository.findById(raceId).orElse(null);
		if (race == null || race.getMatchday() == null) return false;
		var seasonId = race.getMatchday().getSeason().getId();
		return result.getDriver().getSeasonDrivers().stream()
				.filter(sd -> sd.getSeason().getId().equals(seasonId))
				.anyMatch(sd -> sd.getTeam().getId().equals(teamId));
	}
}
