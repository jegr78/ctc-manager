package org.ctc.domain.service;

import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceLineupService {

    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;

    // --- Return types ---

    public record LineupTeamEntry(Team team, List<SeasonDriver> drivers, List<Team> subTeams, boolean hasSubTeams) {}
    public record LineupData(Race race, LineupTeamEntry homeEntry, LineupTeamEntry awayEntry) {}

    // --- Get lineup data ---

    public LineupData getLineupData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var season = race.getMatchday().getSeason();
        var seasonTeams = season.getTeams();

        var raceTeams = Stream.of(race.getHomeTeam(), race.getAwayTeam())
                .filter(Objects::nonNull)
                .toList();

        var teamEntries = new ArrayList<LineupTeamEntry>();

        for (var team : raceTeams) {
            if (team.isSubTeam()) {
                var parent = team.getParentOrSelf();
                if (teamEntries.stream().anyMatch(e -> e.team().getId().equals(parent.getId()))) continue;

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

        LineupTeamEntry homeEntry = teamEntries.size() > 0 ? teamEntries.get(0) : null;
        LineupTeamEntry awayEntry = teamEntries.size() > 1 ? teamEntries.get(1) : null;

        return new LineupData(race, homeEntry, awayEntry);
    }

    // --- Get driver assignments ---

    public Map<UUID, UUID> getDriverAssignments(UUID raceId) {
        var existingLineups = raceLineupRepository.findByRaceId(raceId);
        var assignments = new HashMap<UUID, UUID>();
        for (var lineup : existingLineups) {
            assignments.put(lineup.getDriver().getId(), lineup.getTeam().getId());
        }
        return assignments;
    }

    // --- Save lineup ---

    @Transactional
    public int saveLineup(UUID raceId, Map<UUID, UUID> driverTeamAssignments) {
        var race = raceRepository.findById(raceId).orElseThrow();

        var existing = raceLineupRepository.findByRaceId(raceId);
        raceLineupRepository.deleteAll(existing);

        int count = 0;
        for (var entry : driverTeamAssignments.entrySet()) {
            var driver = driverRepository.findById(entry.getKey()).orElseThrow();
            var team = teamRepository.findById(entry.getValue()).orElseThrow();
            raceLineupRepository.save(new RaceLineup(race, driver, team));
            count++;
        }

        log.info("Saved {} lineup entries for race {}", count, raceId);
        return count;
    }
}
