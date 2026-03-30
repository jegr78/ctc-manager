package de.ctc.admin.controller;

import de.ctc.admin.service.LineupGraphicService;
import de.ctc.admin.service.TeamCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/tools/template-editors")
@RequiredArgsConstructor
public class TemplateEditorController {

    private final TeamCardService teamCardService;
    private final LineupGraphicService lineupGraphicService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "team-cards") String tab, Model model) {
        try {
            model.addAttribute("teamCardTemplate", teamCardService.loadTemplate());
            model.addAttribute("teamCardIsCustom", teamCardService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("teamCardTemplate", "");
            model.addAttribute("errorMessage", "Failed to load team card template: " + e.getMessage());
        }
        try {
            model.addAttribute("lineupTemplate", lineupGraphicService.loadTemplate());
            model.addAttribute("lineupIsCustom", lineupGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("lineupTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load lineup template: " + e.getMessage());
            }
        }
        model.addAttribute("activeTab", tab);
        return "admin/template-editors";
    }

    @PostMapping("/team-cards/save")
    public String saveTeamCardTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            teamCardService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Team card template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=team-cards";
    }

    @PostMapping("/team-cards/reset")
    public String resetTeamCardTemplate(RedirectAttributes redirectAttributes) {
        try {
            teamCardService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Team card template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=team-cards";
    }

    @PostMapping("/lineup/save")
    public String saveLineupTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            lineupGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Lineup template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=lineup";
    }

    @PostMapping("/lineup/reset")
    public String resetLineupTemplate(RedirectAttributes redirectAttributes) {
        try {
            lineupGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Lineup template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=lineup";
    }
}
