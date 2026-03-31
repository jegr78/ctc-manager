package de.ctc.admin.controller;

import de.ctc.admin.dto.MatchScoringForm;
import de.ctc.domain.model.MatchScoring;
import de.ctc.domain.repository.MatchScoringRepository;
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

    private final MatchScoringRepository matchScoringRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scorings", matchScoringRepository.findAll());
        return "admin/match-scoring-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("matchScoringForm", new MatchScoringForm());
        return "admin/match-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var scoring = matchScoringRepository.findById(id).orElseThrow();
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
        if (form.getId() != null) {
            var existing = matchScoringRepository.findById(form.getId()).orElseThrow();
            existing.setName(form.getName());
            existing.setPointsWin(form.getPointsWin());
            existing.setPointsDraw(form.getPointsDraw());
            existing.setPointsLoss(form.getPointsLoss());
            matchScoringRepository.save(existing);
        } else {
            var scoring = new MatchScoring(form.getName(), form.getPointsWin(), form.getPointsDraw(), form.getPointsLoss());
            matchScoringRepository.save(scoring);
        }

        log.info("Saved match scoring: {}", form.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring saved: " + form.getName());
        return "redirect:/admin/match-scorings";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var scoring = matchScoringRepository.findById(id).orElseThrow();
        try {
            matchScoringRepository.delete(scoring);
            matchScoringRepository.flush();
            log.info("Deleted match scoring: {}", scoring.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring deleted");
        } catch (Exception e) {
            log.warn("Cannot delete match scoring {}: {}", scoring.getName(), e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete — still referenced by a season");
        }
        return "redirect:/admin/match-scorings";
    }
}
