package org.ctc.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.SwissRoundForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.SwissRoundService;
import org.ctc.domain.service.SwissRoundValidationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/admin/swiss-rounds") @RequiredArgsConstructor
public class SwissRoundController {
    private final SwissRoundService rounds;
    @InitBinder("roundForm") void limitRows(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(256);
        binder.setAllowedFields("phaseId","groupId","matchdayId","label","roundNumber","scope","generation",
                "rows[*].matchId","rows[*].homeTeamId","rows[*].awayTeamId","rows[*].bye","rows[*].remove");
    }
    @GetMapping public String workspace(@RequestParam UUID phaseId, @RequestParam(required=false) UUID groupId, Model model) {
        var view=rounds.workspace(phaseId,groupId); model.addAttribute("swiss",view);
        model.addAttribute("selectedSeasonId",view.seasonId()); model.addAttribute("pageTitle","Swiss rounds");
        return "admin/swiss-workspace";
    }
    @GetMapping("/edit") public String edit(@RequestParam UUID phaseId, @RequestParam(required=false) UUID groupId,
                                           @RequestParam(required=false) UUID matchdayId, Model model) {
        return populate(phaseId,groupId,matchdayId,null,model);
    }
    @PostMapping("/save") public String save(@Valid @ModelAttribute("roundForm") SwissRoundForm form, BindingResult errors,
                                            HttpServletResponse response, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                var saved=rounds.saveWithWarnings(form); redirect.addFlashAttribute("successMessage","Swiss round saved.");
                if (!saved.warnings().isEmpty()) redirect.addFlashAttribute("swissWarnings",saved.warnings());
                return destination(form.getPhaseId(),form.getGroupId())+"#round-"+saved.id();
            } catch (SwissRoundValidationException failure) {
                for (var issue:failure.issues()) errors.rejectValue(issue.field(),"round",issue.message());
                response.setStatus(HttpServletResponse.SC_CONFLICT);
            } catch (BusinessRuleException failure) {
                errors.reject("round",failure.getMessage()); response.setStatus(HttpServletResponse.SC_CONFLICT);
            }
        }
        if (form.getPhaseId()==null) throw new org.ctc.domain.exception.ValidationException("Choose a valid Swiss phase before editing a round.");
        return populate(form.getPhaseId(),form.getGroupId(),form.getMatchdayId(),form,model);
    }
    @PostMapping("/generate") public String generate(@RequestParam UUID phaseId, @RequestParam(required=false) UUID groupId,
                                                    @RequestParam String scope, @RequestParam String generation, RedirectAttributes redirect) {
        try { rounds.generate(phaseId,groupId,generation,scope); redirect.addFlashAttribute("successMessage","Swiss round generated."); }
        catch (BusinessRuleException | IllegalStateException failure) { redirect.addFlashAttribute("errorMessage",failure.getMessage()); }
        return destination(phaseId,groupId);
    }
    private String populate(UUID phaseId, UUID groupId, UUID dayId, SwissRoundForm rejected, Model model) {
        var view=rounds.editor(phaseId,groupId,dayId,rejected);
        model.addAttribute("editor",view); model.addAttribute("swiss",view.workspace());
        model.addAttribute("selectedSeasonId",view.workspace().seasonId()); model.addAttribute("pageTitle",dayId==null?"Create Swiss round":"Edit Swiss pairings");
        if (!model.containsAttribute("roundForm")) model.addAttribute("roundForm",view.form());
        return "admin/swiss-round-form";
    }
    private static String destination(UUID phaseId, UUID groupId) {
        return "redirect:/admin/swiss-rounds?phaseId="+phaseId+(groupId==null?"":"&groupId="+groupId);
    }
}
