package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.IncidentDeadlineForm;
import org.ctc.admin.service.IncidentAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class IncidentWindowController {
    private final IncidentAdminService service;

    @PostMapping("/admin/incident-contexts/{id}/window/open")
    public String open(@PathVariable UUID id, @Valid @ModelAttribute("incidentDeadlineForm") IncidentDeadlineForm form, BindingResult result,
                       Principal principal, Model model, RedirectAttributes redirect) { return save(id, true, form, result, principal, model, redirect); }
    @PostMapping("/admin/incident-contexts/{id}/window/deadline")
    public String deadline(@PathVariable UUID id, @Valid @ModelAttribute("incidentDeadlineForm") IncidentDeadlineForm form, BindingResult result,
                           Principal principal, Model model, RedirectAttributes redirect) { return save(id, false, form, result, principal, model, redirect); }
    @PostMapping("/admin/matches/{id}/incidents/window/open")
    public String openMatch(@PathVariable UUID id, @Valid @ModelAttribute("incidentDeadlineForm") IncidentDeadlineForm form, BindingResult result,
                            Principal principal, Model model, RedirectAttributes redirect) { return save(service.contextForMatch(id), true, form, result, principal, model, redirect); }
    @PostMapping("/admin/matches/{id}/incidents/window/deadline")
    public String deadlineMatch(@PathVariable UUID id, @Valid @ModelAttribute("incidentDeadlineForm") IncidentDeadlineForm form, BindingResult result,
                                Principal principal, Model model, RedirectAttributes redirect) { return save(service.contextForMatch(id), false, form, result, principal, model, redirect); }
    private String save(UUID id, boolean opening, IncidentDeadlineForm form, BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                if (opening) service.open(id, form, principal); else service.change(id, form, principal);
                redirect.addFlashAttribute("successMessage", opening ? "Reporting deadline confirmed. Reporting is open." : "Reporting deadline changed; its reason is retained.");
                return IncidentContextController.redirect(id);
            } catch (BusinessRuleException failure) { result.reject("incident", failure.getMessage()); }
        }
        model.addAttribute("editingAttempt", true); model.addAttribute("opening", opening);
        var page = service.view(id);
        model.addAttribute("page", page); model.addAttribute("selectedSeasonId", page.seasonId());
        model.addAttribute("incidentBindingForm", service.bindingForm(page));
        return IncidentContextController.VIEW;
    }
}
