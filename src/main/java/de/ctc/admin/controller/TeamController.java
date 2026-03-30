package de.ctc.admin.controller;

import de.ctc.admin.dto.SeasonDriverGroupDto;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.model.Team;
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

        // Alle SeasonDrivers fuer diese Teams laden und gruppieren
        var allSeasonDrivers = seasonDriverRepository.findByTeamIdIn(teamIds);

        var seasonDriverGroups = allSeasonDrivers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SeasonDriver::getSeason,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.groupingBy(
                                SeasonDriver::getTeam,
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.toList()
                        )
                ))
                .entrySet().stream()
                .sorted((a, b) -> {
                    // Active season first, then by name descending
                    if (a.getKey().isActive() != b.getKey().isActive()) {
                        return a.getKey().isActive() ? -1 : 1;
                    }
                    return b.getKey().getName().compareTo(a.getKey().getName());
                })
                .map(entry -> {
                    var sortedByTeam = new java.util.LinkedHashMap<Team, java.util.List<SeasonDriver>>();
                    entry.getValue().entrySet().stream()
                            .sorted(java.util.Comparator.comparing(e -> e.getKey().getShortName()))
                            .forEach(e -> {
                                var sortedDrivers = e.getValue().stream()
                                        .sorted(java.util.Comparator.comparing(sd -> sd.getDriver().getPsnId()))
                                        .toList();
                                sortedByTeam.put(e.getKey(), sortedDrivers);
                            });
                    return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
                })
                .toList();

        model.addAttribute("team", team);
        model.addAttribute("seasons", seasons);
        model.addAttribute("seasonDriverGroups", seasonDriverGroups);
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
