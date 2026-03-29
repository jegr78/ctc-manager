package de.ctc.admin.controller;

import de.ctc.domain.model.RaceLineup;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceLineupController {

    private final RaceRepository raceRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;

    @GetMapping("/{raceId}/lineup")
    public String lineup(@PathVariable UUID raceId, Model model) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var season = race.getMatchday().getSeason();
        var existingLineups = raceLineupRepository.findByRaceId(raceId);
        var seasonTeams = season.getTeams();

        var raceTeams = java.util.stream.Stream.of(race.getHomeTeam(), race.getAwayTeam())
                .filter(Objects::nonNull)
                .toList();

        // For each race team: collect available drivers and sub-team options
        var teamEntries = new ArrayList<LineupTeamEntry>();
        var driverAssignments = new HashMap<UUID, UUID>();

        for (var lineup : existingLineups) {
            driverAssignments.put(lineup.getDriver().getId(), lineup.getTeam().getId());
        }

        for (var team : raceTeams) {
            if (team.isSubTeam()) {
                // Sub-team: show all drivers from all sub-teams of the parent with sub-team dropdown
                var parent = team.getParentOrSelf();
                if (teamEntries.stream().anyMatch(e -> e.team().getId().equals(parent.getId()))) continue;

                var subTeams = seasonTeams.stream()
                        .filter(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()))
                        .sorted(Comparator.comparing(Team::getShortName))
                        .toList();
                // Drivers are registered at sub-team level, collect from all sub-teams
                var drivers = subTeams.stream()
                        .flatMap(sub -> seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), sub.getId()).stream())
                        .toList();
                teamEntries.add(new LineupTeamEntry(parent, drivers, subTeams, true));
            } else {
                // Standalone team: show team's drivers with checkbox
                var drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), team.getId());
                teamEntries.add(new LineupTeamEntry(team, drivers, List.of(), false));
            }
        }

        model.addAttribute("race", race);
        model.addAttribute("teamEntries", teamEntries);
        model.addAttribute("driverAssignments", driverAssignments);
        return "admin/race-lineup";
    }

    public record LineupTeamEntry(Team team, List<SeasonDriver> drivers, List<Team> subTeams, boolean hasSubTeams) {}

    @Transactional
    @PostMapping("/{raceId}/lineup")
    public String saveLineup(@PathVariable UUID raceId,
                             @RequestParam Map<String, String> params,
                             RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(raceId).orElseThrow();

        var existing = raceLineupRepository.findByRaceId(raceId);
        raceLineupRepository.deleteAll(existing);

        // driver_{driverId} = teamId (from dropdown or checkbox; blank = not assigned)
        int count = 0;
        for (var entry : params.entrySet()) {
            if (!entry.getKey().startsWith("driver_") || entry.getValue().isBlank()) continue;

            UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
            UUID teamId = UUID.fromString(entry.getValue());

            var driver = driverRepository.findById(driverId).orElseThrow();
            var team = teamRepository.findById(teamId).orElseThrow();

            raceLineupRepository.save(new RaceLineup(race, driver, team));
            count++;
        }

        log.info("Saved {} lineup entries for race {}", count, raceId);
        redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
        return "redirect:/admin/races/" + raceId + "/lineup";
    }
}
