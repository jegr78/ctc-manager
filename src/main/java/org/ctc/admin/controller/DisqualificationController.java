package org.ctc.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.DisqualificationForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.RaceDisqualificationService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/races/{raceId}/results/{resultId}/disqualification")
@RequiredArgsConstructor
public class DisqualificationController {
    private final RaceDisqualificationService disqualifications;
    private final Environment environment;

    @InitBinder("disqualificationForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("disqualified", "expectedRevision", "fingerprint", "generation", "reason"); }

    @GetMapping
    public String view(@PathVariable UUID raceId, @PathVariable UUID resultId, Model model) { return populate(raceId, resultId, model); }

    @PostMapping
    public String save(@PathVariable UUID raceId, @PathVariable UUID resultId,
                       @Valid @ModelAttribute("disqualificationForm") DisqualificationForm form, BindingResult errors,
                       Principal principal, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                disqualifications.save(raceId, resultId, form.correction(), actor(principal));
                redirect.addFlashAttribute("successMessage", "Driver disqualification saved. Review manual team penalty totals. Regenerate affected graphics and explicitly publish corrected results.");
                return "redirect:/admin/races/" + raceId + "/results/" + resultId + "/disqualification";
            } catch (BusinessRuleException failure) {
                errors.reject("disqualification", failure.getMessage()); response.setStatus(HttpServletResponse.SC_CONFLICT);
            }
        }
        return populate(raceId, resultId, model);
    }

    private String populate(UUID raceId, UUID resultId, Model model) {
        var view = disqualifications.view(raceId, resultId);
        model.addAttribute("disqualification", view); model.addAttribute("pageTitle", "Driver disqualification");
        if (!model.containsAttribute("disqualificationForm")) model.addAttribute("disqualificationForm", DisqualificationForm.from(view));
        return "admin/driver-disqualification";
    }

    private String actor(Principal principal) {
        if (principal != null) return principal.getName();
        if (environment.acceptsProfiles(Profiles.of("dev", "local"))) return "development-manager";
        throw new BusinessRuleException("A trusted manager identity is required");
    }
}
