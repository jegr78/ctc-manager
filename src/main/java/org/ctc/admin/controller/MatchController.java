package org.ctc.admin.controller;

import org.ctc.domain.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/new")
    public String create(@RequestParam UUID matchdayId, Model model) {
        var formData = matchService.getCreateFormData(matchdayId);
        model.addAttribute("matchday", formData.matchday());
        model.addAttribute("teams", formData.teams());
        return "admin/match-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam UUID matchdayId,
                       @RequestParam UUID homeTeamId,
                       @RequestParam(required = false) UUID awayTeamId,
                       @RequestParam(defaultValue = "false") boolean bye,
                       RedirectAttributes redirectAttributes) {
        try {
            var match = matchService.createMatch(matchdayId, homeTeamId, awayTeamId, bye);
            var homeShort = match.getHomeTeam().getShortName();
            var awayShort = match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Match created: " + homeShort + (bye ? " (Bye)" : " vs " + awayShort));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/matchdays/" + matchdayId;
    }

    @PostMapping("/{id}/add-leg")
    public String addLeg(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            var match = matchService.addLeg(id);
            redirectAttributes.addFlashAttribute("successMessage", "Leg added");
            return "redirect:/admin/matchdays/" + match.getMatchday().getId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/matchdays/" + matchService.getMatchdayId(id);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var matchdayId = matchService.deleteMatch(id);
        redirectAttributes.addFlashAttribute("successMessage", "Match deleted");
        return "redirect:/admin/matchdays/" + matchdayId;
    }
}
