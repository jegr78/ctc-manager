package de.ctc.admin.controller;

import de.ctc.domain.model.MatchdayLineup;
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
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayLineupController {

    private final MatchdayRepository matchdayRepository;
    private final MatchdayLineupRepository matchdayLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;

    @GetMapping("/{matchdayId}/lineup")
    public String lineup(@PathVariable UUID matchdayId, Model model) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        var season = matchday.getSeason();
        var existingLineups = matchdayLineupRepository.findByMatchdayId(matchdayId);

        // Find parent teams that have sub-teams in this season
        var seasonTeams = season.getTeams();
        var parentTeamsWithSubs = seasonTeams.stream()
                .filter(Team::isSubTeam)
                .map(Team::getParentOrSelf)
                .distinct()
                .sorted(Comparator.comparing(Team::getShortName))
                .toList();

        // For each parent team, get drivers (from SeasonDriver)
        // and sub-teams available in this season
        var parentDriverMap = new LinkedHashMap<Team, List<SeasonDriver>>();
        var parentSubTeamMap = new LinkedHashMap<Team, List<Team>>();
        var driverLineupMap = new HashMap<UUID, MatchdayLineup>();

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
            driverLineupMap.put(lineup.getDriver().getId(), lineup);
        }

        model.addAttribute("matchday", matchday);
        model.addAttribute("parentTeamsWithSubs", parentTeamsWithSubs);
        model.addAttribute("parentDriverMap", parentDriverMap);
        model.addAttribute("parentSubTeamMap", parentSubTeamMap);
        model.addAttribute("driverLineupMap", driverLineupMap);
        return "admin/matchday-lineup";
    }

    @Transactional
    @PostMapping("/{matchdayId}/lineup")
    public String saveLineup(@PathVariable UUID matchdayId,
                             @RequestParam Map<String, String> params,
                             RedirectAttributes redirectAttributes) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();

        // Delete existing lineups for this matchday
        var existing = matchdayLineupRepository.findByMatchdayId(matchdayId);
        matchdayLineupRepository.deleteAll(existing);

        // Process form params: driver_{driverId} = teamId
        int count = 0;
        for (var entry : params.entrySet()) {
            if (!entry.getKey().startsWith("driver_") || entry.getValue().isBlank()) continue;

            UUID driverId = UUID.fromString(entry.getKey().substring("driver_".length()));
            UUID teamId = UUID.fromString(entry.getValue());

            var driver = driverRepository.findById(driverId).orElseThrow();
            var team = teamRepository.findById(teamId).orElseThrow();

            matchdayLineupRepository.save(new MatchdayLineup(matchday, driver, team));
            count++;
        }

        log.info("Saved {} lineup entries for matchday {}", count, matchday.getLabel());
        redirectAttributes.addFlashAttribute("successMessage", "Lineup saved: " + count + " drivers assigned");
        return "redirect:/admin/matchdays/" + matchdayId + "/lineup";
    }
}
