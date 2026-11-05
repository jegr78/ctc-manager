package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PickCorrectionForm;
import org.ctc.admin.dto.PickReviewForm;
import org.ctc.admin.service.MatchdayPickAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickState;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/matchday-picks")
@RequiredArgsConstructor
public class MatchdayPickController {
    private final MatchdayPickAdminService service;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, @RequestParam(required = false) UUID matchdayId,
                       @RequestParam(required = false) PickState state, Model model) {
        var overview = service.overview(seasonId, matchdayId, state);
        model.addAttribute("overview", overview); model.addAttribute("selectedSeasonId", overview.seasonId());
        model.addAttribute("states", PickState.values()); return "admin/matchday-picks";
    }

    @GetMapping("/{raceId}")
    public String detail(@PathVariable UUID raceId, Model model) {
        var detail = service.detail(raceId); model.addAttribute("review", detail); model.addAttribute("selectedSeasonId", detail.row().seasonId());
        if (!model.containsAttribute("form")) model.addAttribute("form", service.reviewForm(detail));
        return "admin/matchday-pick-review";
    }

    @PostMapping("/{raceId}/decision")
    public String decide(@PathVariable UUID raceId, @Valid @ModelAttribute("form") PickReviewForm form, BindingResult result,
                         Principal principal, Model model, RedirectAttributes redirect) {
        model.addAttribute("form", form);
        if (result.hasErrors()) return detail(raceId, model);
        try {
            service.decide(raceId, form, principal);
            redirect.addFlashAttribute("successMessage", "Pick review decision saved. Publication remains separate.");
            return "redirect:/admin/matchday-picks/" + raceId;
        } catch (BusinessRuleException rejected) {
            result.reject("review", rejected.getMessage()); return detail(raceId, model);
        }
    }

    @GetMapping("/{raceId}/correction")
    public String correction(@PathVariable UUID raceId, Model model) {
        var form = service.correctionForm(raceId);
        model.addAttribute("form", form);
        return correctionModel(raceId, form, model);
    }

    @PostMapping("/{raceId}/correction/refresh")
    public String refreshCorrection(@PathVariable UUID raceId, @ModelAttribute("form") PickCorrectionForm form, BindingResult result, Model model) {
        model.addAttribute("form", form); return correctionModel(raceId, form, model);
    }

    @PostMapping("/{raceId}/correction")
    public String correct(@PathVariable UUID raceId, @Valid @ModelAttribute("form") PickCorrectionForm form, BindingResult result,
                          Principal principal, Model model, RedirectAttributes redirect) {
        model.addAttribute("form", form);
        if (result.hasErrors()) return correctionModel(raceId, form, model);
        try {
            service.correct(raceId, form, principal);
            redirect.addFlashAttribute("successMessage", "Internal correction saved. The approved revision and team lock remain; publication is separate.");
            return "redirect:/admin/matchday-picks/" + raceId;
        } catch (BusinessRuleException rejected) {
            result.reject("correction", rejected.getMessage()); return correctionModel(raceId, form, model);
        }
    }

    private String correctionModel(UUID raceId, PickCorrectionForm form, Model model) {
        var detail = service.detail(raceId); model.addAttribute("review", detail); model.addAttribute("selectedSeasonId", detail.row().seasonId());
        model.addAttribute("choices", service.correctionChoices(raceId, form));
        model.addAttribute("fuels", org.ctc.domain.model.PickInitialFuel.values()); model.addAttribute("tyres", org.ctc.domain.model.PickTyre.values());
        var profile = form.getLobbyRulesProfile();
        model.addAttribute("startTypes", profile == null ? java.util.List.of() : profile.getStartTypes());
        model.addAttribute("slipstreams", profile == null ? java.util.List.of() : profile.getSlipstreams());
        return "admin/matchday-pick-correction";
    }
}
