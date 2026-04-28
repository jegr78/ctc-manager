package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwissPairingService {

	private final SeasonRepository seasonRepository;
	private final RaceRepository raceRepository;
	private final MatchRepository matchRepository;
	private final MatchdayRepository matchdayRepository;
	private final StandingsService standingsService;
	private final SeasonPhaseService seasonPhaseService;
	private final PhaseTeamRepository phaseTeamRepository;
	private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;

	// ---------------------------------------------------------------------------
	// Canonical phase/group-aware methods (D-17, D-21, SVC-04)
	// ---------------------------------------------------------------------------

	/**
	 * Generates the next Swiss round for the given phase and group.
	 *
	 * <p>D-17 layout validation: LEAGUE requires {@code groupId=null}; GROUPS requires non-null groupId.
	 * D-21 per-group isolation: each group tracks its own round counter and bye list.
	 * Pitfall 6: calls {@link StandingsService#calculateStandings(UUID, UUID)} with the phase-aware
	 * signature (depends on Plan 58-02 having shipped first).
	 */
	@Transactional
	public Matchday generateNextRound(UUID phaseId, UUID groupId) {
		var phase = seasonPhaseService.findById(phaseId);
		validateLayoutAndGroupId(phase, groupId);

		if (phase.getFormat() != SeasonFormat.SWISS) {
			throw new IllegalArgumentException("Phase is not in Swiss format");
		}

		var matchdays = getMatchdaysForPhaseGroup(phaseId, groupId);
		int currentRound = matchdays.size();

		if (phase.getTotalRounds() != null && currentRound >= phase.getTotalRounds()) {
			throw new IllegalStateException("All rounds have been generated");
		}

		// Check that all races in the latest round have results
		if (!matchdays.isEmpty()) {
			var lastMatchday = matchdays.get(matchdays.size() - 1);
			var lastRaces = raceRepository.findByMatchdayId(lastMatchday.getId());
			boolean allComplete = lastRaces.stream()
					.allMatch(r -> r.isBye()
							|| !r.getResults().isEmpty()
							|| (r.getHomeScore() != null && r.getAwayScore() != null));
			if (!allComplete) {
				throw new IllegalStateException("Current round has incomplete races");
			}
		}

		int roundNumber = currentRound + 1;
		var matchday = new Matchday(phase.getSeason(), "Round " + roundNumber, roundNumber);
		matchday.setPhase(phase);                                   // T-58-04-01 mitigation
		SeasonPhaseGroup group = resolveGroup(groupId);
		if (group != null) matchday.setGroup(group);               // T-58-04-02 mitigation
		matchday = matchdayRepository.save(matchday);

		// Teams from PhaseTeam roster (D-17 per-group isolation)
		var rosterRows = (groupId != null)
				? phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId)
				: phaseTeamRepository.findByPhaseId(phaseId);
		List<Team> teams = rosterRows.stream().map(PhaseTeam::getTeam).toList();

		List<Race> pairings;

		if (currentRound == 0) {
			var teamsMutable = new ArrayList<>(teams);
			pairings = generateFirstRoundPairings(matchday, teamsMutable);
		} else {
			var teamsMutable = new ArrayList<>(teams);
			pairings = generateSubsequentRoundPairings(matchday, teamsMutable, phaseId, groupId, phase.getSeason());
		}

		for (Race race : pairings) {
			raceRepository.save(race);
		}

		log.info("Generated Swiss round {} for phase {} group {}: {} pairings",
				roundNumber, phaseId, groupId, pairings.size());
		return matchday;
	}

	/**
	 * Returns the set of team IDs that have already received a bye in this phase/group.
	 *
	 * <p>D-21: per-group isolation — only byes from races in this group's matchdays are considered.
	 */
	public Set<UUID> getByeTeams(UUID phaseId, UUID groupId) {
		var phase = seasonPhaseService.findById(phaseId);
		validateLayoutAndGroupId(phase, groupId);
		var matchdays = getMatchdaysForPhaseGroup(phaseId, groupId);
		Set<UUID> byeTeams = new HashSet<>();
		for (var md : matchdays) {
			List<Race> races = raceRepository.findByMatchdayId(md.getId());
			races.stream()
					.filter(Race::isBye)
					.map(r -> r.getHomeTeam().getId())
					.forEach(byeTeams::add);
		}
		return byeTeams;
	}

	/**
	 * Returns the current round number (= number of matchdays generated) for this phase/group.
	 *
	 * <p>D-21: per-group isolation — counts only matchdays belonging to this group.
	 */
	public int getCurrentRound(UUID phaseId, UUID groupId) {
		var phase = seasonPhaseService.findById(phaseId);
		validateLayoutAndGroupId(phase, groupId);
		return getMatchdaysForPhaseGroup(phaseId, groupId).size();
	}

	/**
	 * Returns true if the current (last) round for this phase/group is complete, i.e. all
	 * races have results or the round doesn't exist yet.
	 *
	 * <p>D-21: per-group isolation — only inspects this group's matchdays.
	 */
	public boolean isCurrentRoundComplete(UUID phaseId, UUID groupId) {
		var phase = seasonPhaseService.findById(phaseId);
		validateLayoutAndGroupId(phase, groupId);
		var matchdays = getMatchdaysForPhaseGroup(phaseId, groupId);
		if (matchdays.isEmpty()) return true;

		var lastMatchday = matchdays.get(matchdays.size() - 1);
		var lastRaces = raceRepository.findByMatchdayId(lastMatchday.getId());
		return lastRaces.stream()
				.allMatch(r -> r.isBye()
						|| !r.getResults().isEmpty()
						|| (r.getHomeScore() != null && r.getAwayScore() != null));
	}

	// ---------------------------------------------------------------------------
	// @Deprecated seasonId-overload bridges (D-01, D-03) — remove in Phase 60
	// ---------------------------------------------------------------------------

	/**
	 * @deprecated Phase 58 D-01: use {@link #generateNextRound(UUID, UUID)} with phaseId.
	 * Delegates to the REGULAR phase of the season with groupId=null. Falls back to the
	 * legacy season-based path if no REGULAR phase exists (pre-Phase-57 test data).
	 * Remove in Phase 60 alongside UI cutover.
	 */
	@Deprecated
	@Transactional
	public Matchday generateNextRound(UUID seasonId) {
		var phaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (phaseOpt.isPresent()) {
			return generateNextRound(phaseOpt.get().getId(), null);
		}
		return generateNextRoundLegacy(seasonId);
	}

	/**
	 * @deprecated Phase 58 D-01: use {@link #getByeTeams(UUID, UUID)} with phaseId.
	 * Falls back to legacy season-based path if no REGULAR phase exists.
	 * Remove in Phase 60 alongside UI cutover.
	 */
	@Deprecated
	public Set<UUID> getByeTeams(UUID seasonId) {
		var phaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (phaseOpt.isPresent()) {
			return getByeTeams(phaseOpt.get().getId(), null);
		}
		return getByeTeamsLegacy(seasonId);
	}

	/**
	 * @deprecated Phase 58 D-01: use {@link #getCurrentRound(UUID, UUID)} with phaseId.
	 * Falls back to legacy season-based path if no REGULAR phase exists.
	 * Remove in Phase 60 alongside UI cutover.
	 */
	@Deprecated
	public int getCurrentRound(UUID seasonId) {
		var phaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (phaseOpt.isPresent()) {
			return getCurrentRound(phaseOpt.get().getId(), null);
		}
		return getCurrentRoundLegacy(seasonId);
	}

	/**
	 * @deprecated Phase 58 D-01: use {@link #isCurrentRoundComplete(UUID, UUID)} with phaseId.
	 * Falls back to legacy season-based path if no REGULAR phase exists.
	 * Remove in Phase 60 alongside UI cutover.
	 */
	@Deprecated
	public boolean isCurrentRoundComplete(UUID seasonId) {
		var phaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (phaseOpt.isPresent()) {
			return isCurrentRoundComplete(phaseOpt.get().getId(), null);
		}
		return isCurrentRoundCompleteLegacy(seasonId);
	}

	// ---------------------------------------------------------------------------
	// Legacy fallback methods — used by @Deprecated bridges when no REGULAR phase
	// exists (pre-Phase-57 test data / seasons created before V4 migration ran).
	// Remove in Phase 60 alongside the @Deprecated overloads.
	// ---------------------------------------------------------------------------

	private Matchday generateNextRoundLegacy(UUID seasonId) {
		var season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
		if (season.getFormat() != SeasonFormat.SWISS) {
			throw new IllegalArgumentException("Season is not in Swiss format");
		}
		var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId);
		int currentRound = matchdays.size();
		if (season.getTotalRounds() != null && currentRound >= season.getTotalRounds()) {
			throw new IllegalStateException("All rounds have been generated");
		}
		if (!matchdays.isEmpty()) {
			var lastMatchday = matchdays.get(matchdays.size() - 1);
			var lastRaces = raceRepository.findByMatchdayId(lastMatchday.getId());
			boolean allComplete = lastRaces.stream()
					.allMatch(r -> r.isBye()
							|| !r.getResults().isEmpty()
							|| (r.getHomeScore() != null && r.getAwayScore() != null));
			if (!allComplete) {
				throw new IllegalStateException("Current round has incomplete races");
			}
		}
		int roundNumber = currentRound + 1;
		var matchday = new Matchday(season, "Round " + roundNumber, roundNumber);
		matchday = matchdayRepository.save(matchday);
		List<Team> teams = season.getEligibleTeams();
		List<Race> pairings;
		if (currentRound == 0) {
			pairings = generateFirstRoundPairings(matchday, new ArrayList<>(teams));
		} else {
			pairings = generateSubsequentRoundPairingsLegacy(matchday, new ArrayList<>(teams), seasonId, season);
		}
		for (Race race : pairings) {
			raceRepository.save(race);
		}
		log.info("Generated Swiss round {} for season {}: {} pairings (legacy)", roundNumber, season.getName(), pairings.size());
		return matchday;
	}

	private List<Race> generateSubsequentRoundPairingsLegacy(Matchday matchday, List<Team> teams,
	                                                          UUID seasonId, Season season) {
		var standings = standingsService.calculateStandings(seasonId);
		Map<UUID, Integer> pointsMap = standings.stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), StandingsService.TeamStanding::getPoints));
		teams.sort((a, b) -> Integer.compare(
				pointsMap.getOrDefault(b.getId(), 0),
				pointsMap.getOrDefault(a.getId(), 0)));
		Map<UUID, UUID> successionMap = season.buildSuccessionMap();
		Map<UUID, Set<UUID>> playedOpponents = getPlayedOpponents(seasonId, successionMap);
		Set<UUID> byeTeams = getByeTeamsLegacy(seasonId);
		return createSwissPairings(matchday, teams, playedOpponents, byeTeams);
	}

	private Set<UUID> getByeTeamsLegacy(UUID seasonId) {
		List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
		return races.stream()
				.filter(Race::isBye)
				.map(r -> r.getHomeTeam().getId())
				.collect(Collectors.toSet());
	}

	private int getCurrentRoundLegacy(UUID seasonId) {
		var season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
		return season.getMatchdays().size();
	}

	private boolean isCurrentRoundCompleteLegacy(UUID seasonId) {
		var season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
		if (season.getMatchdays().isEmpty()) return true;
		var lastMatchday = season.getMatchdays().get(season.getMatchdays().size() - 1);
		return lastMatchday.getRaces().stream()
				.allMatch(r -> r.isBye()
						|| !r.getResults().isEmpty()
						|| (r.getHomeScore() != null && r.getAwayScore() != null));
	}

	// ---------------------------------------------------------------------------
	// Buchholz (unchanged public API — delegates to season-level legacy)
	// ---------------------------------------------------------------------------

	public Map<UUID, Integer> calculateBuchholz(UUID seasonId) {
		var season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
		Map<UUID, UUID> successionMap = season.buildSuccessionMap();

		var standings = standingsService.calculateStandings(seasonId);
		Map<UUID, Integer> pointsMap = standings.stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), StandingsService.TeamStanding::getPoints));

		Map<UUID, Set<UUID>> opponents = getPlayedOpponents(seasonId, successionMap);
		Map<UUID, Integer> buchholz = new HashMap<>();

		for (var entry : opponents.entrySet()) {
			int sum = entry.getValue().stream()
					.mapToInt(oppId -> pointsMap.getOrDefault(oppId, 0))
					.sum();
			buchholz.put(entry.getKey(), sum);
		}

		return buchholz;
	}

	// ---------------------------------------------------------------------------
	// Public helpers for external callers (legacy API surface)
	// ---------------------------------------------------------------------------

	public Map<UUID, Set<UUID>> getPlayedOpponents(UUID seasonId) {
		return getPlayedOpponents(seasonId, Map.of());
	}

	// ---------------------------------------------------------------------------
	// Private helpers — unchanged algorithm, only callers updated
	// ---------------------------------------------------------------------------

	private List<Matchday> getMatchdaysForPhaseGroup(UUID phaseId, UUID groupId) {
		return (groupId != null)
				? matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId)
				: matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId);
	}

	private SeasonPhaseGroup resolveGroup(UUID groupId) {
		if (groupId == null) return null;
		return seasonPhaseGroupRepository.findById(groupId)
				.orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));
	}

	/**
	 * D-17 layout validation: LEAGUE requires groupId=null; GROUPS requires non-null groupId.
	 */
	private void validateLayoutAndGroupId(SeasonPhase phase, UUID groupId) {
		if (phase.getLayout() == PhaseLayout.LEAGUE && groupId != null) {
			throw new IllegalArgumentException("LEAGUE layout requires groupId=null, got: " + groupId);
		}
		if (phase.getLayout() == PhaseLayout.GROUPS && groupId == null) {
			throw new IllegalArgumentException("GROUPS layout requires non-null groupId");
		}
	}

	private List<Race> generateFirstRoundPairings(Matchday matchday, List<Team> teams) {
		Collections.shuffle(teams);
		return createPairingsFromOrder(matchday, teams);
	}

	private List<Race> generateSubsequentRoundPairings(Matchday matchday, List<Team> teams,
	                                                    UUID phaseId, UUID groupId, Season season) {
		// Pitfall 6: use phase-aware StandingsService (depends on Plan 58-02 GREEN)
		var standings = standingsService.calculateStandings(phaseId, groupId);
		Map<UUID, Integer> pointsMap = standings.stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), StandingsService.TeamStanding::getPoints));

		// Sort teams by points (descending), then by original order for ties
		teams.sort((a, b) -> {
			int pa = pointsMap.getOrDefault(a.getId(), 0);
			int pb = pointsMap.getOrDefault(b.getId(), 0);
			return Integer.compare(pb, pa);
		});

		Map<UUID, UUID> successionMap = season.buildSuccessionMap();

		// Get played opponents for each team (resolved through succession) — per phase/group scope
		Map<UUID, Set<UUID>> playedOpponents = getPlayedOpponentsForPhaseGroup(phaseId, groupId, successionMap);

		// Get teams that already had a bye (per group scope — D-21)
		Set<UUID> byeTeamIds = getByeTeams(phaseId, groupId);

		return createSwissPairings(matchday, teams, playedOpponents, byeTeamIds);
	}

	private List<Race> createSwissPairings(Matchday matchday, List<Team> teams,
	                                       Map<UUID, Set<UUID>> playedOpponents,
	                                       Set<UUID> byeTeams) {
		List<Race> pairings = new ArrayList<>();
		List<Team> unpaired = new ArrayList<>(teams);

		// Handle bye for odd number of teams
		if (unpaired.size() % 2 != 0) {
			Team byeTeam = selectByeTeam(unpaired, byeTeams);
			unpaired.remove(byeTeam);
			pairings.add(createRaceWithMatch(matchday, byeTeam, null, true));
		}

		// Pair teams: iterate through sorted list, pair with next available opponent
		while (unpaired.size() >= 2) {
			Team team1 = unpaired.remove(0);
			Set<UUID> team1Opponents = playedOpponents.getOrDefault(team1.getId(), Set.of());

			Team opponent = null;
			for (int i = 0; i < unpaired.size(); i++) {
				Team candidate = unpaired.get(i);
				if (!team1Opponents.contains(candidate.getId())) {
					opponent = candidate;
					unpaired.remove(i);
					break;
				}
			}

			if (opponent == null) {
				// Fallback: all opponents already played, pair with closest rank anyway
				opponent = unpaired.remove(0);
				log.warn("Swiss pairing: forced rematch {} vs {}", team1.getShortName(), opponent.getShortName());
			}

			pairings.add(createRaceWithMatch(matchday, team1, opponent, false));
		}

		return pairings;
	}

	private Team selectByeTeam(List<Team> teams, Set<UUID> byeTeams) {
		// Select lowest-ranked team that hasn't had a bye yet
		for (int i = teams.size() - 1; i >= 0; i--) {
			if (!byeTeams.contains(teams.get(i).getId())) {
				return teams.get(i);
			}
		}
		// All teams had a bye already, pick the lowest-ranked
		return teams.get(teams.size() - 1);
	}

	private List<Race> createPairingsFromOrder(Matchday matchday, List<Team> teams) {
		List<Race> pairings = new ArrayList<>();

		// Handle bye for odd number
		if (teams.size() % 2 != 0) {
			Team byeTeam = teams.remove(teams.size() - 1);
			pairings.add(createRaceWithMatch(matchday, byeTeam, null, true));
		}

		for (int i = 0; i < teams.size(); i += 2) {
			pairings.add(createRaceWithMatch(matchday, teams.get(i), teams.get(i + 1), false));
		}

		return pairings;
	}

	private Race createRaceWithMatch(Matchday matchday, Team homeTeam, Team awayTeam, boolean bye) {
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setBye(bye);
		match = matchRepository.save(match);
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		return race;
	}

	private Map<UUID, Set<UUID>> getPlayedOpponents(UUID seasonId, Map<UUID, UUID> successionMap) {
		List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
		Map<UUID, Set<UUID>> opponents = new HashMap<>();

		for (Race race : races) {
			if (race.isBye() || race.getAwayTeam() == null) continue;
			UUID home = successionMap.getOrDefault(race.getHomeTeam().getId(), race.getHomeTeam().getId());
			UUID away = successionMap.getOrDefault(race.getAwayTeam().getId(), race.getAwayTeam().getId());
			opponents.computeIfAbsent(home, k -> new HashSet<>()).add(away);
			opponents.computeIfAbsent(away, k -> new HashSet<>()).add(home);
		}

		return opponents;
	}

	/**
	 * Returns played opponents scoped to the given phase/group's matchdays (D-21 per-group isolation).
	 */
	private Map<UUID, Set<UUID>> getPlayedOpponentsForPhaseGroup(UUID phaseId, UUID groupId,
	                                                              Map<UUID, UUID> successionMap) {
		var matchdays = getMatchdaysForPhaseGroup(phaseId, groupId);
		Map<UUID, Set<UUID>> opponents = new HashMap<>();
		for (var md : matchdays) {
			List<Race> races = raceRepository.findByMatchdayId(md.getId());
			for (Race race : races) {
				if (race.isBye() || race.getAwayTeam() == null) continue;
				UUID home = successionMap.getOrDefault(race.getHomeTeam().getId(), race.getHomeTeam().getId());
				UUID away = successionMap.getOrDefault(race.getAwayTeam().getId(), race.getAwayTeam().getId());
				opponents.computeIfAbsent(home, k -> new HashSet<>()).add(away);
				opponents.computeIfAbsent(away, k -> new HashSet<>()).add(home);
			}
		}
		return opponents;
	}
}
