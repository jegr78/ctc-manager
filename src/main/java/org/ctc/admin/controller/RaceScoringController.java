package org.ctc.admin.controller;

import org.ctc.admin.dto.RaceScoringForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.RaceScoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/race-scorings")
@RequiredArgsConstructor
public class RaceScoringController {

    private final RaceScoringService raceScoringService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scorings", raceScoringService.findAll());
        return "admin/race-scoring-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("raceScoringForm", new RaceScoringForm());
        return "admin/race-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var scoring = raceScoringService.findById(id);
        var form = new RaceScoringForm();
        form.setId(scoring.getId());
        form.setName(scoring.getName());
        form.setRacePoints(scoring.getRacePoints());
        form.setQualiPoints(scoring.getQualiPoints());
        form.setFastestLapPoints(scoring.getFastestLapPoints());
        model.addAttribute("raceScoringForm", form);
        return "admin/race-scoring-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("raceScoringForm") RaceScoringForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/race-scoring-form";
        }
        try {
            raceScoringService.save(form);
            redirectAttributes.addFlashAttribute("successMessage", "Race-Scoring saved: " + form.getName());
            return "redirect:/admin/race-scorings";
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/race-scorings";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            raceScoringService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Race-Scoring deleted");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/race-scorings";
    }
}
