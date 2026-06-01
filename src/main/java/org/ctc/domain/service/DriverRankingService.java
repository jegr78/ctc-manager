package org.ctc.domain.service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRankingService {

	private final RaceResultRepository raceResultRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final SeasonPhaseService seasonPhaseService;
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
		var phase = seasonPhaseService.findById(phaseId);
		UUID seasonId = phase.getSeason().getId();

		List<RaceResult> regularResults = raceResultRepository.findByRaceMatchdayPhaseId(phaseId);
		List<RaceResult> playoffResults = raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId);
		List<RaceResult> all = new ArrayList<>(regularResults.size() + playoffResults.size());
		all.addAll(regularResults);
		all.addAll(playoffResults);

		// Accumulate per driver
		Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
		for (RaceResult result : all) {
			UUID driverId = result.getDriver().getId();
			rankingMap.computeIfAbsent(driverId, id -> {
				Team team = resolveAttributedTeam(result.getDriver(), seasonId, result.getRace().getId());
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
	 * results all contribute. Driver-team attribution uses the unified home-first /
	 * fallback-fielding policy: a rostered driver is attributed to their SeasonDriver team,
	 * a pure guest to the fielding RaceLineup team (sub-team rolled up to parent).
	 */
	@Transactional(readOnly = true)
	public List<DriverRanking> aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId) {
		Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
		for (UUID phaseId : phaseIds) {
			for (DriverRanking phaseRanking : calculateRankingForPhase(phaseId)) {
				UUID driverId = phaseRanking.getDriver().getId();
				rankingMap.computeIfAbsent(driverId, id -> {
					Team team = resolveAttributedTeam(phaseRanking.getDriver(), seasonId, null);
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

		// Pure guests have no SeasonDriver row — backfill their team from any RaceLineup (parent rollup)
		for (RaceResult result : results) {
			UUID driverId = result.getDriver().getId();
			if (!driverTeamMap.containsKey(driverId)) {
				raceLineupRepository.findByDriverId(driverId).stream().findFirst()
						.ifPresent(rl -> driverTeamMap.put(driverId, rl.getTeam().getParentOrSelf()));
			}
		}

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
	 * Unified team attribution for all ranking paths: home-first, then fallback fielding.
	 *
	 * <ul>
	 *   <li>A rostered driver is attributed to their {@code SeasonDriver} team (home-first).</li>
	 *   <li>A pure guest (no {@code SeasonDriver}) is attributed to the fielding {@code RaceLineup}
	 *       team — the specific race when {@code raceId} is given, otherwise any season lineup.</li>
	 * </ul>
	 *
	 * <p>Sub-teams always roll up to their parent via {@link Team#getParentOrSelf()}.
	 *
	 * @param raceId the race to resolve the fielding lineup for, or {@code null} for the
	 *               season-scoped lookup used by {@link #aggregateAcrossPhases}
	 * @return the attributed parent team, or {@code null} if no roster or lineup exists
	 */
	private Team resolveAttributedTeam(Driver driver, UUID seasonId, UUID raceId) {
		Optional<SeasonDriver> rostered = seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driver.getId());
		if (rostered.isPresent()) {
			return rostered.get().getTeam().getParentOrSelf();
		}
		if (raceId != null) {
			Optional<RaceLineup> raceLineup = raceLineupRepository.findByRaceIdAndDriverId(raceId, driver.getId());
			if (raceLineup.isPresent()) {
				return raceLineup.get().getTeam().getParentOrSelf();
			}
		}
		return raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), seasonId).stream()
				.findFirst()
				.map(rl -> rl.getTeam().getParentOrSelf())
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
