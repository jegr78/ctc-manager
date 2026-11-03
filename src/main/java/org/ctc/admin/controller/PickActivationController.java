package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PickActivationForm;
import org.ctc.admin.service.PickActivationAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/seasons/{seasonId}/pick-collection")
@RequiredArgsConstructor
public class PickActivationController {
    private static final String VIEW = "admin/pick-collection";
    private final PickActivationAdminService service;

    @GetMapping
    public String view(@PathVariable UUID seasonId, Model model) {
        var view = service.view(seasonId); model.addAttribute("collection", view); model.addAttribute("selectedSeasonId", seasonId);
        if (model.getAttribute("form") == null) model.addAttribute("form", service.form(view));
        return VIEW;
    }
    @PostMapping("/enable")
    public String enable(@PathVariable UUID seasonId, @Valid @ModelAttribute("form") PickActivationForm form, BindingResult result,
                         Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return view(seasonId, model);
        try {
            service.enable(seasonId, form, principal);
            redirect.addFlashAttribute("successMessage", "Collection enabled. Confirmed public fields are retained and other managed choices stay withheld.");
            return redirect(seasonId);
        } catch (BusinessRuleException failure) { result.reject("activation", failure.getMessage()); return view(seasonId, model); }
    }
    @PostMapping("/pause")
    public String pause(@PathVariable UUID seasonId, @Valid @ModelAttribute("form") PickActivationForm form, BindingResult result,
                        Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return view(seasonId, model);
        try {
            service.pause(seasonId, form, principal);
            redirect.addFlashAttribute("successMessage", "Collection paused. Enrolled races and their publication history remain protected.");
            return redirect(seasonId);
        } catch (BusinessRuleException failure) { result.reject("activation", failure.getMessage()); return view(seasonId, model); }
    }
    private static String redirect(UUID seasonId) { return "redirect:/admin/seasons/" + seasonId + "/pick-collection"; }
}
