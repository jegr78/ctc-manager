package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final RaceRepository raceRepository;
    private final PlayoffCarSelectionService carSelections;

	@Transactional
	public void seedTeam(UUID matchupId, UUID teamId, int slot) {
		PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
				.orElseThrow(() -> new EntityNotFoundException("PlayoffMatchup", matchupId));
        var playoff = matchup.getRound().getPlayoff();
        carSelections.lock(playoff);
        requireSeedingOpen(playoff.getId());
        if (slot != 1 && slot != 2) throw new IllegalArgumentException("Slot must be 1 or 2, got: " + slot);
        var team = teamId == null ? null : findTeam(teamId);
        var change = new PlayoffCarSelectionService.Participants(matchup,
                slot == 1 ? team : matchup.getTeam1(), slot == 2 ? team : matchup.getTeam2());
        carSelections.requireValid(playoff, List.of(change));
        matchup.setTeam1(change.team1());
        matchup.setTeam2(change.team2());
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
					if (m.getTeam1() != null) {
						ids.add(m.getTeam1().getId());
					}
					if (m.getTeam2() != null) {
						ids.add(m.getTeam2().getId());
					}
					return ids.stream();
				})
				.collect(Collectors.toSet());

		Map<UUID, Integer> seedNumbers = playoffSeedRepository.findByPlayoffId(playoffId).stream()
				.collect(Collectors.toMap(s -> s.getTeam().getId(), PlayoffSeed::getSeed));

		return new SeedingData(playoff, bracket, firstRound, teams, seededTeamIds, seedNumbers,
				isSeedingFrozen(playoffId));
	}

	/**
	 * Saves the seeding form: every submitted slot takes its team, and an empty slot is cleared.
	 * The seed numbers are replaced by the submitted ones, so an all-empty form removes them.
	 */
	@Transactional
	public void saveSeed(UUID playoffId, List<SeedEntry> seeds) {
        var playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
        carSelections.lock(playoff);
        requireSeedingOpen(playoffId);
        var planned = new LinkedHashMap<UUID, PlayoffCarSelectionService.Participants>();
        for (var entry : seeds) {
            var matchup = entry.matchupId() == null ? null : playoffMatchupRepository.findById(entry.matchupId()).orElse(null);
            if (matchup == null || !matchup.getRound().getPlayoff().getId().equals(playoffId)
                    || matchup.getRound().getRoundIndex() != 0) {
                throw new IllegalArgumentException("The seeding slot does not belong to this playoff");
            }
            if (entry.slot() != 1 && entry.slot() != 2) throw new IllegalArgumentException("Slot must be 1 or 2, got: " + entry.slot());
            var previous = planned.getOrDefault(matchup.getId(),
                    new PlayoffCarSelectionService.Participants(matchup, matchup.getTeam1(), matchup.getTeam2()));
            var team = entry.teamId() == null ? null : findTeam(entry.teamId());
            planned.put(matchup.getId(), new PlayoffCarSelectionService.Participants(matchup,
                    entry.slot() == 1 ? team : previous.team1(), entry.slot() == 2 ? team : previous.team2()));
        }
        carSelections.requireValid(playoff, List.copyOf(planned.values()));
        for (var change : planned.values()) {
            change.matchup().setTeam1(change.team1());
            change.matchup().setTeam2(change.team2());
            playoffMatchupRepository.save(change.matchup());
        }

		Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
		for (var entry : seeds) {
			if (entry.teamId() != null && entry.seedNumber() != null) {
				teamSeeds.put(entry.teamId(), entry.seedNumber());
			}
		}
		saveSeedNumbers(playoffId, teamSeeds);

		log.info("Seeding saved for playoff {}", playoffId);
	}

	@Transactional
	public void saveSeedNumbers(UUID playoffId, Map<UUID, Integer> teamSeeds) {
		requireSeedingOpen(playoffId);
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
        playoffRepository.findById(playoffId).ifPresent(carSelections::lock);
		requireSeedingOpen(playoffId);
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
		if (seededTeamCount < matchups.size() * 2) {
			throw new IllegalStateException("Auto-seeding needs seed numbers for all %d teams, found %d"
					.formatted(matchups.size() * 2, seededTeamCount));
		}
        var planned = new ArrayList<PlayoffCarSelectionService.Participants>();
        for (int i = 0; i < matchups.size() && i < matchupOrder.length; i++) {
            int seedIdx = matchupOrder[i];
            planned.add(new PlayoffCarSelectionService.Participants(matchups.get(i), sortedTeams.get(seedIdx),
                    sortedTeams.get(seededTeamCount - 1 - seedIdx)));
        }
        carSelections.requireValid(playoff, planned);
        if (seeds.isEmpty()) persistDerivedSeeds(playoff, sortedTeams);
        for (var change : planned) {
            change.matchup().setTeam1(change.team1());
            change.matchup().setTeam2(change.team2());
            playoffMatchupRepository.save(change.matchup());
        }
		log.info("Auto-seeded bracket for playoff {} ({} teams)", playoffId, seededTeamCount);
	}

    /** Reads Top-N regular standings without changing seeds, rosters or participants. */
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

        return topTeams;
    }

    private void persistDerivedSeeds(Playoff playoff, List<Team> topTeams) {
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
    }

	private int[] buildBracketOrder(int matchCount) {
		return switch (matchCount) {
			case 1 -> new int[]{0};
			case 2 -> new int[]{0, 1};
			case 4 -> new int[]{0, 3, 2, 1};
			default -> {
				int[] order = new int[matchCount];
				for (int i = 0; i < matchCount; i++) {
					order[i] = i;
				}
				yield order;
			}
		};
	}

	private Team findTeam(UUID teamId) {
		return teamRepository.findById(teamId)
				.orElseThrow(() -> new EntityNotFoundException("Team", teamId));
	}

	private void requireSeedingOpen(UUID playoffId) {
		if (isSeedingFrozen(playoffId)) {
			throw new IllegalStateException("Seeding is frozen once a playoff matchup has results or a winner");
		}
	}

	private boolean isSeedingFrozen(UUID playoffId) {
		return playoffMatchupRepository.findByRoundPlayoffId(playoffId).stream()
				.anyMatch(matchup -> matchup.isComplete() || raceRepository.findByPlayoffMatchupId(matchup.getId()).stream()
						.anyMatch(race -> !race.getResults().isEmpty()));
	}

	public record SeedEntry(UUID matchupId, int slot, UUID teamId, Integer seedNumber) {
	}

	public record SeedingData(Playoff playoff, PlayoffBracketViewService.PlayoffBracketView bracketView,
	                          PlayoffRound firstRound, List<Team> teams,
	                          Set<UUID> seededTeamIds, Map<UUID, Integer> seedNumbers, boolean seedingFrozen) {
	}
}
