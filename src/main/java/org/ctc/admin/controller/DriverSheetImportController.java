package org.ctc.admin.controller;

import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.DriverSheetImportService;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.dataimport.GoogleSheetsService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.service.SeasonManagementService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/drivers/import")
@RequiredArgsConstructor
public class DriverSheetImportController {

    private final DriverSheetImportService driverSheetImportService;
    private final GoogleSheetsService googleSheetsService;
    private final SeasonManagementService seasonManagementService;

    @GetMapping
    public String showForm(Model model) {
        addCommonAttributes(model);
        return "admin/driver-import";
    }

    @PostMapping("/preview")
    public String preview(@RequestParam String sheetUrl, Model model) {
        if (sheetUrl == null || sheetUrl.isBlank()) {
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Sheet URL must not be blank");
            return "admin/driver-import";
        }
        try {
            var preview = driverSheetImportService.preview(sheetUrl);
            model.addAttribute("preview", preview);
            model.addAttribute("sheetUrl", sheetUrl);
            model.addAttribute("hasAmbiguousTabs", preview.tabPreviews().stream()
                    .anyMatch(t -> t.suggestedSeasonId() == null));
            model.addAttribute("showGroupColumn", preview.tabPreviews().stream()
                    .anyMatch(TabPreview::usesGroups));
            addCommonAttributes(model);
            return "admin/driver-import-preview";
        } catch (IOException e) {
            log.error("Error reading Google Sheet for driver import", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Could not read the Google Sheet. Check the URL and service account credentials.");
            return "admin/driver-import";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Driver import preview failed", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Preview failed: " + e.getMessage());
            return "admin/driver-import";
        }
    }

    @PostMapping("/execute")
    public String execute(@RequestParam String sheetUrl,
                          @RequestParam(required = false) Map<String, String> allParams,
                          RedirectAttributes redirectAttributes) {
        if (sheetUrl == null || sheetUrl.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Sheet URL must not be blank");
            return "redirect:/admin/drivers/import";
        }
        Map<String, String> safeParams = allParams != null ? allParams : Map.of();
        long seasonKeys = safeParams.keySet().stream().filter(k -> k.startsWith("seasonId_")).count();
        long acceptKeys = safeParams.keySet().stream().filter(k -> k.startsWith("accept_")).count();
        long skipKeys = safeParams.keySet().stream().filter(k -> k.startsWith("skip_")).count();
        log.info("Driver sheet execute: sheetUrl={}, {} seasonId keys, {} accept keys, {} skip keys",
                sheetUrl, seasonKeys, acceptKeys, skipKeys);
        if (seasonKeys == 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tabs were assigned a season. Nothing imported.");
            return "redirect:/admin/drivers/import";
        }
        try {
            var result = driverSheetImportService.execute(sheetUrl, allParams);
            var msg = new StringBuilder("Import successful: ")
                    .append(result.getNewDriversCount()).append(" new drivers, ")
                    .append(result.getNewAssignmentsCount()).append(" new assignments, ")
                    .append(result.getConflictsOverwrittenCount()).append(" conflicts overwritten, ")
                    .append(result.getConflictsSkippedCount()).append(" conflicts skipped, ")
                    .append(result.getUnchangedCount()).append(" unchanged, ")
                    .append(result.getErrorCount()).append(" errors.");
            if (result.hasSkippedTabs()) {
                msg.append(" Skipped tabs: ").append(result.getSkippedTabNames())
                   .append(" (no season selected).");
            }
            redirectAttributes.addFlashAttribute("successMessage", msg.toString());
        } catch (IOException e) {
            log.error("Error reading Google Sheet during driver import execute", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Could not read the Google Sheet. Check the URL and service account credentials.");
        } catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
            log.error("Error executing driver sheet import", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.error("Driver sheet import hit DB constraint — transaction rolled back, no rows inserted", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Import failed due to a database constraint. Nothing was imported. See server logs for details.");
        } catch (IllegalStateException | DataAccessException e) {
            log.error("Error executing driver sheet import", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed due to an internal error. See server logs for details.");
        }
        return "redirect:/admin/drivers/import";
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("seasons", seasonManagementService.findAll());
        model.addAttribute("sheetsAvailable", googleSheetsService.isAvailable());
    }
}
