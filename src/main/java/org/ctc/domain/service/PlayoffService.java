package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
	private final PlayoffSeedRepository playoffSeedRepository;
	private final RaceRepository raceRepository;
	private final SeasonRepository seasonRepository;
	private final TeamRepository teamRepository;
	private final MatchdayRepository matchdayRepository;
	private final ScoringService scoringService;
	private final PlayoffBracketViewService playoffBracketViewService;
	// D-19: PLAYOFF SeasonPhase auto-creation
	private final SeasonPhaseService seasonPhaseService;

	@Transactional  // D-19: single boundary covers SeasonPhase + Playoff writes (atomicity per Pitfall 2)
	public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams) {
		if (!DEFAULT_ROUND_LABELS.containsKey(numberOfTeams)) {
			throw new IllegalArgumentException("Number of teams must be 2, 4 or 8, got: " + numberOfTeams);
		}

		// D-19: duplicate-Playoff guard — replaces IllegalArgumentException with BusinessRuleException
		// for D-03 consistency (BusinessRuleException maps to 409 via GlobalExceptionHandler).
		if (playoffRepository.findBySeasonId(seasonId).isPresent()) {
			throw new BusinessRuleException("Season already has a playoff phase");
		}

		Season season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));

		// Phase 61 MIGR-06: scoring now lives on the REGULAR phase, not on the season.
		SeasonPhase regular = seasonPhaseService.findRegularPhase(seasonId);

		// D-19: find-or-create the PLAYOFF SeasonPhase. Auto-creates with BRACKET layout,
		// LEAGUE format (D-08 DB-default workaround), sortIndex=10, scoring copied from REGULAR phase.
		SeasonPhase phase = seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF)
				.orElseGet(() -> seasonPhaseService.create(
						seasonId,
						PhaseType.PLAYOFF,
						PhaseLayout.BRACKET,
						/*sortIndex*/ 10,
						name,
						regular.getRaceScoring(),
						regular.getMatchScoring(),
						SeasonFormat.LEAGUE,                            // D-08 DB-default workaround
						/*startDate*/ null,
						/*endDate*/ null,
						/*totalRounds*/ null,
						/*legs*/ 1,
						/*eventDurationMinutes*/ null));

		// Phase 61 MIGR-06: Playoff is bound to PLAYOFF SeasonPhase only; season derived via Convenience-Getter.
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
				name, season.getName(), numberOfTeams, numRounds, phase.getId());
		return playoff;
	}

	@Transactional(readOnly = true)
	public List<Team> getPlayoffTeams(UUID playoffId) {
		Playoff playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		// Phase 61 MIGR-06: M:N playoff_seasons is gone. Teams come from the playoff's
		// canonical season (resolved via Convenience-Getter playoff.getSeason()).
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
		PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));

		if (!matchup.isReady()) {
			throw new IllegalStateException("Matchup is not ready - both teams must be set");
		}

		List<Race> legs = raceRepository.findByPlayoffMatchupId(matchupId);
		if (legs.isEmpty()) {
			throw new IllegalStateException("No races found for matchup");
		}

		UUID team1Id = matchup.getTeam1().getId();

		// Use shared ScoringService.calculateTeamTotals (per D-06 no duplication)
		int team1Total = 0;
		int team2Total = 0;
		for (Race leg : legs) {
			if (leg.getResults().isEmpty()) continue;
			int[] totals = scoringService.calculateTeamTotals(leg.getResults(), leg.getId(), team1Id);
			team1Total += totals[0];
			team2Total += totals[1];
		}

		// Store aggregated scores on matchup
		matchup.setHomeScore(team1Total);
		matchup.setAwayScore(team2Total);

		// Explicit tie handling — ties are not silently resolved
		if (team1Total == team2Total) {
			playoffMatchupRepository.save(matchup);
			throw new IllegalStateException(
					"Tie (%d:%d) — Winner must be set manually".formatted(team1Total, team2Total));
		}

		Team winner = team1Total > team2Total ? matchup.getTeam1() : matchup.getTeam2();
		matchup.setWinner(winner);
		playoffMatchupRepository.save(matchup);

		// Advance winner to next matchup
		if (matchup.getNextMatchup() != null) {
			PlayoffMatchup next = matchup.getNextMatchup();
			if (matchup.getBracketPosition() % 2 == 0) {
				next.setTeam1(winner);
			} else {
				next.setTeam2(winner);
			}
			playoffMatchupRepository.save(next);
		}

		log.info("Matchup winner determined: {} ({}:{}) - advancing to next round",
				winner.getShortName(), team1Total, team2Total);
	}

	@Transactional
	public void setWinnerManually(UUID matchupId, UUID winnerTeamId) {
		PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));

		Team winner = findTeam(winnerTeamId);

		boolean isParticipant = (matchup.getTeam1() != null && matchup.getTeam1().getId().equals(winnerTeamId))
				|| (matchup.getTeam2() != null && matchup.getTeam2().getId().equals(winnerTeamId));
		if (!isParticipant) {
			throw new IllegalArgumentException("Winner must be one of the matchup participants");
		}

		matchup.setWinner(winner);
		playoffMatchupRepository.save(matchup);

		if (matchup.getNextMatchup() != null) {
			PlayoffMatchup next = matchup.getNextMatchup();
			if (matchup.getBracketPosition() % 2 == 0) {
				next.setTeam1(winner);
			} else {
				next.setTeam2(winner);
			}
			playoffMatchupRepository.save(next);
		}

		log.info("Matchup winner set manually: {}", winner.getShortName());
	}

	@Transactional(readOnly = true)
	public PlayoffRound findRoundById(UUID id) {
		return playoffRoundRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffRound", id));
	}

	// --- New service methods (extracted from PlayoffController) ---

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
		// Phase 61 MIGR-06: Convenience-Getter playoff.getSeason() delegates to phase.getSeason().
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
		var matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));

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
		// Phase 61 MIGR-06: link matchday to PLAYOFF phase directly. Season is derived via
		// matchday.getSeason() Convenience-Getter (delegates to phase.getSeason()).
		// Pitfall 4: link to PLAYOFF phase, not REGULAR — otherwise playoff race results
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
				matchup.getRound().getLabel());
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

	// --- Record types for service return data ---

	public record PlayoffListData(Playoff playoff, PlayoffBracketViewService.PlayoffBracketView bracketView,
	                              List<Season> allSeasons, UUID selectedSeasonId) {
	}

	public record MatchupDetailData(PlayoffMatchup matchup, List<Race> legs, Playoff playoff) {
	}
}
