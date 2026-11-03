package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.IncidentBindingForm;
import org.ctc.admin.service.IncidentAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class IncidentContextController {
    static final String VIEW = "admin/incident-context";
    private final IncidentAdminService service;

    @GetMapping("/admin/incident-contexts/{id}")
    public String context(@PathVariable UUID id, Model model) { return render(service.view(id), model); }
    @GetMapping("/admin/matches/{id}/incidents")
    public String match(@PathVariable UUID id, Model model) { return target(IncidentMatchRef.match(id), model); }
    @GetMapping("/admin/playoffs/matchup/{id}/incidents")
    public String playoff(@PathVariable UUID id, Model model) { return target(IncidentMatchRef.playoff(id), model); }
    @PostMapping("/admin/matches/{id}/incidents/bind")
    public String bindMatch(@PathVariable UUID id, @Valid @ModelAttribute("incidentBindingForm") IncidentBindingForm form, BindingResult result,
                            Principal principal, Model model, RedirectAttributes redirect) {
        return bind(IncidentMatchRef.match(id), form, result, principal, model, redirect);
    }
    @PostMapping("/admin/playoffs/matchup/{id}/incidents/bind")
    public String bindPlayoff(@PathVariable UUID id, @Valid @ModelAttribute("incidentBindingForm") IncidentBindingForm form, BindingResult result,
                              Principal principal, Model model, RedirectAttributes redirect) {
        return bind(IncidentMatchRef.playoff(id), form, result, principal, model, redirect);
    }
    private String target(IncidentMatchRef reference, Model model) {
        var page = service.viewTarget(reference);
        return page.bound() ? redirect(page.contextId()) : render(page, model);
    }
    private String bind(IncidentMatchRef reference, IncidentBindingForm form, BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return render(service.viewTarget(reference), model);
        try {
            UUID id = service.bind(reference, form, principal);
            redirect.addFlashAttribute("successMessage", "Incident context bound. Confirm a deadline separately to open reporting.");
            return redirect(id);
        } catch (BusinessRuleException failure) { result.reject("incident", failure.getMessage()); return render(service.viewTarget(reference), model); }
    }
    private String render(IncidentAdminService.Page page, Model model) {
        model.addAttribute("page", page); model.addAttribute("selectedSeasonId", page.seasonId());
        if (model.getAttribute("incidentBindingForm") == null) model.addAttribute("incidentBindingForm", service.bindingForm(page));
        if (model.getAttribute("incidentDeadlineForm") == null) model.addAttribute("incidentDeadlineForm", service.deadlineForm(page));
        if (model.getAttribute("editingAttempt") == null) model.addAttribute("editingAttempt", false);
        if (model.getAttribute("opening") == null) model.addAttribute("opening", page.draft());
        return VIEW;
    }
    static String redirect(UUID id) { return "redirect:/admin/incident-contexts/" + id; }
}
