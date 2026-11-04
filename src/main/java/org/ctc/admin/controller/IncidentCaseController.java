package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.IncidentRulingDraftForm;
import org.ctc.admin.dto.IncidentRulingPublishForm;
import org.ctc.admin.dto.IncidentRulingReviewForm;
import org.ctc.admin.service.IncidentCaseAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/incidents/{reportId}")
public class IncidentCaseController {
    private static final String VIEW = "admin/incident-case";
    private final IncidentCaseAdminService service;
    @GetMapping
    public String view(@PathVariable UUID reportId, Model model) { return render(service.view(reportId), model); }
    @PostMapping("/ruling/save")
    public String save(@PathVariable UUID reportId, @Valid @ModelAttribute("rulingDraftForm") IncidentRulingDraftForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return render(service.view(reportId), model);
        try {
            service.saveDraft(reportId, form, principal);
            redirect.addFlashAttribute("successMessage", "Draft saved. Review the public preview before publishing.");
            return redirect(reportId);
        } catch (BusinessRuleException failure) {
            result.reject("ruling", failure.getMessage());
            return render(service.view(reportId), model);
        }
    }
    @PostMapping("/ruling/publish")
    public String publish(@PathVariable UUID reportId, @Valid @ModelAttribute("rulingPublishForm") IncidentRulingPublishForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return render(service.view(reportId), model);
        try {
            service.publish(reportId, form, principal);
            redirect.addFlashAttribute("successMessage", "Ruling publication queued. Confirmed Discord state is shown separately.");
            return redirect(reportId);
        } catch (BusinessRuleException failure) {
            result.reject("ruling", failure.getMessage());
            return render(service.view(reportId), model);
        }
    }
    @PostMapping("/ruling/reconcile")
    public String reconcile(@PathVariable UUID reportId, @Valid @ModelAttribute("rulingReviewForm") IncidentRulingReviewForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        model.addAttribute("reviewingAttempt", true);
        if (result.hasErrors()) return render(service.view(reportId), model);
        try {
            service.reconcile(reportId, form, principal);
            redirect.addFlashAttribute("successMessage", "Ruling delivery review recorded.");
            return redirect(reportId);
        } catch (BusinessRuleException failure) {
            result.reject("ruling", failure.getMessage());
            return render(service.view(reportId), model);
        }
    }
    private String render(IncidentCaseAdminService.Page page, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("selectedSeasonId", page.seasonId());
        if (!model.containsAttribute("rulingDraftForm")) {
            var form = new IncidentRulingDraftForm(); form.setExpectedVersion(page.version()); form.setGeneration(page.generation());
            form.setText(page.draftText()); form.setReason(page.draftReason()); model.addAttribute("rulingDraftForm", form);
        }
        if (!model.containsAttribute("rulingPublishForm")) {
            var form = new IncidentRulingPublishForm(); form.setExpectedVersion(page.version()); form.setRulingVersion(page.latestRulingVersion());
            form.setGeneration(page.generation()); model.addAttribute("rulingPublishForm", form);
        }
        if (!model.containsAttribute("rulingReviewForm")) {
            var form = new IncidentRulingReviewForm(); form.setExpectedVersion(page.version()); form.setGeneration(page.generation());
            if (page.publication() != null) { form.setPublicationId(page.publication().id()); form.setPublicationVersion(page.publication().version()); }
            model.addAttribute("rulingReviewForm", form);
        }
        if (!model.containsAttribute("reviewingAttempt")) model.addAttribute("reviewingAttempt", false);
        var publish = (IncidentRulingPublishForm) model.getAttribute("rulingPublishForm");
        var review = (IncidentRulingReviewForm) model.getAttribute("rulingReviewForm");
        model.addAttribute("publicationCurrent", publish != null && Objects.equals(publish.getExpectedVersion(), page.version())
                && Objects.equals(publish.getRulingVersion(), page.latestRulingVersion()) && Objects.equals(publish.getGeneration(), page.generation()));
        model.addAttribute("reviewCurrent", review != null && page.publication() != null && Objects.equals(review.getExpectedVersion(), page.version())
                && Objects.equals(review.getPublicationId(), page.publication().id()) && Objects.equals(review.getPublicationVersion(), page.publication().version())
                && Objects.equals(review.getGeneration(), page.generation()));
        return VIEW;
    }
    private static String redirect(UUID reportId) { return "redirect:/admin/incidents/" + reportId; }
}
