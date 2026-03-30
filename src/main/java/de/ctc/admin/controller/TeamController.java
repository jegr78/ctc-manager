package de.ctc.admin.controller;

import de.ctc.admin.dto.SeasonDriverGroupDto;
import de.ctc.domain.model.Driver;
import de.ctc.domain.model.RaceLineup;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.RaceLineupRepository;
import de.ctc.domain.repository.SeasonDriverRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    @GetMapping
    public String list(Model model) {
        // Show parent teams first, sub-teams grouped under parents
        var allTeams = teamRepository.findAll();
        var parentTeams = allTeams.stream()
                .filter(t -> t.getParentTeam() == null)
                .sorted((a, b) -> a.getShortName().compareToIgnoreCase(b.getShortName()))
                .toList();
        model.addAttribute("parentTeams", parentTeams);
        return "admin/teams";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var team = teamRepository.findById(id).orElseThrow();
        var seasons = seasonRepository.findByTeamsId(id);

        // Alle relevanten Team-IDs (dieses Team + Sub-Teams)
        var teamIds = new java.util.ArrayList<UUID>();
        teamIds.add(id);
        team.getSubTeams().forEach(sub -> teamIds.add(sub.getId()));

        // RaceLineups als Datenquelle: nur Fahrer die tatsaechlich gefahren sind
        var allLineups = raceLineupRepository.findByTeamIdIn(teamIds);

        // Gruppieren: Season → Team → eindeutige Drivers
        var seasonDriverGroups = allLineups.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        lu -> lu.getRace().getMatchday().getSeason(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.groupingBy(
                                RaceLineup::getTeam,
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.mapping(
                                        RaceLineup::getDriver,
                                        java.util.stream.Collectors.toCollection(
                                                () -> new java.util.TreeSet<>(
                                                        java.util.Comparator.comparing(Driver::getPsnId)))
                                )
                        )
                ))
                .entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().isActive() != b.getKey().isActive()) {
                        return a.getKey().isActive() ? -1 : 1;
                    }
                    return b.getKey().getName().compareTo(a.getKey().getName());
                })
                .map(entry -> {
                    var sortedByTeam = new java.util.LinkedHashMap<Team, java.util.List<Driver>>();
                    entry.getValue().entrySet().stream()
                            .sorted(java.util.Comparator.comparing(e -> e.getKey().getShortName()))
                            .forEach(e -> sortedByTeam.put(e.getKey(), new java.util.ArrayList<>(e.getValue())));
                    return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
                })
                .toList();

        // Seasons ohne Lineups: Fallback auf SeasonDriver-Zuordnungen
        var groupedSeasonIds = seasonDriverGroups.stream()
                .map(g -> g.season().getId())
                .collect(java.util.stream.Collectors.toSet());

        var allSeasonDrivers = seasonDriverRepository.findByTeamIdIn(teamIds);
        var fallbackGroups = allSeasonDrivers.stream()
                .filter(sd -> !groupedSeasonIds.contains(sd.getSeason().getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        SeasonDriver::getSeason,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.groupingBy(
                                SeasonDriver::getTeam,
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.mapping(
                                        SeasonDriver::getDriver,
                                        java.util.stream.Collectors.toCollection(
                                                () -> new java.util.TreeSet<>(
                                                        java.util.Comparator.comparing(Driver::getPsnId)))
                                )
                        )
                ))
                .entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().isActive() != b.getKey().isActive()) {
                        return a.getKey().isActive() ? -1 : 1;
                    }
                    return b.getKey().getName().compareTo(a.getKey().getName());
                })
                .map(entry -> {
                    var sortedByTeam = new java.util.LinkedHashMap<Team, java.util.List<Driver>>();
                    entry.getValue().entrySet().stream()
                            .sorted(java.util.Comparator.comparing(e -> e.getKey().getShortName()))
                            .forEach(e -> sortedByTeam.put(e.getKey(), new java.util.ArrayList<>(e.getValue())));
                    return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
                })
                .toList();

        // Zusammenfuehren: Lineup-Gruppen zuerst, dann Fallback-Gruppen
        var allGroups = new java.util.ArrayList<>(seasonDriverGroups);
        allGroups.addAll(fallbackGroups);
        allGroups.sort((a, b) -> {
            if (a.season().isActive() != b.season().isActive()) {
                return a.season().isActive() ? -1 : 1;
            }
            return b.season().getName().compareTo(a.season().getName());
        });

        var allGroupedSeasonIds = allGroups.stream()
                .map(g -> g.season().getId())
                .collect(java.util.stream.Collectors.toSet());
        var seasonsWithoutDrivers = seasons.stream()
                .filter(s -> !allGroupedSeasonIds.contains(s.getId()))
                .toList();

        model.addAttribute("team", team);
        model.addAttribute("seasons", seasons);
        model.addAttribute("seasonDriverGroups", allGroups);
        model.addAttribute("seasonsWithoutDrivers", seasonsWithoutDrivers);
        return "admin/team-detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("team", new Team());
        return "admin/team-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("team", teamRepository.findById(id).orElseThrow());
        return "admin/team-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Team team, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/team-form";
        }
        if (team.getId() != null) {
            var existing = teamRepository.findById(team.getId()).orElseThrow();
            existing.setName(team.getName());
            existing.setShortName(team.getShortName());
            existing.setLogoUrl(team.getLogoUrl());
            teamRepository.save(existing);
        } else {
            teamRepository.save(team);
        }
        log.info("Saved team: {}", team.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Team saved: " + team.getName());
        return "redirect:/admin/teams";
    }

    @PostMapping("/{id}/add-sub-team")
    public String addSubTeam(@PathVariable UUID id,
                             @RequestParam String subName,
                             @RequestParam String subShortName,
                             RedirectAttributes redirectAttributes) {
        if (subName.isBlank() || subShortName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Name and short name must not be blank");
            return "redirect:/admin/teams/" + id + "/edit";
        }
        var parent = teamRepository.findById(id).orElseThrow();
        var subTeam = new Team(subName, subShortName, parent);
        teamRepository.save(subTeam);
        log.info("Added sub-team {} to {}", subShortName, parent.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Sub-team added: " + subShortName);
        return "redirect:/admin/teams/" + id + "/edit";
    }

    @PostMapping("/{id}/remove-sub-team")
    public String removeSubTeam(@PathVariable UUID id,
                                @RequestParam UUID subTeamId,
                                RedirectAttributes redirectAttributes) {
        var subTeam = teamRepository.findById(subTeamId).orElseThrow();
        teamRepository.delete(subTeam);
        log.info("Removed sub-team {}", subTeam.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Sub-team removed: " + subTeam.getShortName());
        return "redirect:/admin/teams/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var team = teamRepository.findById(id).orElseThrow();
        teamRepository.delete(team);
        log.info("Deleted team: {}", team.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Team deleted: " + team.getName());
        return "redirect:/admin/teams";
    }
}
