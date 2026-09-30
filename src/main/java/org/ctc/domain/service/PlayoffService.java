package org.ctc.domain.service;

import static org.ctc.util.LogSanitizer.sanitize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for playoff matchup lifecycle management: bracket creation, winner determination,
 * race-to-matchup linkage, and playoff CRUD.
 * Bracket view assembly is delegated to PlayoffBracketViewService.
 * Seeding logic is delegated to PlayoffSeedingService.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayoffService {

	private static final Map<Integer, List<String>> DEFAULT_ROUND_LABELS = Map.of(
			2, List.of("Final"),
			4, List.of("Semifinal", "Final"),
			8, List.of("Quarterfinal", "Semifinal", "Final")
	);
	private final PlayoffRepository playoffRepository;
	private final PlayoffRoundRepository playoffRoundRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final RaceRepository raceRepository;
	private final SeasonRepository seasonRepository;
	private final TeamRepository teamRepository;
	private final MatchdayRepository matchdayRepository;
	private final ScoringService scoringService;
	private final PlayoffBracketViewService playoffBracketViewService;
	private final SeasonPhaseService seasonPhaseService;
	private final RaceLineupRepository raceLineupRepository;

	static final int MAX_REASON_LENGTH = 500;

	/**
	 * Creates a playoff for a season. Atomically auto-creates a PLAYOFF
	 * {@link org.ctc.domain.model.SeasonPhase} (BRACKET layout, sortIndex 10) if one does
	 * not exist, copies scoring from the REGULAR phase, and wires up rounds and matchups
	 * for {@code numberOfTeams} ∈ {2, 4, 8}.
	 *
	 * @throws BusinessRuleException when the season already has a playoff
	 *         (HTTP 409 via {@link org.ctc.admin.controller.GlobalExceptionHandler}).
	 * @throws IllegalArgumentException when {@code numberOfTeams} is not 2, 4, or 8.
	 */
	@Transactional
	public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams) {
		if (!DEFAULT_ROUND_LABELS.containsKey(numberOfTeams)) {
			throw new IllegalArgumentException("Number of teams must be 2, 4 or 8, got: " + numberOfTeams);
		}

		if (playoffRepository.findBySeasonId(seasonId).isPresent()) {
			throw new BusinessRuleException("Season already has a playoff phase");
		}

		Season season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));

		SeasonPhase regular = seasonPhaseService.findRegularPhase(seasonId);

		// Find-or-create PLAYOFF SeasonPhase: BRACKET layout, LEAGUE format,
		// sortIndex=10, scoring copied from REGULAR phase.
		SeasonPhase phase = seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF)
				.orElseGet(() -> seasonPhaseService.create(
						seasonId,
						PhaseType.PLAYOFF,
						PhaseLayout.BRACKET,
						/*sortIndex*/ 10,
						name,
						regular.getRaceScoring(),
						regular.getMatchScoring(),
						SeasonFormat.LEAGUE,
						/*startDate*/ null,
						/*endDate*/ null,
						/*totalRounds*/ null,
						/*legs*/ 1,
						/*eventDurationMinutes*/ null));

		Playoff playoff = new Playoff(phase, name);
		playoff = playoffRepository.save(playoff);

		List<String> labels = DEFAULT_ROUND_LABELS.get(numberOfTeams);
		int numRounds = labels.size();

		// Create rounds and matchups
		List<List<PlayoffMatchup>> allRoundMatchups = new ArrayList<>();
		for (int r = 0; r < numRounds; r++) {
			PlayoffRound round = new PlayoffRound(playoff, labels.get(r), r);
			round = playoffRoundRepository.save(round);
			playoff.getRounds().add(round);

			int matchupsInRound = numberOfTeams / (int) Math.pow(2, r + 1);
			List<PlayoffMatchup> matchups = new ArrayList<>();
			for (int m = 0; m < matchupsInRound; m++) {
				PlayoffMatchup matchup = new PlayoffMatchup(round, m);
				matchup = playoffMatchupRepository.save(matchup);
				round.getMatchups().add(matchup);
				matchups.add(matchup);
			}
			allRoundMatchups.add(matchups);
		}

		// Wire nextMatchup links: each pair of matchups in round N feeds into one matchup in round N+1
		for (int r = 0; r < numRounds - 1; r++) {
			List<PlayoffMatchup> currentRound = allRoundMatchups.get(r);
			List<PlayoffMatchup> nextRound = allRoundMatchups.get(r + 1);
			for (int m = 0; m < currentRound.size(); m++) {
				PlayoffMatchup matchup = currentRound.get(m);
				matchup.setNextMatchup(nextRound.get(m / 2));
				playoffMatchupRepository.save(matchup);
			}
		}

		log.info("Created playoff '{}' for season '{}' with {} teams, {} rounds, linked to PLAYOFF phase {}",
				sanitize(name), sanitize(season.getName()), numberOfTeams, numRounds, phase.getId());
		return playoff;
	}

	/**
	 * Returns the teams eligible for the playoff, derived from the playoff's canonical
	 * season via {@code playoff.getSeason().getTeams()}.
	 */
	@Transactional(readOnly = true)
	public List<Team> getPlayoffTeams(UUID playoffId) {
		Playoff playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		Map<UUID, Team> teamMap = new LinkedHashMap<>();
		for (Team team : playoff.getSeason().getTeams()) {
			teamMap.putIfAbsent(team.getId(), team);
		}
		return new ArrayList<>(teamMap.values());
	}

	private Team findTeam(UUID teamId) {
		return teamRepository.findById(teamId)
				.orElseThrow(() -> new EntityNotFoundException("Team", teamId));
	}

	@Transactional
	public void determineWinner(UUID matchupId) {
		PlayoffMatchup matchup = findMatchup(matchupId);
		requireUndecided(matchup);
		if (!matchup.isReady()) {
			throw new IllegalStateException("Matchup is not ready - both teams must be set");
		}

		List<Race> legs = raceRepository.findByPlayoffMatchupId(matchupId);
		if (legs.isEmpty()) {
			throw new IllegalStateException("No races found for matchup");
		}
		if (!allLegsScored(legs)) {
			throw new IllegalStateException("Every scheduled leg needs results before the winner is determined");
		}

		int[] totals = totals(matchup, legs);
		matchup.setHomeScore(totals[0]);
		matchup.setAwayScore(totals[1]);

		// Explicit tie handling — ties are not silently resolved
		if (totals[0] == totals[1]) {
			playoffMatchupRepository.save(matchup);
			throw new IllegalStateException(
					"Tie (%d:%d) — Winner must be set manually".formatted(totals[0], totals[1]));
		}

		Team winner = totals[0] > totals[1] ? matchup.getTeam1() : matchup.getTeam2();
		decide(matchup, winner, null);
		log.info("Matchup winner determined: {} ({}:{}) - advancing to next round",
				winner.getShortName(), totals[0], totals[1]);
	}

	/**
	 * Declares {@code winnerTeamId} the winner. A winner who is not the points leader of all scheduled
	 * legs (early completion, tie, adjudication) needs a reason, which is stored with the decision.
	 */
	@Transactional
	public void setWinnerManually(UUID matchupId, UUID winnerTeamId, String reason) {
		PlayoffMatchup matchup = findMatchup(matchupId);
		requireUndecided(matchup);
		Team winner = findTeam(winnerTeamId);

		boolean isTeam1 = matchup.getTeam1() != null && matchup.getTeam1().getId().equals(winnerTeamId);
		boolean isTeam2 = matchup.getTeam2() != null && matchup.getTeam2().getId().equals(winnerTeamId);
		if (!isTeam1 && !isTeam2) {
			throw new IllegalArgumentException("Winner must be one of the matchup participants");
		}

		List<Race> legs = raceRepository.findByPlayoffMatchupId(matchupId);
		int[] totals = totals(matchup, legs);
		boolean pointsLeader = !legs.isEmpty() && allLegsScored(legs)
				&& (isTeam1 ? totals[0] > totals[1] : totals[1] > totals[0]);
		String decisionReason = normalizedReason(reason);
		if (!pointsLeader && decisionReason == null) {
			throw new IllegalStateException("A winner that is not the points leader of all scheduled legs needs a reason");
		}

		decide(matchup, winner, decisionReason);
		log.info("Matchup winner set manually: {}", winner.getShortName());
	}

	/**
	 * Reopens a decided matchup for a new decision. Rejected while a later matchup has results or a
	 * winner; otherwise the advancement into the successor and the revoked team's lineups there are
	 * removed, while results, schedules and the decision history stay.
	 */
	@Transactional
	public void reopen(UUID matchupId, String reason) {
		PlayoffMatchup matchup = findMatchup(matchupId);
		String reopenReason = normalizedReason(reason);
		if (reopenReason == null) {
			throw new IllegalStateException("Reopening a matchup needs a reason");
		}
		Team previousWinner = matchup.getWinner();
		if (previousWinner == null) {
			throw new IllegalStateException("The matchup is not decided");
		}
		for (var later = matchup.getNextMatchup(); later != null; later = later.getNextMatchup()) {
			if (later.getWinner() != null || !allLegsUnscored(raceRepository.findByPlayoffMatchupId(later.getId()))) {
				throw new IllegalStateException("A later matchup already has results or a winner. This needs a bracket correction");
			}
		}

		appendHistory(matchup, "REOPENED", previousWinner, reopenReason);
		matchup.setWinner(null);
		matchup.setDecisionReason(null);
		playoffMatchupRepository.save(matchup);
		revokeAdvancement(matchup, previousWinner);
		log.info("Matchup {} reopened, previous winner {}", matchupId, previousWinner.getShortName());
	}

	private PlayoffMatchup findMatchup(UUID matchupId) {
		return playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));
	}

	private static void requireUndecided(PlayoffMatchup matchup) {
		if (matchup.isComplete()) {
			throw new IllegalStateException("The matchup is already decided. Reopen it first");
		}
	}

	private static boolean allLegsScored(List<Race> legs) {
		return legs.stream().noneMatch(leg -> leg.getResults().isEmpty());
	}

	private static boolean allLegsUnscored(List<Race> legs) {
		return legs.stream().allMatch(leg -> leg.getResults().isEmpty());
	}

	private int[] totals(PlayoffMatchup matchup, List<Race> legs) {
		int[] totals = {0, 0};
		if (matchup.getTeam1() == null) {
			return totals;
		}
		for (Race leg : legs) {
			if (!leg.getResults().isEmpty()) {
				int[] legTotals = scoringService.calculateTeamTotals(leg.getResults(), leg.getId(), matchup.getTeam1().getId());
				totals[0] += legTotals[0];
				totals[1] += legTotals[1];
			}
		}
		return totals;
	}

	private static String normalizedReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return null;
		}
		String stripped = reason.strip();
		if (stripped.length() > MAX_REASON_LENGTH) {
			throw new IllegalStateException("The reason may have at most " + MAX_REASON_LENGTH + " characters");
		}
		return stripped;
	}

	private void decide(PlayoffMatchup matchup, Team winner, String reason) {
		matchup.setWinner(winner);
		matchup.setDecisionReason(reason);
		appendHistory(matchup, "DECIDED", winner, reason);
		playoffMatchupRepository.save(matchup);

		PlayoffMatchup next = matchup.getNextMatchup();
		if (next != null) {
			if (feedsFirstSlot(matchup)) {
				next.setTeam1(winner);
			} else {
				next.setTeam2(winner);
			}
			playoffMatchupRepository.save(next);
		}
	}

	private void revokeAdvancement(PlayoffMatchup matchup, Team previousWinner) {
		PlayoffMatchup next = matchup.getNextMatchup();
		if (next == null) {
			return;
		}
		Team advanced = feedsFirstSlot(matchup) ? next.getTeam1() : next.getTeam2();
		if (advanced == null || !advanced.getId().equals(previousWinner.getId())) {
			return;
		}
		if (feedsFirstSlot(matchup)) {
			next.setTeam1(null);
		} else {
			next.setTeam2(null);
		}
		playoffMatchupRepository.save(next);
		UUID revokedClub = previousWinner.getParentOrSelf().getId();
		for (Race race : raceRepository.findByPlayoffMatchupId(next.getId())) {
			raceLineupRepository.findByRaceId(race.getId()).stream()
					.filter(lineup -> lineup.getTeam().getParentOrSelf().getId().equals(revokedClub))
					.forEach(raceLineupRepository::delete);
		}
	}

	private static boolean feedsFirstSlot(PlayoffMatchup matchup) {
		return matchup.getBracketPosition() % 2 == 0;
	}

	private static void appendHistory(PlayoffMatchup matchup, String action, Team winner, String reason) {
		String entry = "%s %s %s (%s:%s)%s".formatted(
				java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), action,
				winner.getShortName(), matchup.getHomeScore(), matchup.getAwayScore(),
				reason == null ? "" : " reason: " + reason);
		String history = matchup.getDecisionHistory();
		matchup.setDecisionHistory(history == null ? entry : history + "\n" + entry);
	}

	@Transactional(readOnly = true)
	public PlayoffRound findRoundById(UUID id) {
		return playoffRoundRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffRound", id));
	}

	@Transactional
	public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams,
	                             LocalDate startDate, LocalDate endDate,
	                             Integer eventDurationMinutes) {
		var playoff = createPlayoff(seasonId, name, numberOfTeams);
		playoff.setStartDate(startDate);
		playoff.setEndDate(endDate);
		playoff.setEventDurationMinutes(eventDurationMinutes);
		return playoffRepository.save(playoff);
	}

	@Transactional(readOnly = true)
	public PlayoffListData getPlayoffListData(UUID seasonId) {
		var allSeasons = seasonRepository.findAll();

		UUID effectiveSeasonId = seasonId;
		if (effectiveSeasonId == null) {
			effectiveSeasonId = allSeasons.stream()
					.filter(Season::isActive)
					.map(Season::getId)
					.findFirst().orElse(null);
		}

		Playoff playoff = null;
		PlayoffBracketViewService.PlayoffBracketView bracketView = null;
		if (effectiveSeasonId != null) {
			var optPlayoff = playoffRepository.findBySeasonId(effectiveSeasonId);
			if (optPlayoff.isPresent()) {
				playoff = optPlayoff.get();
				bracketView = playoffBracketViewService.getBracketView(playoff.getId());
			}
		}

		return new PlayoffListData(playoff, bracketView, allSeasons, effectiveSeasonId);
	}

	@Transactional(readOnly = true)
	public PlayoffListData getPlayoffDetailData(UUID playoffId) {
		var playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		var allSeasons = seasonRepository.findAll();
		var bracketView = playoffBracketViewService.getBracketView(playoffId);
		UUID effectiveSeasonId = playoff.getSeason() != null ? playoff.getSeason().getId() : null;
		return new PlayoffListData(playoff, bracketView, allSeasons, effectiveSeasonId);
	}

	@Transactional
	public PlayoffRound setRoundLegs(UUID roundId, int bestOfLegs) {
		var round = playoffRoundRepository.findById(roundId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffRound", roundId));
		round.setBestOfLegs(bestOfLegs);
		return playoffRoundRepository.save(round);
	}

	@Transactional(readOnly = true)
	public MatchupDetailData getMatchupDetail(UUID matchupId) {
		var matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));
		var legs = raceRepository.findByPlayoffMatchupId(matchupId);
		var playoff = matchup.getRound().getPlayoff();
		return new MatchupDetailData(matchup, legs, playoff);
	}

	@Transactional
	public Race addRaceToMatchup(UUID matchupId, String track, String car, LocalDateTime dateTime) {
		var matchup = findMatchup(matchupId);
		requireUndecided(matchup);

		if (!matchup.isReady()) {
			throw new IllegalStateException("Both teams must be set");
		}

		int existingLegs = raceRepository.findByPlayoffMatchupId(matchupId).size();
		int maxLegs = matchup.getRound().getBestOfLegs();
		if (existingLegs >= maxLegs) {
			throw new IllegalStateException("Maximum number of legs reached (" + maxLegs + ")");
		}

		// Auto-create matchday for this playoff leg
		var playoff = matchup.getRound().getPlayoff();
		int legNumber = existingLegs + 1;
		String label = matchup.getRound().getLabel() + " - Leg " + legNumber;
		// Link matchday to PLAYOFF phase, NOT REGULAR — otherwise playoff race results
		// are misattributed to REGULAR by DriverRankingService.calculateRankingForPhase.
		var matchday = new Matchday(playoff.getPhase(), label,
				100 + matchup.getRound().getRoundIndex() * 10 + legNumber);
		matchday = matchdayRepository.save(matchday);

		var race = new Race();
		race.setMatchday(matchday);
		race.setDateTime(dateTime);
		race.setPlayoffMatchup(matchup);
		race = raceRepository.save(race);

		log.info("Added leg {} to matchup {} (round: {})", legNumber, matchupId,
				sanitize(matchup.getRound().getLabel()));
		return race;
	}

	public UUID getSeasonIdForPlayoff(UUID playoffId) {
		return playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId))
				.getSeason().getId();
	}

	public UUID getSeasonIdForMatchup(UUID matchupId) {
		return playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId))
				.getRound().getPlayoff().getSeason().getId();
	}

	public UUID getSeasonIdForRound(UUID roundId) {
		return playoffRoundRepository.findById(roundId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffRound", roundId))
				.getPlayoff().getSeason().getId();
	}

	/**
	 * Returns the playoff linked to the given SeasonPhase, or empty if none.
	 * Used by SeasonPhaseController to populate the Bracket card on the phase-detail tab.
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<Playoff> findByPhaseId(UUID phaseId) {
		return playoffRepository.findByPhaseId(phaseId);
	}

	public record PlayoffListData(Playoff playoff, PlayoffBracketViewService.PlayoffBracketView bracketView,
	                              List<Season> allSeasons, UUID selectedSeasonId) {
	}

	public record MatchupDetailData(PlayoffMatchup matchup, List<Race> legs, Playoff playoff) {
	}
}
