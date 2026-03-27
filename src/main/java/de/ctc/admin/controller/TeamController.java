package de.ctc.admin.controller;

import de.ctc.domain.model.Team;
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

    @GetMapping
    public String list(Model model) {
        model.addAttribute("teams", teamRepository.findAll());
        return "admin/teams";
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
        teamRepository.save(team);
        log.info("Saved team: {}", team.getShortName());
        redirectAttributes.addFlashAttribute("successMessage", "Team saved: " + team.getName());
        return "redirect:/admin/teams";
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
