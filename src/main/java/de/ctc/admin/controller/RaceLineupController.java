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
        var parentTeamsWithSubs = seasonTeams.stream()
                .filter(Team::isSubTeam)
                .map(Team::getParentOrSelf)
                .distinct()
                .sorted(Comparator.comparing(Team::getShortName))
                .toList();

        var parentDriverMap = new LinkedHashMap<Team, List<SeasonDriver>>();
        var parentSubTeamMap = new LinkedHashMap<Team, List<Team>>();
        var driverSubTeamMap = new HashMap<UUID, UUID>();

        for (var parent : parentTeamsWithSubs) {
            var drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), parent.getId());
            parentDriverMap.put(parent, drivers);

            var subTeams = seasonTeams.stream()
                    .filter(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()))
                    .sorted(Comparator.comparing(Team::getShortName))
                    .toList();
            parentSubTeamMap.put(parent, subTeams);
        }

        for (var lineup : existingLineups) {
            driverSubTeamMap.put(lineup.getDriver().getId(), lineup.getTeam().getId());
        }

        model.addAttribute("race", race);
        model.addAttribute("parentTeamsWithSubs", parentTeamsWithSubs);
        model.addAttribute("parentDriverMap", parentDriverMap);
        model.addAttribute("parentSubTeamMap", parentSubTeamMap);
        model.addAttribute("driverSubTeamMap", driverSubTeamMap);
        return "admin/race-lineup";
    }

    @Transactional
    @PostMapping("/{raceId}/lineup")
    public String saveLineup(@PathVariable UUID raceId,
                             @RequestParam Map<String, String> params,
                             RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(raceId).orElseThrow();

        var existing = raceLineupRepository.findByRaceId(raceId);
        raceLineupRepository.deleteAll(existing);

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
