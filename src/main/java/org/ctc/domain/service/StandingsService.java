package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsService {

	private final MatchRepository matchRepository;
	private final TeamRepository teamRepository;
	private final SeasonRepository seasonRepository;
	private final RaceRepository raceRepository;
	private final SeasonPhaseService seasonPhaseService;
	private final PhaseTeamRepository phaseTeamRepository;

	// ---------------------------------------------------------------------------
	// Canonical phase-aware methods (SVC-02, D-01, D-04, D-05, D-06)
	// ---------------------------------------------------------------------------

	/**
	 * Calculates standings for the given phase and optional group.
	 *
	 * <p>For {@code layout=LEAGUE}: {@code groupId} must be null (ignored if provided — returns empty
	 * list since no PhaseTeam rows match a non-null groupId for a LEAGUE phase).
	 *
	 * <p>For {@code layout=GROUPS} with {@code groupId=null}: returns a flat combined-view across all
	 * sub-groups, sorted by {@code points → pointDifference → pointsFor} (D-04). Each
	 * {@link TeamStanding#getGroup()} is set to the team's sub-group (D-05).
	 *
	 * <p>For {@code layout=GROUPS} with a non-null {@code groupId}: returns standings for that single
	 * group only (D-04 per-group view).
	 */
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateStandings(UUID phaseId, UUID groupId) {
		var phase = seasonPhaseService.findById(phaseId);
		var matchScoring = phase.getMatchScoring();
		List<Match> matches = matchRepository.findByMatchdayPhaseId(phaseId);

		// Source teams from PhaseTeam, optionally filtered by groupId
		List<PhaseTeam> rosterRows = (groupId != null)
				? phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId)
				: phaseTeamRepository.findByPhaseId(phaseId);

		Map<UUID, TeamStanding> standingsMap = new HashMap<>();
		for (PhaseTeam pt : rosterRows) {
			var ts = new TeamStanding(pt.getTeam());
			ts.setGroup(pt.getGroup()); // D-05: set nullable group
			standingsMap.put(pt.getTeam().getId(), ts);
		}

		// Build succession map from the phase's season
		Map<UUID, UUID> successionMap = phase.getSeason().buildSuccessionMap();

		// If groupId given, filter matches to only those belonging to that group
		List<Match> filteredMatches = (groupId != null)
				? matches.stream()
					.filter(m -> m.getMatchday().getGroup() != null
							&& m.getMatchday().getGroup().getId().equals(groupId))
					.toList()
				: matches;

		for (Match match : filteredMatches) {
			processMatch(match, standingsMap, matchScoring, successionMap);
		}

		List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
		standings.removeIf(s -> s.getPlayed() == 0);
		standings.sort(Comparator
				.comparing(TeamStanding::getPoints, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

		log.debug("Calculated standings for phase {} group {}: {} teams", phaseId, groupId, standings.size());
		return standings;
	}

	/**
	 * Calculates standings with Buchholz tiebreaker for the given phase and optional group.
	 *
	 * <p>D-06: For {@code layout=GROUPS} with a non-null {@code groupId}: Buchholz is used as the
	 * tiebreaker (per-group Swiss pairing, opponents all within the same group).
	 *
	 * <p>D-06: For {@code layout=GROUPS} with {@code groupId=null} (combined-view): the {@code buchholz}
	 * field on each {@link TeamStanding} is populated for display, but Buchholz is NOT used as a
	 * tiebreaker — falls back to {@code points → pointDifference → pointsFor} to avoid cross-group
	 * opposition bias.
	 *
	 * <p>For {@code layout=LEAGUE} (groupId always null): Buchholz is used as tiebreaker, matching
	 * legacy behaviour.
	 */
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateStandingsWithBuchholz(UUID phaseId, UUID groupId) {
		var standings = calculateStandings(phaseId, groupId);
		if (standings.isEmpty()) return standings;

		var phase = seasonPhaseService.findById(phaseId);
		boolean isGroupsCombinedView = (phase.getLayout() == PhaseLayout.GROUPS && groupId == null);

		// Populate buchholz field for display (regardless of whether it's used as tiebreaker)
		Map<UUID, Integer> buchholzScores = calculateBuchholzScoresForPhase(phase, groupId);
		for (var standing : standings) {
			standing.setBuchholz(buchholzScores.getOrDefault(standing.getTeam().getId(), 0));
		}

		if (isGroupsCombinedView) {
			// D-06: combined-view — Buchholz populated for display but NOT used as tiebreaker
			standings.sort(Comparator
					.comparing(TeamStanding::getPoints, Comparator.reverseOrder())
					.thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
					.thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));
			log.debug("Calculated combined-view standings with Buchholz (display-only) for phase {}: {} teams",
					phaseId, standings.size());
		} else {
			// LEAGUE or per-group GROUPS: Buchholz used as tiebreaker
			standings.sort(Comparator
					.comparing(TeamStanding::getPoints, Comparator.reverseOrder())
					.thenComparing(TeamStanding::getBuchholz, Comparator.reverseOrder())
					.thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
					.thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));
			log.debug("Calculated standings with Buchholz for phase {} group {}: {} teams",
					phaseId, groupId, standings.size());
		}

		return standings;
	}

	// ---------------------------------------------------------------------------
	// @Deprecated seasonId-overload bridges (D-01, D-03) — remove in Phase 60
	// ---------------------------------------------------------------------------

	/**
	 * @deprecated Phase 58: use {@link #calculateStandings(UUID, UUID)}. Remove in Phase 60 alongside UI cutover.
	 *
	 * <p>Backward-compat bridge: delegates to the canonical phase-aware method via the REGULAR phase.
	 * Falls back to the legacy season-based path (via {@code seasonRepository}) if no REGULAR phase
	 * exists for the season — this covers seasons created before Phase 57 data migration ran
	 * (e.g., test data created at runtime in integration tests that bypass Flyway's V4 migration).
	 * Uses {@code findByType} (returns Optional) to avoid marking the transaction rollback-only.
	 */
	@Deprecated
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateStandings(UUID seasonId) {
		var regularPhaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (regularPhaseOpt.isPresent()) {
			return calculateStandings(regularPhaseOpt.get().getId(), null);
		}
		// Pre-Phase-57 season (no REGULAR phase row) — fall back to legacy season-level query
		log.debug("No REGULAR phase for season {} — falling back to legacy season-level standings", seasonId);
		return calculateStandingsLegacy(seasonId);
	}

	// Legacy season-level implementation — kept only for the @Deprecated calculateStandings(UUID seasonId)
	// bridge fallback path on seasons without a REGULAR SeasonPhase row. The Buchholz variant of this
	// fallback was removed in Phase 60 alongside the @Deprecated calculateStandingsWithBuchholz(seasonId)
	// overload — Phase 61 MIGR-06 will remove the remaining season-level path.

	private List<TeamStanding> calculateStandingsLegacy(UUID seasonId) {
		var season = seasonRepository.findById(seasonId).orElse(null);
		if (season == null) return List.of();

		var matchScoring = season.getMatchScoring();
		List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
		Map<UUID, TeamStanding> standingsMap = new HashMap<>();
		Map<UUID, UUID> successionMap = season.buildSuccessionMap();

		for (Team team : season.getActiveTeams()) {
			standingsMap.put(team.getId(), new TeamStanding(team));
		}
		for (Match match : matches) {
			processMatch(match, standingsMap, matchScoring, successionMap);
		}

		List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
		standings.removeIf(s -> s.getPlayed() == 0);
		standings.sort(Comparator
				.comparing(TeamStanding::getPoints, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

		log.debug("Calculated standings (legacy) for season {}: {} teams", seasonId, standings.size());
		return standings;
	}

	// ---------------------------------------------------------------------------
	// Alltime aggregation (unchanged public API — D-09 structurally stable)
	// ---------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<TeamStanding> calculateAlltimeStandings() {
		return calculateAlltimeStandings(seasonRepository.findAll());
	}

	/**
	 * Calculates alltime standings restricted to the given seasons.
	 * Used by the site generator to exclude Test seasons from public pages.
	 */
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateAlltimeStandings(List<Season> seasons) {
		Map<UUID, TeamStanding> alltimeMap = new HashMap<>();

		for (Season season : seasons) {
			List<TeamStanding> seasonStandings = calculateStandings(season.getId());
			if (seasonStandings.isEmpty()) continue;

			for (TeamStanding standing : seasonStandings) {
				Team parentTeam = standing.getTeam().getParentOrSelf();
				TeamStanding alltime = alltimeMap.computeIfAbsent(
						parentTeam.getId(), id -> new TeamStanding(parentTeam));
				alltime.merge(standing);
			}
		}

		List<TeamStanding> result = new ArrayList<>(alltimeMap.values());
		result.sort(Comparator
				.comparing(TeamStanding::getPoints, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
				.thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

		log.debug("Calculated alltime standings: {} teams across {} seasons", result.size(), seasons.size());
		return result;
	}

	// ---------------------------------------------------------------------------
	// Private helpers
	// ---------------------------------------------------------------------------

	private Map<UUID, Integer> calculateBuchholzScores(UUID seasonId) {
		var season = seasonRepository.findById(seasonId).orElse(null);
		if (season == null) return Map.of();

		Map<UUID, UUID> successionMap = season.buildSuccessionMap();

		// Build points map from standings
		var standings = calculateStandings(seasonId);
		Map<UUID, Integer> pointsMap = standings.stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), TeamStanding::getPoints));

		// Build opponents map from races (same logic as SwissPairingService.getPlayedOpponents)
		List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
		Map<UUID, Set<UUID>> opponents = new HashMap<>();
		for (Race race : races) {
			if (race.isBye() || race.getAwayTeam() == null) continue;
			UUID home = successionMap.getOrDefault(race.getHomeTeam().getId(), race.getHomeTeam().getId());
			UUID away = successionMap.getOrDefault(race.getAwayTeam().getId(), race.getAwayTeam().getId());
			opponents.computeIfAbsent(home, k -> new HashSet<>()).add(away);
			opponents.computeIfAbsent(away, k -> new HashSet<>()).add(home);
		}

		// Calculate Buchholz as sum of opponents' points
		Map<UUID, Integer> buchholz = new HashMap<>();
		for (var entry : opponents.entrySet()) {
			int sum = entry.getValue().stream()
					.mapToInt(oppId -> pointsMap.getOrDefault(oppId, 0))
					.sum();
			buchholz.put(entry.getKey(), sum);
		}

		return buchholz;
	}

	/**
	 * Calculates Buchholz scores for a phase-aware context.
	 * For LEAGUE phases or per-group GROUPS phases, delegates to the season-level Buchholz
	 * using the phase's parent season (since the existing raceRepository finder is season-scoped).
	 * For GROUPS combined-view (groupId=null), calculates from the phase's season as well
	 * (Buchholz is display-only in combined-view per D-06).
	 */
	private Map<UUID, Integer> calculateBuchholzScoresForPhase(SeasonPhase phase, UUID groupId) {
		// Delegate to season-level calculation — the raceRepository finder is season-scoped.
		// This is safe: for GROUPS combined-view, Buchholz is display-only (D-06).
		// For LEAGUE and per-group, the season-level calculation is equivalent.
		return calculateBuchholzScores(phase.getSeason().getId());
	}

	private void processMatch(Match match, Map<UUID, TeamStanding> standingsMap,
	                          MatchScoring matchScoring, Map<UUID, UUID> successionMap) {
		if (match.isBye()) {
			UUID homeId = resolveTeamId(match.getHomeTeam().getId(), successionMap);
			var homeStanding = standingsMap.get(homeId);
			if (homeStanding != null) {
				homeStanding.addWin();
				homeStanding.addMatchPoints(matchScoring.getPointsWin());
			}
			return;
		}

		if (match.getHomeScore() == null || match.getAwayScore() == null) return;

		int homeTotal = match.getHomeScore();
		int awayTotal = match.getAwayScore();

		UUID homeId = resolveTeamId(match.getHomeTeam().getId(), successionMap);
		UUID awayId = match.getAwayTeam() != null ? resolveTeamId(match.getAwayTeam().getId(), successionMap) : null;

		var homeStanding = standingsMap.get(homeId);
		var awayStanding = awayId != null ? standingsMap.get(awayId) : null;

		if (homeStanding == null) return;

		homeStanding.addPointsFor(homeTotal);
		homeStanding.addPointsAgainst(awayTotal);
		if (awayStanding != null) {
			awayStanding.addPointsFor(awayTotal);
			awayStanding.addPointsAgainst(homeTotal);
		}

		if (homeTotal > awayTotal) {
			homeStanding.addWin();
			homeStanding.addMatchPoints(matchScoring.getPointsWin());
			if (awayStanding != null) {
				awayStanding.addLoss();
				awayStanding.addMatchPoints(matchScoring.getPointsLoss());
			}
		} else if (homeTotal < awayTotal) {
			homeStanding.addLoss();
			homeStanding.addMatchPoints(matchScoring.getPointsLoss());
			if (awayStanding != null) {
				awayStanding.addWin();
				awayStanding.addMatchPoints(matchScoring.getPointsWin());
			}
		} else {
			homeStanding.addDraw();
			homeStanding.addMatchPoints(matchScoring.getPointsDraw());
			if (awayStanding != null) {
				awayStanding.addDraw();
				awayStanding.addMatchPoints(matchScoring.getPointsDraw());
			}
		}
	}

	private UUID resolveTeamId(UUID teamId, Map<UUID, UUID> successionMap) {
		return successionMap.getOrDefault(teamId, teamId);
	}

	// ---------------------------------------------------------------------------
	// Inner class TeamStanding (D-05: nullable group field added)
	// ---------------------------------------------------------------------------

	public static class TeamStanding {
		private final Team team;
		private int wins;
		private int draws;
		private int losses;
		private int points;
		private int pointsFor;
		private int pointsAgainst;
		private int buchholz;
		private SeasonPhaseGroup group; // D-05: nullable — null for LEAGUE, set for GROUPS

		public TeamStanding(Team team) {
			this.team = team;
		}

		public void addWin() {
			wins++;
		}

		public void addDraw() {
			draws++;
		}

		public void addLoss() {
			losses++;
		}

		public void addMatchPoints(int pts) {
			points += pts;
		}

		public void addPointsFor(int pts) {
			pointsFor += pts;
		}

		public void addPointsAgainst(int pts) {
			pointsAgainst += pts;
		}

		public void merge(TeamStanding other) {
			this.wins += other.wins;
			this.draws += other.draws;
			this.losses += other.losses;
			this.points += other.points;
			this.pointsFor += other.pointsFor;
			this.pointsAgainst += other.pointsAgainst;
		}

		public Team getTeam() {
			return team;
		}

		public int getWins() {
			return wins;
		}

		public int getDraws() {
			return draws;
		}

		public int getLosses() {
			return losses;
		}

		public int getPlayed() {
			return wins + draws + losses;
		}

		public int getPoints() {
			return points;
		}

		public int getPointsFor() {
			return pointsFor;
		}

		public int getPointsAgainst() {
			return pointsAgainst;
		}

		public int getPointDifference() {
			return pointsFor - pointsAgainst;
		}

		public String getPointsRatio() {
			return pointsFor + ":" + pointsAgainst;
		}

		public int getBuchholz() {
			return buchholz;
		}

		public void setBuchholz(int buchholz) {
			this.buchholz = buchholz;
		}

		/** D-05: nullable — null for LEAGUE-layout phases, set to team's sub-group for GROUPS-layout. */
		public SeasonPhaseGroup getGroup() {
			return group;
		}

		/** D-05: set by StandingsService when GROUPS-layout; leave null for LEAGUE. */
		public void setGroup(SeasonPhaseGroup group) {
			this.group = group;
		}

		public String getMatchRecord() {
			return wins + " - " + losses + " - " + draws;
		}
	}
}
