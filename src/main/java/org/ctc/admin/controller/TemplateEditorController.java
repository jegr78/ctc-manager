package org.ctc.admin.controller;

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

    private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.of(
        "team-cards",         "teamCardService",
        "lineup",             "lineupGraphicService",
        "settings",           "settingsGraphicService",
        "race-results",       "resultsGraphicService",
        "match-results",      "matchResultsGraphicService",
        "matchday-overview",  "matchdayOverviewGraphicService",
        "matchday-schedule",  "matchdayScheduleGraphicService",
        "matchday-results",   "matchdayResultsGraphicService",
        "overlay",            "overlayGraphicService",
        "power-rankings",     "powerRankingsGraphicService"
    );

    private static final Map<String, String> TEMPLATE_TYPE_TO_ATTR = Map.of(
        "team-cards",         "teamCard",
        "lineup",             "lineup",
        "settings",           "settings",
        "race-results",       "raceResults",
        "match-results",      "matchResults",
        "matchday-overview",  "matchdayOverview",
        "matchday-schedule",  "matchdaySchedule",
        "matchday-results",   "matchdayResults",
        "overlay",            "overlay",
        "power-rankings",     "powerRankings"
    );

    private static final Map<String, String> TEMPLATE_TYPE_TO_LABEL = Map.of(
        "team-cards",         "Team card",
        "lineup",             "Lineup",
        "settings",           "Settings",
        "race-results",       "Race results",
        "match-results",      "Match results",
        "matchday-overview",  "Matchday overview",
        "matchday-schedule",  "Matchday schedule",
        "matchday-results",   "Matchday results",
        "overlay",            "Overlay",
        "power-rankings",     "Power rankings"
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
            log.warn("Blocked unsafe template save for type {}: {}", templateType, e.getMessage());
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
            log.warn("Blocked unsafe template preview: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Template contains unsafe expressions");
        } catch (RuntimeException e) {
            log.error("Preview failed for template type: {}", templateType, e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Preview failed");
        }
    }
}
