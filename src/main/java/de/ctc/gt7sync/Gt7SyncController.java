package de.ctc.gt7sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/gt7-sync")
@RequiredArgsConstructor
public class Gt7SyncController {

    private final Gt7SyncService syncService;

    @GetMapping
    public String show(Model model) {
        return "admin/gt7-sync";
    }

    @PostMapping("/preview")
    public String preview(Model model, RedirectAttributes redirectAttributes) {
        try {
            var preview = syncService.fetchAndPreview();
            model.addAttribute("preview", preview);
            return "admin/gt7-sync-preview";
        } catch (IOException e) {
            log.error("GT7 sync preview failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Could not fetch GT7 data: " + e.getMessage());
            return "redirect:/admin/gt7-sync";
        }
    }

    @PostMapping("/execute")
    public String execute(@RequestParam(required = false) List<String> selectedCars,
                          @RequestParam(required = false) List<String> selectedTracks,
                          RedirectAttributes redirectAttributes) {
        try {
            var result = syncService.executeSync(
                    selectedCars != null ? selectedCars : List.of(),
                    selectedTracks != null ? selectedTracks : List.of());

            String msg = result.carsImported() + " cars and " + result.tracksImported() + " tracks imported";
            if (!result.errors().isEmpty()) {
                msg += " (" + result.errors().size() + " warnings)";
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            log.error("GT7 sync execute failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/gt7-sync";
    }
}
