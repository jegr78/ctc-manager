package org.ctc.admin.controller;

import org.ctc.admin.dto.TeamForm;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.TeamManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final FileStorageService fileStorageService;
    private final TeamManagementService teamManagementService;

    @GetMapping
    public String list(Model model) {
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
        var data = teamManagementService.getTeamDetailData(id);
        model.addAttribute("team", data.team());
        model.addAttribute("seasons", data.seasons());
        model.addAttribute("seasonDriverGroups", data.seasonDriverGroups());
        model.addAttribute("seasonsWithoutDrivers", data.seasonsWithoutDrivers());
        return "admin/team-detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("teamForm", new TeamForm());
        return "admin/team-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var team = teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team", id));
        var form = new TeamForm();
        form.setId(team.getId());
        form.setName(team.getName());
        form.setShortName(team.getShortName());
        form.setPrimaryColor(team.getPrimaryColor());
        form.setSecondaryColor(team.getSecondaryColor());
        form.setAccentColor(team.getAccentColor());
        model.addAttribute("teamForm", form);
        model.addAttribute("team", team);
        return "admin/team-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("teamForm") TeamForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/team-form";
        }
        if (form.getId() != null) {
            var existing = teamRepository.findById(form.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Team", form.getId()));
            existing.setName(form.getName());
            existing.setShortName(form.getShortName());
            existing.setPrimaryColor(form.getPrimaryColor());
            existing.setSecondaryColor(form.getSecondaryColor());
            existing.setAccentColor(form.getAccentColor());
            teamRepository.save(existing);
            teamManagementService.propagateColorsToSubTeams(existing);
        } else {
            var team = new Team(form.getName(), form.getShortName());
            teamRepository.save(team);
        }
        log.info("Saved team: {}", form.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Team saved: " + form.getName());
        return "redirect:/admin/teams";
    }

    @PostMapping("/{id}/logo")
    public String uploadLogo(@PathVariable UUID id, @RequestParam MultipartFile logo,
                             RedirectAttributes redirectAttributes) {
        try {
            var team = teamRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Team", id));
            if (team.getLogoUrl() != null) {
                fileStorageService.delete(team.getLogoUrl());
            }
            String url = fileStorageService.storeImage("teams", id, logo);
            team.setLogoUrl(url);
            teamRepository.save(team);
            teamManagementService.propagateLogoToSubTeams(team, url);
            redirectAttributes.addFlashAttribute("successMessage", "Logo updated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
        }
        return "redirect:/admin/teams/" + id + "/edit";
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
        var parent = teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team", id));
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
        var subTeam = teamRepository.findById(subTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", subTeamId));
        teamRepository.delete(subTeam);
        log.info("Removed sub-team {}", subTeam.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Sub-team removed: " + subTeam.getShortName());
        return "redirect:/admin/teams/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var team = teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team", id));
        teamRepository.delete(team);
        log.info("Deleted team: {}", team.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Team deleted: " + team.getName());
        return "redirect:/admin/teams";
    }
}
