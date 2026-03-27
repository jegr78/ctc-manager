package de.ctc.dataimport;

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

    @GetMapping
    public String showImportForm(Model model) {
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/import";
    }

    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          @RequestParam String seasonName,
                          @RequestParam String matchdayLabel,
                          @RequestParam(required = false) String track,
                          @RequestParam(required = false) String car,
                          Model model) {
        try {
            var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, track, car);
            var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

            model.addAttribute("preview", preview);
            model.addAttribute("metadata", metadata);
            model.addAttribute("seasons", seasonRepository.findAll());
            return "admin/import-preview";
        } catch (Exception e) {
            log.error("Error parsing CSV", e);
            model.addAttribute("seasons", seasonRepository.findAll());
            model.addAttribute("errorMessage", "Error reading CSV: " + e.getMessage());
            return "admin/import";
        }
    }

    @PostMapping("/execute")
    public String execute(@RequestParam String seasonName,
                          @RequestParam String matchdayLabel,
                          @RequestParam(required = false) String track,
                          @RequestParam(required = false) String car,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) Map<String, String> allParams,
                          RedirectAttributes redirectAttributes) {
        try {
            var metadata = new CsvImportService.ImportMetadata(seasonName, matchdayLabel, track, car);
            var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

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

            var result = csvImportService.executeImport(preview, confirmedMatches, createNewDrivers);

            if (result.hasErrors()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Import with errors: " + String.join(", ", result.getErrors()));
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Import successful: " + result.getImportedRaces().size() + " races, " +
                        result.getNewDriversCreated() + " new drivers");
            }
        } catch (Exception e) {
            log.error("Error executing import", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import error: " + e.getMessage());
        }
        return "redirect:/admin/import";
    }
}
