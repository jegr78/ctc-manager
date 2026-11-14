package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
}
