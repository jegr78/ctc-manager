package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.*;
import org.ctc.admin.service.IncidentDeliveryAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class IncidentDeliveryController {
    private final IncidentDeliveryAdminService service;
    @GetMapping("/admin/incident-contexts/{id}/deliveries")
    public String overview(@PathVariable UUID id,Model model) {return renderOverview(id,model);}
    @GetMapping("/admin/incident-deliveries/{id}")
    public String review(@PathVariable UUID id,Model model) {return renderReview(id,model);}
    @PostMapping("/admin/incident-deliveries/{id}")
    public String reconcile(@PathVariable UUID id,@Valid @ModelAttribute("deliveryReviewForm") IncidentDeliveryReviewForm form,BindingResult result,Principal principal,Model model,RedirectAttributes redirect) {
        if(!result.hasErrors())try {var contextId=service.reconcile(id,form,principal);redirect.addFlashAttribute("successMessage","Delivery review saved.");return redirect(contextId);}catch(BusinessRuleException failure){result.reject("delivery",failure.getMessage());}
        return renderReview(id,model);
    }
    @PostMapping("/admin/incident-contexts/{id}/deliveries/resume")
    public String resume(@PathVariable UUID id,@Valid @ModelAttribute("deliveryResumeForm") IncidentDeliveryResumeForm form,BindingResult result,Principal principal,Model model,RedirectAttributes redirect) {
        if(!result.hasErrors())try {service.resume(id,form,principal);redirect.addFlashAttribute("successMessage","External delivery resumed for this reporting context.");return redirect(id);}catch(BusinessRuleException failure){result.reject("delivery",failure.getMessage());}
        return renderOverview(id,model);
    }
    private String renderOverview(UUID id,Model model) {
        var page=service.overview(id);model.addAttribute("page",page);model.addAttribute("selectedSeasonId",page.seasonId());
        if(model.getAttribute("deliveryResumeForm")==null){var form=new IncidentDeliveryResumeForm();form.setExpectedVersion(page.version());form.setGeneration(page.generation());model.addAttribute("deliveryResumeForm",form);}
        return "admin/incident-deliveries";
    }
    private String renderReview(UUID id,Model model) {
        var page=service.review(id);model.addAttribute("page",page);model.addAttribute("selectedSeasonId",page.seasonId());
        if(model.getAttribute("deliveryReviewForm")==null){var form=new IncidentDeliveryReviewForm();form.setExpectedVersion(page.version());form.setGeneration(page.generation());model.addAttribute("deliveryReviewForm",form);}
        return "admin/incident-delivery-review";
    }
    private static String redirect(UUID id){return "redirect:/admin/incident-contexts/"+id+"/deliveries";}
}
