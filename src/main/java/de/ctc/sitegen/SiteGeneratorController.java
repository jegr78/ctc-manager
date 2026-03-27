package de.ctc.sitegen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/generate")
@RequiredArgsConstructor
public class SiteGeneratorController {

    private final SiteGeneratorService siteGeneratorService;

    @GetMapping
    public String showGenerate() {
        return "admin/generate";
    }

    @PostMapping
    public String generate(RedirectAttributes redirectAttributes) {
        var result = siteGeneratorService.generate();

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Generierung mit Fehlern: " + String.join(", ", result.getErrors()));
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Seite erfolgreich generiert: " + result.getPagesGenerated() + " Seiten");
        }

        return "redirect:/admin/generate";
    }
}
