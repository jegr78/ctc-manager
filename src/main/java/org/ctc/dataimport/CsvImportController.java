package org.ctc.dataimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.SeasonManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final GoogleSheetsService googleSheetsService;
    private final ScorecardParser scorecardParser;
    private final SeasonManagementService seasonManagementService;

    @GetMapping
    public String showImportForm(Model model) {
        addCommonAttributes(model);
        return "admin/import";
    }

    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          @RequestParam UUID seasonId,
                          @RequestParam(required = false) String matchdayLabel,
                          @RequestParam(required = false) UUID matchdayId,
                          @RequestParam(required = false) UUID playoffMatchupId,
                          Model model) {
        try {
            var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);
            var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

            csvImportService.checkDuplicate(preview);
            model.addAttribute("preview", preview);
            model.addAttribute("metadata", metadata);
            seasonManagementService.findByIdOptional(seasonId).ifPresent(s -> model.addAttribute("seasonDisplayLabel", s.getDisplayLabel()));
            model.addAttribute("source", "csv");
            addMatchdayName(model, metadata);
            addCommonAttributes(model);
            return "admin/import-preview";
        } catch (IOException e) {
            log.error("Error parsing CSV", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Error reading CSV: " + e.getMessage());
            return "admin/import";
        }
    }

    @PostMapping("/preview-sheet")
    public String previewSheet(@RequestParam String sheetUrl,
                               @RequestParam UUID seasonId,
                               @RequestParam(required = false) String matchdayLabel,
                               @RequestParam(required = false) UUID matchdayId,
                               @RequestParam(required = false) UUID playoffMatchupId,
                               Model model) {
        try {
            var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
            var sheetData = googleSheetsService.readRange(spreadsheetId, "A:H");

            var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);
            var preview = scorecardParser.parse(sheetData, metadata);

            csvImportService.checkDuplicate(preview);
            model.addAttribute("preview", preview);
            model.addAttribute("metadata", metadata);
            seasonManagementService.findByIdOptional(seasonId).ifPresent(s -> model.addAttribute("seasonDisplayLabel", s.getDisplayLabel()));
            model.addAttribute("source", "sheet");
            model.addAttribute("sheetUrl", sheetUrl);
            addMatchdayName(model, metadata);
            addCommonAttributes(model);
            return "admin/import-preview";
        } catch (IOException | IllegalStateException e) {
            log.error("Error reading Google Sheet", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
            return "admin/import";
        }
    }

    @PostMapping("/execute")
    public String execute(@RequestParam UUID seasonId,
                          @RequestParam(required = false) String matchdayLabel,
                          @RequestParam(required = false) UUID matchdayId,
                          @RequestParam(required = false) UUID playoffMatchupId,
                          @RequestParam(required = false, defaultValue = "csv") String source,
                          @RequestParam(required = false) String sheetUrl,
                          @RequestParam(required = false) MultipartFile file,
                          @RequestParam(required = false, defaultValue = "false") boolean overwrite,
                          @RequestParam(required = false) Map<String, String> allParams,
                          RedirectAttributes redirectAttributes) {
        try {
            var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);

            // Re-parse from original source
            CsvImportService.ImportPreview preview;
            if ("sheet".equals(source) && sheetUrl != null && !sheetUrl.isBlank()) {
                var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
                var sheetData = googleSheetsService.readRange(spreadsheetId, "A:H");
                preview = scorecardParser.parse(sheetData, metadata);
            } else {
                if (file == null || file.isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage", "No CSV file provided");
                    return "redirect:/admin/import";
                }
                preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);
            }

            // Collect confirmed fuzzy matches and new driver decisions
            Map<String, UUID> confirmedMatches = new HashMap<>();
            Set<String> createNewDrivers = new HashSet<>();

            if (allParams != null) {
                for (var entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith("confirm_")) {
                        var psnId = entry.getKey().substring("confirm_".length());
                        if ("new".equals(entry.getValue())) {
                            createNewDrivers.add(psnId);
                        } else {
                            confirmedMatches.put(psnId, UUID.fromString(entry.getValue()));
                        }
                    }
                }
            }

            var result = csvImportService.executeImport(preview, confirmedMatches, createNewDrivers, overwrite);

            if (result.hasErrors()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Import with errors: " + String.join(", ", result.getErrors()));
            } else {
                var msg = "Import successful: " + result.getImportedRaces().size() + " races, " +
                        result.getNewDriversCreated() + " new drivers";
                if (result.getLineupCount() > 0) {
                    msg += ", " + result.getLineupCount() + " lineup entries";
                }
                redirectAttributes.addFlashAttribute("successMessage", msg);
            }
        } catch (IOException | IllegalStateException e) {
            log.error("Error executing import", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    private void addMatchdayName(Model model, CsvImportService.ImportMetadata metadata) {
        if (metadata.hasMatchdayId()) {
            csvImportService.getMatchdayLabel(metadata.matchdayId())
                    .ifPresent(label -> model.addAttribute("matchdayName", label));
        }
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("seasons", csvImportService.getAllSeasons());
        model.addAttribute("sheetsAvailable", googleSheetsService.isAvailable());
        model.addAttribute("playoffMatchups", csvImportService.getPlayoffMatchups());
    }
}
