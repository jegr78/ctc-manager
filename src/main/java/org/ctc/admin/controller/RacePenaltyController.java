package org.ctc.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.RacePenaltyForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.RacePenaltyService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/races/{raceId}/penalties")
@RequiredArgsConstructor
public class RacePenaltyController {
    private final RacePenaltyService penalties;
    private final Environment environment;

    @InitBinder("penaltyForm")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("homeTeamId", "awayTeamId", "homePenalty", "awayPenalty", "expectedRevision",
                "fingerprint", "generation", "reason");
    }

    @GetMapping
    public String view(@PathVariable UUID raceId, Model model) { return populate(raceId, model); }

    @PostMapping
    public String save(@PathVariable UUID raceId, @Valid @ModelAttribute("penaltyForm") RacePenaltyForm form,
                       BindingResult errors, Principal principal, HttpServletResponse response,
                       Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                penalties.save(raceId, form.correction(), actor(principal));
                redirect.addFlashAttribute("successMessage", "Team penalty points saved. Regenerate affected graphics and explicitly publish the corrected result.");
                return "redirect:/admin/races/" + raceId + "/penalties";
            } catch (BusinessRuleException failure) {
                errors.reject("penalty", failure.getMessage());
                response.setStatus(HttpServletResponse.SC_CONFLICT);
            }
        }
        return populate(raceId, model);
    }

    private String populate(UUID raceId, Model model) {
        var view = penalties.view(raceId);
        var race = view.race();
        boolean correctable = !race.getResults().isEmpty() && !race.isBye()
                && (race.getMatch() == null || race.getMatch().getWalkoverTeam() == null)
                && (race.getPlayoffMatchup() == null || !race.getPlayoffMatchup().isBye() && race.getPlayoffMatchup().getWalkoverTeam() == null && !race.getPlayoffMatchup().isComplete());
        model.addAttribute("penalty", view);
        model.addAttribute("canCorrect", correctable);
        model.addAttribute("pageTitle", "Team penalty points");
        if (!model.containsAttribute("penaltyForm")) model.addAttribute("penaltyForm", RacePenaltyForm.from(view));
        return "admin/race-penalties";
    }

    private String actor(Principal principal) {
        if (principal != null) return principal.getName();
        if (environment.acceptsProfiles(Profiles.of("dev", "local"))) return "development-manager";
        throw new BusinessRuleException("A trusted manager identity is required");
    }
}
