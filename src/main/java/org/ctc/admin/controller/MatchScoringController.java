package org.ctc.admin.controller;

import org.ctc.admin.dto.MatchScoringForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.MatchScoringService;
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
@RequestMapping("/admin/match-scorings")
@RequiredArgsConstructor
public class MatchScoringController {

    private final MatchScoringService matchScoringService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scorings", matchScoringService.findAll());
        return "admin/match-scoring-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("matchScoringForm", new MatchScoringForm());
        return "admin/match-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var scoring = matchScoringService.findById(id);
        var form = new MatchScoringForm();
        form.setId(scoring.getId());
        form.setName(scoring.getName());
        form.setPointsWin(scoring.getPointsWin());
        form.setPointsDraw(scoring.getPointsDraw());
        form.setPointsLoss(scoring.getPointsLoss());
        model.addAttribute("matchScoringForm", form);
        return "admin/match-scoring-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("matchScoringForm") MatchScoringForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/match-scoring-form";
        }
        try {
            matchScoringService.save(form);
            redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring saved: " + form.getName());
            return "redirect:/admin/match-scorings";
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/match-scorings";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            matchScoringService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring deleted");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/match-scorings";
    }
}
