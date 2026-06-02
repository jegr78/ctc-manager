package org.ctc.domain.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceLineupService {

	private final RaceRepository raceRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final TeamRepository teamRepository;
	private final DriverRepository driverRepository;
	private final RaceResultRepository raceResultRepository;
	private final ScoringService scoringService;

	public LineupData getLineupData(UUID raceId) {
		var race = raceRepository.findById(raceId)
				.orElseThrow(() -> new EntityNotFoundException("Race", raceId));
		var season = race.getMatchday().getSeason();
		var seasonTeams = season.getTeams();

		var raceTeams = Stream.of(race.getHomeTeam(), race.getAwayTeam())
				.filter(Objects::nonNull)
				.toList();

		var teamEntries = new ArrayList<LineupTeamEntry>();

		for (var team : raceTeams) {
			if (team.isSubTeam()) {
				var parent = team.getParentOrSelf();
				if (teamEntries.stream().anyMatch(e -> e.team().getId().equals(parent.getId()))) {
					continue;
				}

				var subTeams = seasonTeams.stream()
						.filter(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()))
						.sorted(Comparator.comparing(Team::getShortName))
						.toList();
				var drivers = subTeams.stream()
						.flatMap(sub -> seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), sub.getId()).stream())
						.toList();
				teamEntries.add(new LineupTeamEntry(parent, drivers, subTeams, true));
			} else {
				var drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), team.getId());
				teamEntries.add(new LineupTeamEntry(team, drivers, List.of(), false));
			}
		}

		LineupTeamEntry homeEntry = teamEntries.isEmpty() ? null : teamEntries.get(0);
		LineupTeamEntry awayEntry = teamEntries.size() > 1 ? teamEntries.get(1) : null;

		return new LineupData(race, homeEntry, awayEntry);
	}

	public Map<UUID, UUID> getDriverAssignments(UUID raceId) {
		var existingLineups = raceLineupRepository.findByRaceId(raceId);
		var assignments = new HashMap<UUID, UUID>();
		for (var lineup : existingLineups) {
			if (!lineup.isGuest()) {
				assignments.put(lineup.getDriver().getId(), lineup.getTeam().getId());
			}
		}
		return assignments;
	}

	public List<RaceLineup> getGuestLineups(UUID raceId) {
		return raceLineupRepository.findByRaceId(raceId).stream()
				.filter(RaceLineup::isGuest)
				.toList();
	}

	@Transactional
	public int saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments) {
		return saveLineup(raceId, rosterAssignments, Map.of());
	}

	@Transactional
	public int saveLineup(UUID raceId, Map<UUID, UUID> rosterAssignments, Map<UUID, UUID> guestAssignments) {
		var race = raceRepository.findById(raceId)
				.orElseThrow(() -> new EntityNotFoundException("Race", raceId));

		var collisions = new HashSet<>(rosterAssignments.keySet());
		collisions.retainAll(guestAssignments.keySet());
		if (!collisions.isEmpty()) {
			throw new BusinessRuleException("Driver already assigned as roster driver");
		}

		if (!guestAssignments.isEmpty()) {
			var validTeamIds = validFieldingTeamIds(race);
			boolean invalidTeam = guestAssignments.values().stream().anyMatch(teamId -> !validTeamIds.contains(teamId));
			if (invalidTeam) {
				throw new BusinessRuleException("Guest team is not one of the race's participating teams");
			}
		}

		var existing = raceLineupRepository.findByRaceId(raceId);
		var priorGuestTeams = existing.stream()
				.filter(RaceLineup::isGuest)
				.collect(Collectors.toMap(lineup -> lineup.getDriver().getId(), lineup -> lineup.getTeam().getId()));
		var droppedGuestDriverIds = priorGuestTeams.keySet().stream()
				.filter(driverId -> !guestAssignments.containsKey(driverId))
				.toList();
		boolean keptGuestTeamChanged = guestAssignments.entrySet().stream()
				.anyMatch(entry -> priorGuestTeams.containsKey(entry.getKey())
						&& !entry.getValue().equals(priorGuestTeams.get(entry.getKey())));

		raceLineupRepository.deleteAll(existing);
		raceLineupRepository.flush();

		int count = 0;
		for (var entry : rosterAssignments.entrySet()) {
			var driver = driverRepository.findById(entry.getKey())
					.orElseThrow(() -> new EntityNotFoundException("Driver", entry.getKey()));
			var team = teamRepository.findById(entry.getValue())
					.orElseThrow(() -> new EntityNotFoundException("Team", entry.getValue()));
			raceLineupRepository.save(new RaceLineup(race, driver, team));
			count++;
		}
		for (var entry : guestAssignments.entrySet()) {
			var driver = driverRepository.findById(entry.getKey())
					.orElseThrow(() -> new EntityNotFoundException("Driver", entry.getKey()));
			var team = teamRepository.findById(entry.getValue())
					.orElseThrow(() -> new EntityNotFoundException("Team", entry.getValue()));
			raceLineupRepository.save(new RaceLineup(race, driver, team, true));
			count++;
		}

		for (var driverId : droppedGuestDriverIds) {
			raceResultRepository.findByRaceIdAndDriverId(raceId, driverId)
					.ifPresent(raceResultRepository::delete);
		}
		if (!droppedGuestDriverIds.isEmpty() || keptGuestTeamChanged) {
			scoringService.aggregateMatchScores(race);
		}

		log.info("Saved {} lineup entries for race {}", count, raceId);
		return count;
	}

	private Set<UUID> validFieldingTeamIds(Race race) {
		var seasonTeams = race.getMatchday().getSeason().getTeams();
		var validIds = new HashSet<UUID>();
		Stream.of(race.getHomeTeam(), race.getAwayTeam())
				.filter(Objects::nonNull)
				.forEach(team -> {
					var parentId = team.getParentOrSelf().getId();
					validIds.add(team.getId());
					validIds.add(parentId);
					seasonTeams.stream()
							.filter(seasonTeam -> seasonTeam.getParentOrSelf().getId().equals(parentId))
							.forEach(seasonTeam -> validIds.add(seasonTeam.getId()));
				});
		return validIds;
	}

	public record LineupTeamEntry(Team team, List<SeasonDriver> drivers, List<Team> subTeams, boolean hasSubTeams) {
	}

	public record LineupData(Race race, LineupTeamEntry homeEntry, LineupTeamEntry awayEntry) {
	}
}
