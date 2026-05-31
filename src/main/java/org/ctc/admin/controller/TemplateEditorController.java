package org.ctc.admin.controller;

import static java.util.Map.entry;
import static org.ctc.util.LogSanitizer.sanitize;

import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.TemplateManageable;
import org.ctc.admin.service.TemplatePreviewService;
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

    private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.ofEntries(
        entry("team-cards",         "teamCardService"),
        entry("lineup",             "lineupGraphicService"),
        entry("settings",           "settingsGraphicService"),
        entry("race-results",       "resultsGraphicService"),
        entry("match-results",      "matchResultsGraphicService"),
        entry("matchday-overview",  "matchdayOverviewGraphicService"),
        entry("matchday-schedule",  "matchdayScheduleGraphicService"),
        entry("matchday-results",   "matchdayResultsGraphicService"),
        entry("overlay",            "overlayGraphicService"),
        entry("power-rankings",     "powerRankingsGraphicService"),
        entry("lobby-settings",     "lobbySettingsGraphicService")
    );

    private static final Map<String, String> TEMPLATE_TYPE_TO_ATTR = Map.ofEntries(
        entry("team-cards",         "teamCard"),
        entry("lineup",             "lineup"),
        entry("settings",           "settings"),
        entry("race-results",       "raceResults"),
        entry("match-results",      "matchResults"),
        entry("matchday-overview",  "matchdayOverview"),
        entry("matchday-schedule",  "matchdaySchedule"),
        entry("matchday-results",   "matchdayResults"),
        entry("overlay",            "overlay"),
        entry("power-rankings",     "powerRankings"),
        entry("lobby-settings",     "lobbySettings")
    );

    private static final Map<String, String> TEMPLATE_TYPE_TO_LABEL = Map.ofEntries(
        entry("team-cards",         "Team card"),
        entry("lineup",             "Lineup"),
        entry("settings",           "Settings"),
        entry("race-results",       "Race results"),
        entry("match-results",      "Match results"),
        entry("matchday-overview",  "Matchday overview"),
        entry("matchday-schedule",  "Matchday schedule"),
        entry("matchday-results",   "Matchday results"),
        entry("overlay",            "Overlay"),
        entry("power-rankings",     "Power rankings"),
        entry("lobby-settings",     "Lobby settings")
    );

    private final Map<String, TemplateManageable> templateServices;
    private final TemplatePreviewService templatePreviewService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "team-cards") String tab, Model model) {
        for (var entry : TEMPLATE_TYPE_TO_BEAN.entrySet()) {
            String typeKey = entry.getKey();
            String beanName = entry.getValue();
            String attrPrefix = TEMPLATE_TYPE_TO_ATTR.get(typeKey);
            TemplateManageable svc = templateServices.get(beanName);
            try {
                model.addAttribute(attrPrefix + "Template", svc.loadTemplate());
                model.addAttribute(attrPrefix + "IsCustom", svc.hasCustomTemplate());
            } catch (IOException e) {
                model.addAttribute(attrPrefix + "Template", "");
                if (!model.containsAttribute("errorMessage")) {
                    model.addAttribute("errorMessage",
                        "Failed to load " + TEMPLATE_TYPE_TO_LABEL.get(typeKey).toLowerCase() + " template: " + e.getMessage());
                }
            }
        }
        model.addAttribute("activeTab", tab);
        return "admin/template-editors";
    }

    @PostMapping("/{templateType}/save")
    public String save(@PathVariable String templateType,
                       @RequestParam String template,
                       RedirectAttributes redirectAttributes) {
        String beanName = TEMPLATE_TYPE_TO_BEAN.get(templateType);
        if (beanName == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unknown template type");
            return "redirect:/admin/tools/template-editors";
        }
        try {
            templatePreviewService.validateTemplateContent(template);
            templateServices.get(beanName).saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage",
                TEMPLATE_TYPE_TO_LABEL.get(templateType) + " template saved");
        } catch (TemplatePreviewService.TemplateSecurityException e) {
            log.warn("Blocked unsafe template save for type {}: {}", sanitize(templateType), e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Template contains unsafe expressions");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=" + templateType;
    }

    @PostMapping("/{templateType}/reset")
    public String reset(@PathVariable String templateType,
                        RedirectAttributes redirectAttributes) {
        String beanName = TEMPLATE_TYPE_TO_BEAN.get(templateType);
        if (beanName == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unknown template type");
            return "redirect:/admin/tools/template-editors";
        }
        try {
            templateServices.get(beanName).resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage",
                TEMPLATE_TYPE_TO_LABEL.get(templateType) + " template reset to default");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=" + templateType;
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
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Unknown template type");
        } catch (TemplatePreviewService.TemplateSecurityException e) {
            log.warn("Blocked unsafe template preview: {}", sanitize(e.getMessage()));
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Template contains unsafe expressions");
        } catch (RuntimeException e) {
            log.error("Preview failed for template type: {}", sanitize(templateType), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Preview failed");
        }
    }
}
