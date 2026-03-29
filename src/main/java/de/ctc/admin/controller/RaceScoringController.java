package de.ctc.admin.controller;

import de.ctc.domain.model.RaceScoring;
import de.ctc.domain.repository.RaceScoringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/race-scorings")
@RequiredArgsConstructor
public class RaceScoringController {

    private final RaceScoringRepository raceScoringRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scorings", raceScoringRepository.findAll());
        return "admin/race-scoring-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("scoring", new RaceScoring());
        return "admin/race-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("scoring", raceScoringRepository.findById(id).orElseThrow());
        return "admin/race-scoring-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute RaceScoring scoring, RedirectAttributes redirectAttributes) {
        if (!scoring.isValid()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Punktewerte muessen monoton fallend sein (gleiche Werte erlaubt)");
            return "redirect:/admin/race-scorings" + (scoring.getId() != null ? "/" + scoring.getId() + "/edit" : "/new");
        }

        if (scoring.getId() != null) {
            var existing = raceScoringRepository.findById(scoring.getId()).orElseThrow();
            existing.setName(scoring.getName());
            existing.setRacePoints(scoring.getRacePoints());
            existing.setQualiPoints(scoring.getQualiPoints());
            existing.setFastestLapPoints(scoring.getFastestLapPoints());
            raceScoringRepository.save(existing);
        } else {
            raceScoringRepository.save(scoring);
        }

        log.info("Saved race scoring: {}", scoring.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Race-Scoring saved: " + scoring.getName());
        return "redirect:/admin/race-scorings";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var scoring = raceScoringRepository.findById(id).orElseThrow();
        try {
            raceScoringRepository.delete(scoring);
            raceScoringRepository.flush();
            log.info("Deleted race scoring: {}", scoring.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Race-Scoring deleted");
        } catch (Exception e) {
            log.warn("Cannot delete race scoring {}: {}", scoring.getName(), e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Kann nicht geloescht werden — wird noch von einer Season referenziert");
        }
        return "redirect:/admin/race-scorings";
    }
}
