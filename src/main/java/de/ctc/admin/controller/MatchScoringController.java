package de.ctc.admin.controller;

import de.ctc.domain.model.MatchScoring;
import de.ctc.domain.repository.MatchScoringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
        model.addAttribute("scoring", new MatchScoring());
        return "admin/match-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("scoring", matchScoringRepository.findById(id).orElseThrow());
        return "admin/match-scoring-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute MatchScoring scoring, RedirectAttributes redirectAttributes) {
        if (scoring.getId() != null) {
            var existing = matchScoringRepository.findById(scoring.getId()).orElseThrow();
            existing.setName(scoring.getName());
            existing.setPointsWin(scoring.getPointsWin());
            existing.setPointsDraw(scoring.getPointsDraw());
            existing.setPointsLoss(scoring.getPointsLoss());
            matchScoringRepository.save(existing);
        } else {
            matchScoringRepository.save(scoring);
        }

        log.info("Saved match scoring: {}", scoring.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Match-Scoring saved: " + scoring.getName());
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
                    "Kann nicht geloescht werden — wird noch von einer Season referenziert");
        }
        return "redirect:/admin/match-scorings";
    }
}
