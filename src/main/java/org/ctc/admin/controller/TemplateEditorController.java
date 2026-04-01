package org.ctc.admin.controller;

import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.admin.service.TemplatePreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final SettingsGraphicService settingsGraphicService;
    private final MatchdayOverviewGraphicService matchdayOverviewGraphicService;
    private final MatchdayScheduleGraphicService matchdayScheduleGraphicService;
    private final MatchdayResultsGraphicService matchdayResultsGraphicService;
    private final ResultsGraphicService resultsGraphicService;
    private final TemplatePreviewService templatePreviewService;

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
        try {
            model.addAttribute("settingsTemplate", settingsGraphicService.loadTemplate());
            model.addAttribute("settingsIsCustom", settingsGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("settingsTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load settings template: " + e.getMessage());
            }
        }
        try {
            model.addAttribute("raceResultsTemplate", resultsGraphicService.loadTemplate());
            model.addAttribute("raceResultsIsCustom", resultsGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("raceResultsTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load race results template: " + e.getMessage());
            }
        }
        try {
            model.addAttribute("matchdayOverviewTemplate", matchdayOverviewGraphicService.loadTemplate());
            model.addAttribute("matchdayOverviewIsCustom", matchdayOverviewGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("matchdayOverviewTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load matchday overview template: " + e.getMessage());
            }
        }
        try {
            model.addAttribute("matchdayScheduleTemplate", matchdayScheduleGraphicService.loadTemplate());
            model.addAttribute("matchdayScheduleIsCustom", matchdayScheduleGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("matchdayScheduleTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load matchday schedule template: " + e.getMessage());
            }
        }
        try {
            model.addAttribute("matchdayResultsTemplate", matchdayResultsGraphicService.loadTemplate());
            model.addAttribute("matchdayResultsIsCustom", matchdayResultsGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("matchdayResultsTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load matchday results template: " + e.getMessage());
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

    @PostMapping("/settings/save")
    public String saveSettingsTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            settingsGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Settings template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=settings";
    }

    @PostMapping("/settings/reset")
    public String resetSettingsTemplate(RedirectAttributes redirectAttributes) {
        try {
            settingsGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Settings template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=settings";
    }

    @PostMapping("/race-results/save")
    public String saveRaceResultsTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            resultsGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Race results template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=race-results";
    }

    @PostMapping("/race-results/reset")
    public String resetRaceResultsTemplate(RedirectAttributes redirectAttributes) {
        try {
            resultsGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Race results template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=race-results";
    }

    @PostMapping("/matchday-overview/save")
    public String saveMatchdayOverviewTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            matchdayOverviewGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Matchday overview template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-overview";
    }

    @PostMapping("/matchday-overview/reset")
    public String resetMatchdayOverviewTemplate(RedirectAttributes redirectAttributes) {
        try {
            matchdayOverviewGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Matchday overview template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-overview";
    }

    @PostMapping("/matchday-schedule/save")
    public String saveMatchdayScheduleTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            matchdayScheduleGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Matchday schedule template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-schedule";
    }

    @PostMapping("/matchday-schedule/reset")
    public String resetMatchdayScheduleTemplate(RedirectAttributes redirectAttributes) {
        try {
            matchdayScheduleGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Matchday schedule template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-schedule";
    }

    @PostMapping("/matchday-results/save")
    public String saveMatchdayResultsTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            matchdayResultsGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Matchday results template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-results";
    }

    @PostMapping("/matchday-results/reset")
    public String resetMatchdayResultsTemplate(RedirectAttributes redirectAttributes) {
        try {
            matchdayResultsGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Matchday results template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=matchday-results";
    }

    @PostMapping("/{templateType}/preview")
    @ResponseBody
    public ResponseEntity<String> preview(@PathVariable String templateType,
                                          @RequestParam String template) {
        try {
            String html = templatePreviewService.renderPreview(templateType, template);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown template type: " + templateType);
        } catch (Exception e) {
            log.error("Preview failed for template type: {}", templateType, e);
            return ResponseEntity.internalServerError().body("Preview failed: " + e.getMessage());
        }
    }
}
