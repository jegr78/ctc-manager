package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for assembling the playoff bracket view (read-only).
 * Builds PlayoffBracketView from domain data, delegating to ScoringService for
 * point aggregation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayoffBracketViewService {

	private final PlayoffRepository playoffRepository;
	private final RaceRepository raceRepository;
	private final PlayoffSeedRepository playoffSeedRepository;
	private final ScoringService scoringService;

	public PlayoffBracketView getBracketView(UUID playoffId) {
		Playoff playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));

		// Fetch all races for this playoff in one query, then group by matchup ID
		List<Race> allRaces = raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId);
		Map<UUID, List<Race>> racesByMatchup = allRaces.stream()
				.collect(Collectors.groupingBy(r -> r.getPlayoffMatchup().getId()));

		// Load seed numbers for bracket display
		Map<UUID, Integer> seedsByTeamId = playoffSeedRepository.findByPlayoffId(playoffId)
				.stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), PlayoffSeed::getSeed));

		List<RoundView> roundViews = new ArrayList<>();
		for (PlayoffRound round : playoff.getRounds()) {
			List<MatchupView> matchupViews = new ArrayList<>();
			for (PlayoffMatchup matchup : round.getMatchups()) {
				List<Race> legs = racesByMatchup.getOrDefault(matchup.getId(), List.of());
				matchupViews.add(buildMatchupView(matchup, legs, seedsByTeamId));
			}
			roundViews.add(new RoundView(round.getLabel(), round.getRoundIndex(), matchupViews));
		}

		return new PlayoffBracketView(playoff.getId(), playoff.getName(), roundViews);
	}

	private MatchupView buildMatchupView(PlayoffMatchup matchup, List<Race> legs,
	                                     Map<UUID, Integer> seedsByTeamId) {
		UUID team1Id = matchup.getTeam1() != null ? matchup.getTeam1().getId() : null;
		UUID team2Id = matchup.getTeam2() != null ? matchup.getTeam2().getId() : null;

		int team1Aggregate = 0;
		int team2Aggregate = 0;
		List<LegView> legViews = new ArrayList<>();

		for (int i = 0; i < legs.size(); i++) {
			Race leg = legs.get(i);
			int homeTotal = 0;
			int awayTotal = 0;

			if (!leg.getResults().isEmpty() && team1Id != null) {
				int[] totals = scoringService.calculateTeamTotals(leg.getResults(), leg.getId(), team1Id);
				homeTotal = totals[0];
				awayTotal = totals[1];
				team1Aggregate += homeTotal;
				team2Aggregate += awayTotal;
			}

			legViews.add(new LegView(leg.getId(), i + 1, homeTotal, awayTotal, !leg.getResults().isEmpty()));
		}

		// Boolean fields instead of string comparison in templates
		boolean team1IsWinner = matchup.getWinner() != null && matchup.getWinner().getId().equals(team1Id);
		boolean team2IsWinner = matchup.getWinner() != null && !team1IsWinner && matchup.isComplete();

		Integer team1Seed = team1Id != null ? seedsByTeamId.get(team1Id) : null;
		Integer team2Seed = team2Id != null ? seedsByTeamId.get(team2Id) : null;

		return new MatchupView(
				matchup.getId(),
				matchup.getBracketPosition(),
				team1Id,
				team2Id,
				matchup.getTeam1() != null ? matchup.getTeam1().getShortName() : null,
				matchup.getTeam2() != null ? matchup.getTeam2().getShortName() : null,
				matchup.getTeam1() != null ? matchup.getTeam1().getLogoUrl() : null,
				matchup.getTeam2() != null ? matchup.getTeam2().getLogoUrl() : null,
				team1Seed,
				team2Seed,
				team1Aggregate,
				team2Aggregate,
				team1IsWinner,
				team2IsWinner,
				matchup.isComplete(),
				legViews
		);
	}

	public record PlayoffBracketView(UUID playoffId, String name, List<RoundView> rounds) {
	}

	public record RoundView(String label, int roundIndex, List<MatchupView> matchups) {
	}

	public record MatchupView(UUID matchupId, int bracketPosition, UUID team1Id, UUID team2Id, String team1ShortName,
	                          String team2ShortName, String team1LogoUrl, String team2LogoUrl, Integer team1Seed,
	                          Integer team2Seed, int team1AggregatePoints, int team2AggregatePoints,
	                          boolean team1IsWinner, boolean team2IsWinner, boolean complete, List<LegView> legs) {
	}

	public record LegView(UUID raceId, int legNumber, int team1Total, int team2Total, boolean hasResults) {
	}
}
