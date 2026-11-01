package org.ctc.gt7sync;

import java.io.IOException;
import org.ctc.admin.dto.Gt7SyncForm;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
	public String execute(@ModelAttribute Gt7SyncForm form, BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid sync selection. Fetch and review the catalog again.");
            return "redirect:/admin/gt7-sync";
        }
		try {
			var result = syncService.executeSync(
					form.getSelectedCars(), form.getSelectedTracks(), form.getSelectedMetadata(), form.getPreviewId());

			String msg = result.carsImported() + " cars and " + result.tracksImported() + " tracks imported in " + result.durationSeconds() + "s";
			msg += "; " + result.carsUpdated() + " car metadata updates";
			if (!result.errors().isEmpty()) {
				msg += " (" + result.errors().size() + " warnings)";
			}
			redirectAttributes.addFlashAttribute("successMessage", msg);
            if (!result.errors().isEmpty()) redirectAttributes.addFlashAttribute("syncWarnings", result.errors());
		} catch (IOException | RuntimeException e) {
			log.error("GT7 sync execute failed", e);
			redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
		}
		return "redirect:/admin/gt7-sync";
	}
}
