package org.ctc.admin.controller;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.gt7sync.GtEngineTrackMapService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Track list actions for GT-Engine layout maps; both return to the track list. */
@Controller
@RequiredArgsConstructor
public class GtEngineTrackMapController {
    private final GtEngineTrackMapService trackMaps;

    @PostMapping("/admin/tracks/gt-engine-maps")
    public String loadAll(RedirectAttributes redirect) {
        try {
            var result = trackMaps.loadAll();
            redirect.addFlashAttribute(result.failures() ? "errorMessage" : "successMessage", result.message());
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("errorMessage", rejected.getMessage());
        }
        return "redirect:/admin/tracks";
    }

    @PostMapping("/admin/tracks/gt-engine-maps/compare")
    public String compare(RedirectAttributes redirect) {
        return run(redirect, () -> trackMaps.compare().message());
    }

    private static String run(RedirectAttributes redirect, Supplier<String> action) {
        try {
            redirect.addFlashAttribute("successMessage", action.get());
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("errorMessage", rejected.getMessage());
        }
        return "redirect:/admin/tracks";
    }
}
