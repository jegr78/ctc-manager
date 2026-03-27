package de.ctc.admin.controller;

import de.ctc.domain.model.Season;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/seasons";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("season", new Season());
        return "admin/season-form";
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var season = seasonRepository.findById(id).orElseThrow();
        // Eagerly initialize teams for Thymeleaf (session closes before rendering)
        season.getTeams().size();
        model.addAttribute("season", season);
        model.addAttribute("allTeams", teamRepository.findAll());
        return "admin/season-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Season season, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/season-form";
        }
        seasonRepository.save(season);
        log.info("Saved season: {}", season.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Saison gespeichert: " + season.getName());
        return "redirect:/admin/seasons";
    }

    @PostMapping("/{id}/add-team")
    public String addTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                          RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();
        if (!season.getTeams().contains(team)) {
            season.getTeams().add(team);
            seasonRepository.save(season);
            log.info("Added team {} to season {}", team.getShortName(), season.getName());
        }
        redirectAttributes.addFlashAttribute("successMessage", "Team hinzugefügt: " + team.getShortName());
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/remove-team")
    public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                             RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        season.getTeams().removeIf(t -> t.getId().equals(teamId));
        seasonRepository.save(season);
        log.info("Removed team {} from season {}", teamId, season.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Team entfernt");
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        seasonRepository.delete(season);
        log.info("Deleted season: {}", season.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Saison gelöscht: " + season.getName());
        return "redirect:/admin/seasons";
    }
}
