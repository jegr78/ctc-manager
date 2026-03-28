package de.ctc.admin.controller;

import de.ctc.domain.model.Team;
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
        model.addAttribute("team", team);
        model.addAttribute("seasons", seasons);
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
