package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PickPublicationForm;
import org.ctc.admin.service.PickPublicationAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/admin/pick-publications") @RequiredArgsConstructor
public class PickPublicationController {
    private final PickPublicationAdminService service;
    @GetMapping public String list(Model model) { model.addAttribute("publications", service.pending()); return "admin/pick-publications"; }
    @GetMapping("/{batchId}") public String detail(@PathVariable UUID batchId, Model model) {
        var detail = service.detail(batchId); model.addAttribute("publication", detail);
        if (model.getAttribute("form") == null) model.addAttribute("form", service.form(detail));
        return "admin/pick-publication";
    }
    @PostMapping("/{batchId}/confirm")
    public String confirm(@PathVariable UUID batchId, @Valid @ModelAttribute("form") PickPublicationForm form, BindingResult result,
                          Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return detail(batchId, model);
        try {
            service.confirm(batchId, form, principal); redirect.addFlashAttribute("successMessage", "The captured publication was verified and confirmed.");
            return redirect(batchId);
        } catch (BusinessRuleException failure) { result.reject("publication", failure.getMessage()); return detail(batchId, model); }
        catch (IOException failure) { result.reject("publication", "Discord verification did not complete. The publication remains unconfirmed; retry the check later."); return detail(batchId, model); }
    }
    @PostMapping("/{batchId}/abandon")
    public String abandon(@PathVariable UUID batchId, @Valid @ModelAttribute("form") PickPublicationForm form, BindingResult result,
                          Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return detail(batchId, model);
        try {
            service.abandon(batchId, form, principal); redirect.addFlashAttribute("successMessage", "The publication was retired. A later explicit publication will use a fresh message.");
            return redirect(batchId);
        } catch (BusinessRuleException failure) { result.reject("publication", failure.getMessage()); return detail(batchId, model); }
    }
    private static String redirect(UUID batchId) { return "redirect:/admin/pick-publications/" + batchId; }
}
