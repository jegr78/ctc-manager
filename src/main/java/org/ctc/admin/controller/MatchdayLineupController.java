package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.LineupApplyForm;
import org.ctc.admin.dto.LineupCorrectionForm;
import org.ctc.admin.dto.LineupWindowForm;
import org.ctc.admin.service.MatchdayLineupAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LineupWindowKind;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/matchday-lineups")
@RequiredArgsConstructor
public class MatchdayLineupController {
    private final MatchdayLineupAdminService service;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, @RequestParam(required = false) UUID matchdayId, Model model) {
        var overview = service.overview(seasonId, matchdayId);
        model.addAttribute("overview", overview);
        model.addAttribute("selectedSeasonId", overview.seasonId());
        return "admin/matchday-lineups";
    }

    @GetMapping("/{raceId}/{teamId}")
    public String detail(@PathVariable UUID raceId, @PathVariable UUID teamId, Model model) {
        var detail = service.detail(raceId, teamId);
        model.addAttribute("lineup", detail);
        model.addAttribute("selectedSeasonId", detail.seasonId());
        model.addAttribute("windowKinds", LineupWindowKind.values());
        if (!model.containsAttribute("applyForm") && detail.latestRevision() != null) model.addAttribute("applyForm", service.applyForm(detail));
        if (!model.containsAttribute("correctionForm")) model.addAttribute("correctionForm", service.correctionForm(detail));
        if (!model.containsAttribute("windowForm")) model.addAttribute("windowForm", service.windowForm(detail));
        return "admin/matchday-lineup-detail";
    }

    @PostMapping("/{raceId}/{teamId}/apply")
    public String apply(@PathVariable UUID raceId, @PathVariable UUID teamId, @Valid @ModelAttribute("applyForm") LineupApplyForm form,
                        BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return detail(raceId, teamId, model);
        try {
            var warnings = service.apply(raceId, teamId, form, principal);
            redirect.addFlashAttribute("successMessage", message("Lineup applied to the race lineup. Publication remains separate.", warnings));
            return redirect(raceId, teamId);
        } catch (BusinessRuleException rejected) {
            result.reject("apply", rejected.getMessage());
            return detail(raceId, teamId, model);
        }
    }

    @PostMapping("/{raceId}/{teamId}/correction")
    public String correct(@PathVariable UUID raceId, @PathVariable UUID teamId, @Valid @ModelAttribute("correctionForm") LineupCorrectionForm form,
                          BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return detail(raceId, teamId, model);
        try {
            var warnings = service.correct(raceId, teamId, form, principal);
            redirect.addFlashAttribute("successMessage", message("Management correction saved as a new revision. Apply it to change the race lineup.", warnings));
            return redirect(raceId, teamId);
        } catch (BusinessRuleException rejected) {
            result.reject("correction", rejected.getMessage());
            return detail(raceId, teamId, model);
        }
    }

    @PostMapping("/{raceId}/{teamId}/window")
    public String window(@PathVariable UUID raceId, @PathVariable UUID teamId, @Valid @ModelAttribute("windowForm") LineupWindowForm form,
                         BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return detail(raceId, teamId, model);
        try {
            service.decideWindow(raceId, form, principal);
            redirect.addFlashAttribute("successMessage", form.getKind() == LineupWindowKind.OPEN
                    ? "Lineups opened for this leg until its deadline." : "Lineups closed for this leg.");
            return redirect(raceId, teamId);
        } catch (BusinessRuleException rejected) {
            result.reject("window", rejected.getMessage());
            return detail(raceId, teamId, model);
        }
    }

    private static String message(String text, List<String> warnings) {
        return warnings.isEmpty() ? text : text + " Note: " + String.join("; ", warnings) + ".";
    }

    private static String redirect(UUID raceId, UUID teamId) { return "redirect:/admin/matchday-lineups/" + raceId + "/" + teamId; }
}
