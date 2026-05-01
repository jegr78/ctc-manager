package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	/**
	 * Calculates standings for the given phase and optional group.
	 *
	 * <p>For {@code layout=LEAGUE}: {@code groupId} must be null (ignored if provided — returns empty
	 * list since no PhaseTeam rows match a non-null groupId for a LEAGUE phase).
	 *
	 * <p>For {@code layout=GROUPS} with {@code groupId=null}: returns a flat combined-view across all
	 * sub-groups, sorted by {@code points → pointDifference → pointsFor}. Each
	 * {@link TeamStanding#getGroup()} is set to the team's sub-group.
	 *
	 * <p>For {@code layout=GROUPS} with a non-null {@code groupId}: returns standings for that single
	 * group only.
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
			ts.setGroup(pt.getGroup());
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
	 * <p>For {@code layout=GROUPS} with a non-null {@code groupId}: Buchholz is the tiebreaker
	 * (per-group Swiss pairing, opponents all within the same group).
	 *
	 * <p>For {@code layout=GROUPS} with {@code groupId=null} (combined-view): the {@code buchholz}
	 * field on each {@link TeamStanding} is populated for display, but Buchholz is NOT used as a
	 * tiebreaker — falls back to {@code points → pointDifference → pointsFor} to avoid cross-group
	 * opposition bias.
	 *
	 * <p>For {@code layout=LEAGUE} (groupId always null): Buchholz is used as tiebreaker.
	 */
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateStandingsWithBuchholz(UUID phaseId, UUID groupId) {
		var standings = calculateStandings(phaseId, groupId);
		if (standings.isEmpty()) return standings;

		var phase = seasonPhaseService.findById(phaseId);
		boolean isGroupsCombinedView = (phase.getLayout() == PhaseLayout.GROUPS && groupId == null);

		// Populate buchholz field for display (regardless of whether it's used as tiebreaker)
		Map<UUID, Integer> buchholzScores = calculateBuchholzScoresForPhase(phase);
		for (var standing : standings) {
			standing.setBuchholz(buchholzScores.getOrDefault(standing.getTeam().getId(), 0));
		}

		if (isGroupsCombinedView) {
			// Combined-view: Buchholz populated for display but NOT used as tiebreaker
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
	// SeasonId convenience overload — delegates to the canonical phase-aware method
	// ---------------------------------------------------------------------------

	/**
	 * Convenience overload: resolves the REGULAR phase for the given season and delegates
	 * to {@link #calculateStandings(UUID, UUID)}.
	 */
	@Transactional(readOnly = true)
	public List<TeamStanding> calculateStandings(UUID seasonId) {
		var regular = seasonPhaseService.findRegularPhase(seasonId);
		return calculateStandings(regular.getId(), null);
	}

	// --- Alltime aggregation ---

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
	 * Calculates Buchholz scores for a phase. Delegates to the season-level Buchholz using
	 * the phase's parent season because the underlying race finder is season-scoped.
	 *
	 * <p>This is correct for the current consumers: for GROUPS combined-view Buchholz is
	 * display-only (NOT used as a tiebreaker), and for LEAGUE / per-group GROUPS the
	 * season-level calculation produces equivalent values because the phase's matchdays
	 * are the only matchdays.
	 */
	private Map<UUID, Integer> calculateBuchholzScoresForPhase(SeasonPhase phase) {
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

	public static class TeamStanding {
		private final Team team;
		private int wins;
		private int draws;
		private int losses;
		private int points;
		private int pointsFor;
		private int pointsAgainst;
		private int buchholz;
		private SeasonPhaseGroup group;

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

		/** Nullable — null for LEAGUE-layout phases, set to team's sub-group for GROUPS-layout. */
		public SeasonPhaseGroup getGroup() {
			return group;
		}

		public void setGroup(SeasonPhaseGroup group) {
			this.group = group;
		}

		public String getMatchRecord() {
			return wins + " - " + losses + " - " + draws;
		}
	}
}
