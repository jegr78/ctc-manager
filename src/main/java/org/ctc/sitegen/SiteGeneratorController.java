package org.ctc.sitegen;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.SiteGenerationForm;
import org.ctc.sitegen.publication.SitePublicationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/generate")
@RequiredArgsConstructor
public class SiteGeneratorController {

    private final SiteGeneratorService siteGeneratorService;
    private final SitePublicationService publicationService;

    @GetMapping
    public String showGenerate(@RequestParam(required = false) String snapshot, Model model,
                               HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", "frame-src 'self'");
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("publicationEnabled", publicationService.isPublicationEnabled());
        try {
            model.addAttribute("publication", publicationService.publication());
            if (snapshot != null) model.addAttribute("snapshot", publicationService.snapshot(snapshot));
        } catch (IOException | IllegalArgumentException error) {
            model.addAttribute("errorMessage", "Publication information is unavailable. Review the stored request before publishing.");
        }
        return "admin/generate";
    }

    @PostMapping
    public String generate(@ModelAttribute SiteGenerationForm form, RedirectAttributes redirect) {
        var result = siteGeneratorService.generate();
        if (form.getSnapshot() != null) redirect.addAttribute("snapshot", form.getSnapshot());
        if (result.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Generation with errors: " + String.join(", ", result.getErrors()));
        } else {
            redirect.addFlashAttribute("successMessage", "Site generated successfully: " + result.getPagesGenerated() + " pages");
            if (!result.getWarnings().isEmpty()) redirect.addFlashAttribute("warningMessage", String.join("; ", result.getWarnings()));
            if (result.getSnapshot() != null) redirect.addAttribute("snapshot", result.getSnapshot().id());
        }
        return "redirect:/admin/generate";
    }
}
