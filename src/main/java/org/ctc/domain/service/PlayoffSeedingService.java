package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for playoff seeding logic: auto-seeding, manual seed assignment, and seed number persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayoffSeedingService {

	private final PlayoffRepository playoffRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final PlayoffSeedRepository playoffSeedRepository;
	private final TeamRepository teamRepository;
	private final PlayoffBracketViewService playoffBracketViewService;
	private final EntityManager entityManager;
	private final SeasonPhaseService seasonPhaseService;
	private final StandingsService standingsService;
	private final PhaseTeamRepository phaseTeamRepository;

	@Transactional
	public void seedTeam(UUID matchupId, UUID teamId, int slot) {
		PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));

		if (slot == 1) {
			matchup.setTeam1(teamId != null ? findTeam(teamId) : null);
		} else if (slot == 2) {
			matchup.setTeam2(teamId != null ? findTeam(teamId) : null);
		} else {
			throw new IllegalArgumentException("Slot must be 1 or 2, got: " + slot);
		}
		playoffMatchupRepository.save(matchup);
	}

	@Transactional(readOnly = true)
	public SeedingData getSeedingData(UUID playoffId) {
		var playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		var bracket = playoffBracketViewService.getBracketView(playoffId);

		var firstRound = playoff.getRounds().stream()
				.filter(r -> r.getRoundIndex() == 0)
				.findFirst().orElseThrow(() -> new EntityNotFoundException("PlayoffRound", "index=0"));

		Map<UUID, Team> teamMap = new LinkedHashMap<>();
		for (Team team : playoff.getSeason().getTeams()) {
			teamMap.putIfAbsent(team.getId(), team);
		}
		List<Team> teams = new ArrayList<>(teamMap.values());

		Set<UUID> seededTeamIds = firstRound.getMatchups().stream()
				.flatMap(m -> {
					var ids = new ArrayList<UUID>();
					if (m.getTeam1() != null) ids.add(m.getTeam1().getId());
					if (m.getTeam2() != null) ids.add(m.getTeam2().getId());
					return ids.stream();
				})
				.collect(Collectors.toSet());

		Map<UUID, Integer> seedNumbers = playoffSeedRepository.findByPlayoffId(playoffId).stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), PlayoffSeed::getSeed));

		return new SeedingData(playoff, bracket, firstRound, teams, seededTeamIds, seedNumbers);
	}

	@Transactional
	public void saveSeed(UUID playoffId, List<SeedEntry> seeds) {
		for (var entry : seeds) {
			if (entry.teamId() != null) {
				seedTeam(entry.matchupId(), entry.teamId(), entry.slot());
			}
		}

		Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
		for (var entry : seeds) {
			if (entry.teamId() != null && entry.seedNumber() != null) {
				teamSeeds.put(entry.teamId(), entry.seedNumber());
			}
		}
		if (!teamSeeds.isEmpty()) {
			saveSeedNumbers(playoffId, teamSeeds);
		}

		log.info("Seeding saved for playoff {}", playoffId);
	}

	@Transactional
	public void saveSeedNumbers(UUID playoffId, Map<UUID, Integer> teamSeeds) {
		var playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		playoffSeedRepository.deleteByPlayoffId(playoffId);
		entityManager.flush();

		for (var entry : teamSeeds.entrySet()) {
			var team = findTeam(entry.getKey());
			var seed = new PlayoffSeed(playoff, team, entry.getValue());
			playoffSeedRepository.save(seed);
		}
		log.info("Saved {} seed numbers for playoff {}", teamSeeds.size(), playoffId);
	}

	/**
	 * Auto-seeds the bracket. Two flows are supported:
	 *
	 * <p><b>Manual flow (priority):</b> When manually-saved {@link PlayoffSeed} rows exist
	 * (via {@link #saveSeedNumbers}), they are used directly.
	 *
	 * <p><b>Auto flow:</b> When NO manual seeds exist, Top-N teams are pulled from the
	 * REGULAR phase standings (combined-view across groups for GROUPS-layout). Each seeded
	 * team is persisted as a {@link PlayoffSeed} row and added to the PLAYOFF phase's
	 * {@link PhaseTeam} roster.
	 *
	 * <p>If neither source yields seeds, throws {@link IllegalStateException}.
	 */
	@Transactional
	public void autoSeedBracket(UUID playoffId) {
		var seeds = playoffSeedRepository.findByPlayoffId(playoffId);

		List<Team> sortedTeams;

		if (!seeds.isEmpty()) {
			sortedTeams = seeds.stream()
					.sorted(Comparator.comparingInt(PlayoffSeed::getSeed))
					.map(PlayoffSeed::getTeam)
					.toList();
		} else {
			// Derive Top-N from REGULAR-phase standings.
			// If the playoff or round can't be resolved, treat as "no seeds available".
			var playoffOpt = playoffRepository.findById(playoffId);
			if (playoffOpt.isEmpty()) {
				throw new IllegalStateException("No seed numbers assigned yet");
			}
			var playoff = playoffOpt.get();
			var firstRoundOpt = playoff.getRounds().stream()
					.filter(r -> r.getRoundIndex() == 0)
					.findFirst();
			if (firstRoundOpt.isEmpty()) {
				throw new IllegalStateException("No seed numbers assigned yet");
			}
			int totalTeams = firstRoundOpt.get().getMatchups().size() * 2;

			sortedTeams = tryLoadFromRegularStandings(playoff, totalTeams);
			if (sortedTeams == null) {
				throw new IllegalStateException("No seed numbers assigned yet");
			}
		}

		// Resolve the playoff again for the bracket-pairing step (works in both flows)
		var playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		var firstRound = playoff.getRounds().stream()
				.filter(r -> r.getRoundIndex() == 0)
				.findFirst().orElseThrow(() -> new EntityNotFoundException("PlayoffRound", "index=0"));

		var matchups = firstRound.getMatchups().stream()
				.sorted(Comparator.comparingInt(PlayoffMatchup::getBracketPosition))
				.toList();

		int[] matchupOrder = buildBracketOrder(matchups.size());

		int seededTeamCount = sortedTeams.size();
		for (int i = 0; i < matchups.size() && i < matchupOrder.length; i++) {
			int seedIdx = matchupOrder[i];
			var matchup = matchups.get(i);
			matchup.setTeam1(sortedTeams.get(seedIdx));
			matchup.setTeam2(sortedTeams.get(seededTeamCount - 1 - seedIdx));
			playoffMatchupRepository.save(matchup);
		}
		log.info("Auto-seeded bracket for playoff {} ({} teams)", playoffId, seededTeamCount);
	}

	/**
	 * Pulls Top-N teams from the REGULAR phase standings when available.
	 * Returns {@code null} when no REGULAR phase exists or standings are insufficient — caller
	 * falls back to manual seeds. On a successful pull:
	 * <ul>
	 *   <li>Replaces any existing {@link PlayoffSeed} rows with the Top-N seeding (1..N).</li>
	 *   <li>Adds each seeded team as a {@link PhaseTeam} on the PLAYOFF phase if not already present.</li>
	 * </ul>
	 */
	private List<Team> tryLoadFromRegularStandings(Playoff playoff, int totalTeams) {
		UUID seasonId = playoff.getSeason().getId();
		var regularPhaseOpt = seasonPhaseService.findByType(seasonId, PhaseType.REGULAR);
		if (regularPhaseOpt.isEmpty()) {
			return null;
		}
		var regularPhase = regularPhaseOpt.get();
		// Combined-view (groupId=null) for GROUPS-layout; flat list for LEAGUE.
		var standings = standingsService.calculateStandings(regularPhase.getId(), null);
		if (standings.size() < totalTeams) {
			return null;
		}

		var topTeams = standings.stream()
				.map(StandingsService.TeamStanding::getTeam)
				.limit(totalTeams)
				.toList();

		// Persist Top-N as PlayoffSeed rows (replacing any prior seeds) so getSeedingData and
		// the PlayoffController seed UI stay coherent.
		playoffSeedRepository.deleteByPlayoffId(playoff.getId());
		entityManager.flush();
		int seedNumber = 1;
		for (Team t : topTeams) {
			playoffSeedRepository.save(new PlayoffSeed(playoff, t, seedNumber++));
		}

		// Side-effect: each seeded team becomes a PhaseTeam row on the PLAYOFF phase.
		if (playoff.getPhase() != null) {
			Set<UUID> existingTeamIds = phaseTeamRepository.findByPhaseId(playoff.getPhase().getId())
					.stream().map(pt -> pt.getTeam().getId()).collect(Collectors.toSet());
			for (Team t : topTeams) {
				if (!existingTeamIds.contains(t.getId())) {
					phaseTeamRepository.save(new PhaseTeam(playoff.getPhase(), t));
				}
			}
		}

		log.debug("Pulled Top-{} from REGULAR phase {} for playoff {}",
				totalTeams, regularPhase.getId(), playoff.getId());
		return topTeams;
	}

	private int[] buildBracketOrder(int matchCount) {
		return switch (matchCount) {
			case 1 -> new int[]{0};
			case 2 -> new int[]{0, 1};
			case 4 -> new int[]{0, 3, 2, 1};
			default -> {
				int[] order = new int[matchCount];
				for (int i = 0; i < matchCount; i++) order[i] = i;
				yield order;
			}
		};
	}

	private Team findTeam(UUID teamId) {
		return teamRepository.findById(teamId)
				.orElseThrow(() -> new EntityNotFoundException("Team", teamId));
	}

	public record SeedEntry(UUID matchupId, int slot, UUID teamId, Integer seedNumber) {
	}

	public record SeedingData(Playoff playoff, PlayoffBracketViewService.PlayoffBracketView bracketView,
	                          PlayoffRound firstRound, List<Team> teams,
	                          Set<UUID> seededTeamIds, Map<UUID, Integer> seedNumbers) {
	}
}
