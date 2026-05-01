package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayGeneratorService {

	private final SeasonRepository seasonRepository;
	private final MatchdayRepository matchdayRepository;
	private final MatchService matchService;
	private final SeasonPhaseService seasonPhaseService;
	private final PhaseTeamRepository phaseTeamRepository;
	private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;

	// ---------------------------------------------------------------------------
	// Canonical phase/group-aware method (D-16, SVC-04)
	// ---------------------------------------------------------------------------

	/**
	 * Generates matchdays for the given phase and optional group.
	 *
	 * <p>D-16 layout validation:
	 * <ul>
	 *   <li>For {@code layout=LEAGUE}: {@code groupId} MUST be null — throws {@link IllegalArgumentException} if not.</li>
	 *   <li>For {@code layout=GROUPS}: {@code groupId} MUST be non-null — throws {@link IllegalArgumentException} if null.</li>
	 * </ul>
	 *
	 * <p>Teams are sourced from {@link PhaseTeamRepository} (not {@code season.getEligibleTeams()}).
	 * Generated matchdays are linked to both {@code phase} and, for GROUPS layout, to the {@code group}.
	 */
	@Transactional
	public void generate(UUID phaseId, UUID groupId, int numberOfRounds, boolean homeAndAway) {
		var phase = seasonPhaseService.findById(phaseId);

		// D-16 layout validation
		if (phase.getLayout() == PhaseLayout.LEAGUE && groupId != null) {
			throw new IllegalArgumentException(
					"LEAGUE layout requires groupId=null, got: " + groupId);
		}
		if (phase.getLayout() == PhaseLayout.GROUPS && groupId == null) {
			throw new IllegalArgumentException(
					"GROUPS layout requires non-null groupId");
		}

		if (phase.getFormat() == SeasonFormat.SWISS) {
			throw new IllegalArgumentException(
					"Generator does not support Swiss format — use Swiss Rounds instead");
		}

		// Pre-existing matchdays check per phase/group
		var existing = (groupId != null)
				? matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId)
				: matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId);
		if (!existing.isEmpty()) {
			throw new IllegalStateException("Phase/group already has matchdays — delete them first");
		}

		// Teams from PhaseTeam roster (D-16 — replaces season.getEligibleTeams())
		var rosterRows = (groupId != null)
				? phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId)
				: phaseTeamRepository.findByPhaseId(phaseId);
		var teams = rosterRows.stream().map(PhaseTeam::getTeam).toList();
		if (teams.size() < 2) {
			throw new IllegalStateException("Need at least 2 teams to generate matchdays");
		}

		SeasonPhaseGroup group = (groupId != null)
				? seasonPhaseGroupRepository.findById(groupId)
						.orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId))
				: null;

		List<List<int[]>> rounds = circleMethod(teams.size(), numberOfRounds);

		// Phase 61 MIGR-06: Matchday is bound exclusively via phase.
		int sortIndex = 1;
		for (var round : rounds) {
			var matchday = new Matchday(phase, "MD " + sortIndex, sortIndex);
			if (group != null) matchday.setGroup(group);            // T-58-04-02 mitigation
			matchday = matchdayRepository.save(matchday);
			createMatchesForRound(matchday, round, teams, false);
			sortIndex++;
		}

		if (homeAndAway) {
			for (var round : rounds) {
				var matchday = new Matchday(phase, "MD " + sortIndex, sortIndex);
				if (group != null) matchday.setGroup(group);
				matchday = matchdayRepository.save(matchday);
				createMatchesForRound(matchday, round, teams, true);
				sortIndex++;
			}
		}

		log.info("Generated {} matchdays for phase {} group {}", sortIndex - 1, phaseId, groupId);
	}

	// ---------------------------------------------------------------------------
	// Form data
	// ---------------------------------------------------------------------------

	public GeneratorFormData getFormData(UUID seasonId) {
		var season = seasonRepository.findById(seasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
		var regularPhaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		SeasonPhase phase = regularPhaseOpt.orElse(null);
		var teams = season.getEligibleTeams();
		int n = teams.size();
		int optimalRounds = (n % 2 == 0) ? n - 1 : n;
		return new GeneratorFormData(season, phase, n, optimalRounds);
	}

	// ---------------------------------------------------------------------------
	// Algorithm helpers (private — unchanged)
	// ---------------------------------------------------------------------------

	/**
	 * Circle method (polygon scheduling) for round-robin tournament scheduling.
	 * Fixes team[0], rotates team[1..N-1]. For odd team counts, a phantom team
	 * is added to create byes.
	 * <p>
	 * Returns list of rounds, each round is a list of [homeIdx, awayIdx] pairs.
	 * awayIdx == -1 means bye.
	 */
	List<List<int[]>> circleMethod(int teamCount, int maxRounds) {
		boolean odd = teamCount % 2 != 0;
		int n = odd ? teamCount + 1 : teamCount;
		int[] circle = new int[n];
		for (int i = 0; i < n; i++) circle[i] = i;

		List<List<int[]>> rounds = new ArrayList<>();
		int totalRounds = Math.min(n - 1, maxRounds);

		for (int round = 0; round < totalRounds; round++) {
			List<int[]> pairs = new ArrayList<>();
			for (int i = 0; i < n / 2; i++) {
				int a = circle[i];
				int b = circle[n - 1 - i];

				if (odd && (a == teamCount || b == teamCount)) {
					int realTeam = (a == teamCount) ? b : a;
					pairs.add(new int[]{realTeam, -1});
				} else {
					pairs.add(new int[]{a, b});
				}
			}
			rounds.add(pairs);

			// Rotate: fix circle[0], rotate circle[1..n-1]
			int last = circle[n - 1];
			System.arraycopy(circle, 1, circle, 2, n - 2);
			circle[1] = last;
		}

		balanceHomeAway(rounds, teamCount);
		return rounds;
	}

	/**
	 * Post-process pairings to balance home/away distribution.
	 * For each match, decide which team is home by tracking counts and
	 * swapping to keep the difference ≤ 1.
	 */
	private void balanceHomeAway(List<List<int[]>> rounds, int teamCount) {
		int[] homeCounts = new int[teamCount];
		int[] awayCounts = new int[teamCount];

		for (var pairs : rounds) {
			for (int[] pair : pairs) {
				if (pair[1] == -1) continue;
				int a = pair[0];
				int b = pair[1];

				int aDiff = homeCounts[a] - awayCounts[a];
				int bDiff = homeCounts[b] - awayCounts[b];

				// Assign home to the team with fewer home games
				if (aDiff > bDiff) {
					pair[0] = b;
					pair[1] = a;
					homeCounts[b]++;
					awayCounts[a]++;
				} else if (bDiff > aDiff) {
					// Keep as is
					homeCounts[a]++;
					awayCounts[b]++;
				} else {
					// Tie: alternate
					homeCounts[a]++;
					awayCounts[b]++;
				}
			}
		}
	}

	private void createMatchesForRound(Matchday matchday, List<int[]> pairs, List<Team> teams, boolean reversed) {
		for (int[] pair : pairs) {
			if (pair[1] == -1) {
				matchService.createMatchWithLegs(matchday, teams.get(pair[0]), null, true);
			} else if (reversed) {
				matchService.createMatchWithLegs(matchday, teams.get(pair[1]), teams.get(pair[0]), false);
			} else {
				matchService.createMatchWithLegs(matchday, teams.get(pair[0]), teams.get(pair[1]), false);
			}
		}
	}

	// ---------------------------------------------------------------------------
	// GeneratorFormData record (A7 shape: keep Season for template compat, add SeasonPhase phase)
	// ---------------------------------------------------------------------------

	/**
	 * Form data for the matchday generator UI.
	 *
	 * <p>Carries both {@link Season} (for backward-compat template references) and
	 * {@link SeasonPhase} (for Phase-60 UI cutover). Phase-60 can remove {@code season}
	 * once templates are updated. (A7 minimum-churn shape.)
	 */
	public record GeneratorFormData(Season season, SeasonPhase phase, int teamCount, int optimalRounds) {
	}
}
