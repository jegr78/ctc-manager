package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRankingService {

	private final RaceResultRepository raceResultRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final SeasonPhaseService seasonPhaseService;
	private final PhaseTeamRepository phaseTeamRepository;
	private final RaceLineupRepository raceLineupRepository;

	/**
	 * Primary per-phase entry point.
	 *
	 * <p>Union-merges race results from both finders to ensure PLAYOFF phases produce
	 * non-empty rankings:
	 * <ul>
	 *   <li>{@code findByRaceMatchdayPhaseId} — REGULAR matchday-linked races</li>
	 *   <li>{@code findByRacePlayoffMatchupRoundPlayoffPhaseId} — PLAYOFF matchup-linked races</li>
	 * </ul>
	 *
	 * <p>Team attribution for the per-phase ranking uses RaceLineup (Source of Truth per CLAUDE.md).
	 */
	@Transactional(readOnly = true)
	public List<DriverRanking> calculateRankingForPhase(UUID phaseId) {
		seasonPhaseService.findById(phaseId); // validate phase exists

		List<RaceResult> regularResults = raceResultRepository.findByRaceMatchdayPhaseId(phaseId);
		List<RaceResult> playoffResults = raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId);
		List<RaceResult> all = new ArrayList<>(regularResults.size() + playoffResults.size());
		all.addAll(regularResults);
		all.addAll(playoffResults);

		// Build phase-specific team lookup from PhaseTeam roster (keyed by teamId)
		Map<UUID, Team> phaseTeamByTeamId = phaseTeamRepository.findByPhaseId(phaseId).stream()
				.collect(Collectors.toMap(pt -> pt.getTeam().getId(), PhaseTeam::getTeam, (a, b) -> a));

		// Accumulate per driver
		Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
		for (RaceResult result : all) {
			UUID driverId = result.getDriver().getId();
			rankingMap.computeIfAbsent(driverId, id -> {
				// Per-phase team: resolve via RaceLineup (Source of Truth)
				Team team = resolveTeamFromLineup(driverId, result.getRace());
				return new DriverRanking(result.getDriver(), team);
			}).addResult(result);
		}

		List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
		sortRankings(rankings);
		log.debug("Calculated driver ranking for phase {}: {} drivers", phaseId, rankings.size());
		return rankings;
	}

	/**
	 * Season-wide aggregation across all specified phases. REGULAR + PLAYOFF + PLACEMENT
	 * results all contribute. Driver-team attribution uses the REGULAR-phase team (via
	 * RaceLineup for that driver's season races); stand-ins without a REGULAR-phase
	 * RaceLineup fall back to any season RaceLineup.
	 */
	@Transactional(readOnly = true)
	public List<DriverRanking> aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId) {
		// PhaseTeam maps phase→team; driver→team comes from RaceLineup
		Optional<SeasonPhase> regularPhaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		Set<UUID> regularPhaseTeamIds = regularPhaseOpt
				.map(rp -> phaseTeamRepository.findByPhaseId(rp.getId()).stream()
						.map(pt -> pt.getTeam().getId())
						.collect(Collectors.toSet()))
				.orElseGet(Set::of);

		Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
		for (UUID phaseId : phaseIds) {
			for (DriverRanking phaseRanking : calculateRankingForPhase(phaseId)) {
				UUID driverId = phaseRanking.getDriver().getId();
				rankingMap.computeIfAbsent(driverId, id -> {
					Team team = attributeTeamFromRegularOrLineup(
							regularPhaseTeamIds, driverId, seasonId, phaseRanking.getDriver());
					return new DriverRanking(phaseRanking.getDriver(), team);
				});
				// Merge phase results into season-wide ranking
				DriverRanking merged = rankingMap.get(driverId);
				phaseRanking.getRaceResults().forEach(merged::addResult);
			}
		}

		List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
		sortRankings(rankings);
		log.debug("Aggregated driver ranking across {} phases of season {}: {} drivers",
				phaseIds.size(), seasonId, rankings.size());
		return rankings;
	}

	/**
	 * Calculates alltime driver ranking across all seasons.
	 *
	 * <p>Uses {@code seasonDriverRepository.findAll()} intentionally — alltime rankings
	 * by definition span all seasons; a scoped alternative would require N+1 queries.
	 * Alltime only covers REGULAR-phase results (via {@code findByRacePlayoffMatchupIsNull}),
	 * so SeasonDriver is sufficient for team attribution.
	 */
	@Transactional(readOnly = true)
	public List<DriverRanking> calculateAlltimeRanking() {
		return calculateAlltimeRanking(
				raceResultRepository.findByRacePlayoffMatchupIsNull(),
				seasonDriverRepository.findAll());
	}

	/**
	 * Calculates alltime driver ranking restricted to the given season IDs.
	 * Used by the site generator to exclude Test seasons from public pages.
	 *
	 * <p><strong>Tracked Behavior Change (v1.9 / D-19):</strong> Aggregation now includes
	 * PLAYOFF-matchup-linked race results, not just REGULAR-phase results. Multi-phase seasons'
	 * drivers reflect all races they competed in across REGULAR + PLAYOFF + PLACEMENT phases.
	 */
	@Transactional(readOnly = true)
	public List<DriverRanking> calculateAlltimeRanking(List<UUID> seasonIds) {
		return calculateAlltimeRanking(
				raceResultRepository.findByRaceMatchdaySeasonIdIn(seasonIds),
				seasonDriverRepository.findBySeasonIdIn(seasonIds));
	}

	// ---------------------------------------------------------------------------
	// Private helpers
	// ---------------------------------------------------------------------------

	private List<DriverRanking> calculateAlltimeRanking(List<RaceResult> results,
	                                                     List<SeasonDriver> allSeasonDrivers) {

		// For each driver, find their most recent team (by season name), resolved to parent
		Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
				.collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
				.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> e.getValue().stream()
								.max(Comparator.comparing(sd -> sd.getSeason().getName()))
								.map(sd -> sd.getTeam().getParentOrSelf())
								.orElse(null)));

		Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();

		for (RaceResult result : results) {
			UUID driverId = result.getDriver().getId();
			DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
					id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));
			ranking.addResult(result);
		}

		List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
		sortRankings(rankings);

		log.debug("Calculated alltime driver ranking: {} drivers", rankings.size());
		return rankings;
	}

	/**
	 * Resolves team attribution from RaceLineup for the season, preferring REGULAR-phase
	 * team members. Falls back to any season lineup if no REGULAR-phase lineup is found.
	 *
	 * @param regularPhaseTeamIds set of team IDs enrolled in the REGULAR phase — used to prefer
	 *                            the REGULAR-phase team when a driver is in multiple teams
	 * @param driverId            driver to look up
	 * @param seasonId            season scope for the lookup
	 * @param driver              entity reference (unused here, for caller readability)
	 * @return the attributed team, or {@code null} if no lineup found
	 */
	private Team attributeTeamFromRegularOrLineup(Set<UUID> regularPhaseTeamIds,
	                                               UUID driverId, UUID seasonId, Driver driver) {
		List<RaceLineup> lineups = raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driverId, seasonId);
		if (lineups.isEmpty()) {
			return null;
		}
		return lineups.stream()
				.filter(rl -> regularPhaseTeamIds.contains(rl.getTeam().getId()))
				.findFirst()
				.map(RaceLineup::getTeam)
				.orElseGet(() -> lineups.get(0).getTeam());
	}

	/**
	 * Resolves per-race team from RaceLineup (Source of Truth per CLAUDE.md
	 * `feedback_racelineup_source_of_truth`). The first race result a driver has in the
	 * phase fixes their per-phase team attribution; later races in the same phase keep
	 * the same team because the result accumulator only triggers
	 * {@code computeIfAbsent} once per driver.
	 *
	 * @return the driver's team for the specific race, or {@code null} if no RaceLineup
	 *         row exists (e.g. test fixture race with results but no lineup — the season-
	 *         wide aggregation in {@link #aggregateAcrossPhases} compensates via
	 *         {@link #attributeTeamFromRegularOrLineup})
	 */
	private Team resolveTeamFromLineup(UUID driverId, Race race) {
		return raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driverId)
				.map(RaceLineup::getTeam)
				.orElse(null);
	}

	private void sortRankings(List<DriverRanking> rankings) {
		rankings.sort(Comparator
				.comparing(DriverRanking::getTotalPoints, Comparator.reverseOrder())
				.thenComparing(DriverRanking::getRacesCount)
				.thenComparing(DriverRanking::getAveragePoints, Comparator.reverseOrder()));
	}

	public static class DriverRanking {
		private final Driver driver;
		private final Team team;
		private int totalPoints;
		private int totalRacePoints;
		private int totalQualiPoints;
		private int totalFlPoints;
		private int racesCount;
		private int bestPosition = Integer.MAX_VALUE;
		private final List<RaceResult> raceResults = new ArrayList<>();

		public DriverRanking(Driver driver, Team team) {
			this.driver = driver;
			this.team = team;
		}

		public void addResult(RaceResult result) {
			totalPoints += result.getPointsTotal();
			totalRacePoints += result.getPointsRace();
			totalQualiPoints += result.getPointsQuali();
			totalFlPoints += result.getPointsFl();
			racesCount++;
			if (result.getPosition() < bestPosition) {
				bestPosition = result.getPosition();
			}
			raceResults.add(result);
		}

		public Driver getDriver() {
			return driver;
		}

		public Team getTeam() {
			return team;
		}

		public int getTotalPoints() {
			return totalPoints;
		}

		public int getTotalRacePoints() {
			return totalRacePoints;
		}

		public int getTotalQualiPoints() {
			return totalQualiPoints;
		}

		public int getTotalFlPoints() {
			return totalFlPoints;
		}

		public int getRacesCount() {
			return racesCount;
		}

		public int getBestPosition() {
			return bestPosition == Integer.MAX_VALUE ? 0 : bestPosition;
		}

		public double getAveragePoints() {
			return racesCount > 0 ? (double) totalPoints / racesCount : 0;
		}

		public List<RaceResult> getRaceResults() {
			return Collections.unmodifiableList(raceResults);
		}
	}
}
