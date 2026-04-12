package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
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

		// Collect all teams from linked seasons and the main season
		Map<UUID, Team> teamMap = new LinkedHashMap<>();
		for (Season season : playoff.getSeasons()) {
			for (Team team : season.getTeams()) {
				teamMap.putIfAbsent(team.getId(), team);
			}
		}
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

	@Transactional
	public void autoSeedBracket(UUID playoffId) {
		var seeds = playoffSeedRepository.findByPlayoffId(playoffId);
		if (seeds.isEmpty()) {
			throw new IllegalStateException("No seed numbers assigned yet");
		}

		var playoff = playoffRepository.findById(playoffId)
				.orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));
		var firstRound = playoff.getRounds().stream()
				.filter(r -> r.getRoundIndex() == 0)
				.findFirst().orElseThrow(() -> new EntityNotFoundException("PlayoffRound", "index=0"));

		var sortedSeeds = seeds.stream()
				.sorted(Comparator.comparingInt(PlayoffSeed::getSeed))
				.toList();

		int totalTeams = sortedSeeds.size();
		var matchups = firstRound.getMatchups().stream()
				.sorted(Comparator.comparingInt(PlayoffMatchup::getBracketPosition))
				.toList();

		int[] matchupOrder = buildBracketOrder(totalTeams / 2);

		for (int i = 0; i < matchups.size() && i < matchupOrder.length; i++) {
			int seedIdx = matchupOrder[i];
			var matchup = matchups.get(i);
			matchup.setTeam1(sortedSeeds.get(seedIdx).getTeam());
			matchup.setTeam2(sortedSeeds.get(totalTeams - 1 - seedIdx).getTeam());
			playoffMatchupRepository.save(matchup);
		}
		log.info("Auto-seeded bracket for playoff {}", playoffId);
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

	// --- Record types ---

	public record SeedEntry(UUID matchupId, int slot, UUID teamId, Integer seedNumber) {
	}

	public record SeedingData(Playoff playoff, PlayoffBracketViewService.PlayoffBracketView bracketView,
	                          PlayoffRound firstRound, List<Team> teams,
	                          Set<UUID> seededTeamIds, Map<UUID, Integer> seedNumbers) {
	}
}
