package de.ctc.dataimport;

import de.ctc.domain.repository.PlayoffMatchupRepository;
import de.ctc.domain.repository.PlayoffRepository;
import de.ctc.domain.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/import")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final SeasonRepository seasonRepository;
    private final de.ctc.domain.repository.MatchdayRepository matchdayRepository;
    private final GoogleSheetsService googleSheetsService;
    private final ScorecardParser scorecardParser;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final PlayoffRepository playoffRepository;

    @GetMapping
    public String showImportForm(Model model) {
        addCommonAttributes(model);
        return "admin/import";
    }

    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          @RequestParam String seasonName,
                          @RequestParam(required = false) String matchdayLabel,
                          @RequestParam(required = false) UUID matchdayId,
                          @RequestParam(required = false) UUID playoffMatchupId,
                          Model model) {
        try {
            var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, null, null, playoffMatchupId, matchdayId);
            var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

            csvImportService.checkDuplicate(preview);
            model.addAttribute("preview", preview);
            model.addAttribute("metadata", metadata);
            model.addAttribute("source", "csv");
            addMatchdayName(model, metadata);
            addCommonAttributes(model);
            return "admin/import-preview";
        } catch (Exception e) {
            log.error("Error parsing CSV", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Error reading CSV: " + e.getMessage());
            return "admin/import";
        }
    }

    @PostMapping("/preview-sheet")
    public String previewSheet(@RequestParam String sheetUrl,
                               @RequestParam String seasonName,
                               @RequestParam(required = false) String matchdayLabel,
                               @RequestParam(required = false) UUID matchdayId,
                               @RequestParam(required = false) UUID playoffMatchupId,
                               Model model) {
        try {
            var spreadsheetId = googleSheetsService.extractSpreadsheetId(sheetUrl);
            var sheetData = googleSheetsService.readRange(spreadsheetId, "A:H");

            var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, null, null, playoffMatchupId, matchdayId);
            var preview = scorecardParser.parse(sheetData, metadata);

            csvImportService.checkDuplicate(preview);
            model.addAttribute("preview", preview);
            model.addAttribute("metadata", metadata);
            model.addAttribute("source", "sheet");
            model.addAttribute("sheetUrl", sheetUrl);
            addMatchdayName(model, metadata);
            addCommonAttributes(model);
            return "admin/import-preview";
        } catch (Exception e) {
            log.error("Error reading Google Sheet", e);
            addCommonAttributes(model);
            model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
            return "admin/import";
        }
    }

    @PostMapping("/execute")
    public String execute(@RequestParam String seasonName,
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
            var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, null, null, playoffMatchupId, matchdayId);

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
        } catch (Exception e) {
            log.error("Error executing import", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    private void addMatchdayName(Model model, CsvImportService.ImportMetadata metadata) {
        if (metadata.hasMatchdayId()) {
            matchdayRepository.findById(metadata.matchdayId())
                    .ifPresent(md -> model.addAttribute("matchdayName", md.getLabel()));
        }
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("seasons", seasonRepository.findAll());
        model.addAttribute("sheetsAvailable", googleSheetsService.isAvailable());

        // Load playoff matchups for all seasons that have playoffs
        List<PlayoffMatchupDto> matchups = new ArrayList<>();
        for (var season : seasonRepository.findAll()) {
            playoffRepository.findBySeasonId(season.getId()).ifPresent(playoff -> {
                var playoffMatchups = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId());
                for (var matchup : playoffMatchups) {
                    if (matchup.isReady()) {
                        matchups.add(new PlayoffMatchupDto(
                                matchup.getId(),
                                season.getName(),
                                matchup.getRound().getLabel(),
                                matchup.getTeam1().getShortName(),
                                matchup.getTeam2().getShortName()
                        ));
                    }
                }
            });
        }
        model.addAttribute("playoffMatchups", matchups);
    }

    public record PlayoffMatchupDto(UUID id, String seasonName, String roundLabel,
                                     String team1, String team2) {
        public String displayLabel() {
            return roundLabel + ": " + team1 + " vs " + team2;
        }
    }
}
