package org.ctc.gt7sync.environment;

import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.TrackEnvironmentImportForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/admin/gt7-sync/environment") @RequiredArgsConstructor @Slf4j
public class TrackEnvironmentImportController {
    private final TrackEnvironmentImportService imports;
    @InitBinder("selection") void fields(WebDataBinder binder) {
        binder.setAllowedFields("previewId","selectedTracks","selectedTracks[*]","_csrf");
        binder.setAutoGrowCollectionLimit(256);
        // Spring also binds HTTP headers by default; this form accepts explicit parameters only.
        if(binder instanceof org.springframework.web.servlet.support.ExtendedServletRequestDataBinder requestBinder)
            requestBinder.setHeaderPredicate(name -> false);
    }
    @GetMapping public String preview(Principal principal,Model model,RedirectAttributes redirect) {
        try { return show(imports.bundled(principal),model); }
        catch(IOException failure) { return unavailable(failure,redirect); }
    }
    @PostMapping("/refresh") public String refresh(Principal principal,Model model,RedirectAttributes redirect) {
        try { return show(imports.refresh(principal),model); }
        catch(IOException failure) { return unavailable(failure,redirect); }
    }
    @PostMapping("/apply") public String apply(@Valid @ModelAttribute("selection") TrackEnvironmentImportForm form,BindingResult errors,
                                               Principal principal,RedirectAttributes redirect) {
        if(errors.hasErrors() || errors.getSuppressedFields().length>0) {
            redirect.addFlashAttribute("errorMessage","Invalid layout selection. Load and review the preview again.");
        } else {
            try { redirect.addFlashAttribute("successMessage",imports.apply(form.getPreviewId(),form.getSelectedTracks(),principal)+" layout environments imported"); }
            catch(BusinessRuleException failure) { redirect.addFlashAttribute("errorMessage",failure.getMessage()); }
        }
        return "redirect:/admin/gt7-sync/environment";
    }
    private String show(TrackEnvironmentImportService.Preview preview,Model model) {
        model.addAttribute("preview",preview);model.addAttribute("sourceUrl",GtPlanetEnvironmentSource.URL);
        model.addAttribute("statuses",TrackEnvironmentImportService.Status.values());
        return "admin/track-environment-preview";
    }
    private String unavailable(IOException failure,RedirectAttributes redirect) {
        log.warn("Layout environment reference could not be loaded",failure);
        redirect.addFlashAttribute("errorMessage","Could not validate the layout reference. Saved settings are unchanged. Use the bundled reference or try again later.");
        return "redirect:/admin/gt7-sync";
    }
}
