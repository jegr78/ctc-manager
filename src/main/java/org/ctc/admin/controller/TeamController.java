package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.TeamForm;
import org.ctc.discord.DiscordRoleCache;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.TeamManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/teams")
@RequiredArgsConstructor
public class TeamController {

	private final TeamManagementService teamManagementService;
	private final DiscordRoleCache discordRoleCache;

	@GetMapping
	public String list(Model model) {
		model.addAttribute("parentTeams", teamManagementService.findParentTeamsSorted());
		return "admin/teams";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable UUID id, Model model) {
		var data = teamManagementService.getTeamDetailData(id);
		model.addAttribute("team", data.team());
		model.addAttribute("seasons", data.seasons());
		model.addAttribute("seasonDriverGroups", data.seasonDriverGroups());
		model.addAttribute("seasonsWithoutDrivers", data.seasonsWithoutDrivers());
		model.addAttribute("pageTitle", "Team: " + data.team().getShortName());
		return "admin/team-detail";
	}

	@GetMapping("/new")
	public String create(Model model) {
		model.addAttribute("teamForm", new TeamForm());
		model.addAttribute("discordRoles", discordRoleCache.snapshot());
		return "admin/team-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		var team = teamManagementService.findById(id);
		var form = new TeamForm();
		form.setId(team.getId());
		form.setName(team.getName());
		form.setShortName(team.getShortName());
		form.setPrimaryColor(team.getPrimaryColor());
		form.setSecondaryColor(team.getSecondaryColor());
		form.setAccentColor(team.getAccentColor());
		form.setDiscordRoleId(team.getDiscordRoleId());
		model.addAttribute("teamForm", form);
		model.addAttribute("team", team);
		model.addAttribute("discordRoles", discordRoleCache.snapshot());
		return "admin/team-form";
	}

	@PostMapping("/save")
	public String save(@Valid @ModelAttribute("teamForm") TeamForm form, BindingResult result,
	                   Model model, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			model.addAttribute("discordRoles", discordRoleCache.snapshot());
			return "admin/team-form";
		}
		try {
			teamManagementService.save(form.getId(), form.getName(), form.getShortName(),
					form.getPrimaryColor(), form.getSecondaryColor(), form.getAccentColor(),
					form.getDiscordRoleId());
			redirectAttributes.addFlashAttribute("successMessage", "Team saved: " + form.getName());
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/teams";
	}

	@PostMapping("/{id}/logo")
	public String uploadLogo(@PathVariable UUID id, @RequestParam MultipartFile logo,
	                         RedirectAttributes redirectAttributes) {
		try {
			teamManagementService.uploadLogo(id, logo);
			redirectAttributes.addFlashAttribute("successMessage", "Logo updated");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
		teamManagementService.addSubTeam(id, subName, subShortName);
		redirectAttributes.addFlashAttribute("successMessage", "Sub-team added: " + subShortName);
		return "redirect:/admin/teams/" + id + "/edit";
	}

	@PostMapping("/{id}/remove-sub-team")
	public String removeSubTeam(@PathVariable UUID id,
	                            @RequestParam UUID subTeamId,
	                            RedirectAttributes redirectAttributes) {
		teamManagementService.removeSubTeam(subTeamId);
		redirectAttributes.addFlashAttribute("successMessage", "Sub-team removed");
		return "redirect:/admin/teams/" + id + "/edit";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			teamManagementService.delete(id);
			redirectAttributes.addFlashAttribute("successMessage", "Team deleted");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/teams";
	}
}
