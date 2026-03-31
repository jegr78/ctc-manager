package de.ctc.admin.controller;

import de.ctc.admin.dto.RaceScoringForm;
import de.ctc.domain.model.RaceScoring;
import de.ctc.domain.repository.RaceScoringRepository;
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

    private final RaceScoringRepository raceScoringRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scorings", raceScoringRepository.findAll());
        return "admin/race-scoring-list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("raceScoringForm", new RaceScoringForm());
        return "admin/race-scoring-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var scoring = raceScoringRepository.findById(id).orElseThrow();
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

        // Map form to entity for validation
        var scoring = new RaceScoring(form.getName(), form.getRacePoints(), form.getQualiPoints(), form.getFastestLapPoints());
        scoring.setId(form.getId());

        if (!scoring.isValid()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Points must be monotonically decreasing (equal values allowed)");
            return "redirect:/admin/race-scorings" + (form.getId() != null ? "/" + form.getId() + "/edit" : "/new");
        }

        if (form.getId() != null) {
            var existing = raceScoringRepository.findById(form.getId()).orElseThrow();
            existing.setName(form.getName());
            existing.setRacePoints(form.getRacePoints());
            existing.setQualiPoints(form.getQualiPoints());
            existing.setFastestLapPoints(form.getFastestLapPoints());
            raceScoringRepository.save(existing);
        } else {
            raceScoringRepository.save(scoring);
        }

        log.info("Saved race scoring: {}", form.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Race-Scoring saved: " + form.getName());
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
                    "Cannot delete — still referenced by a season");
        }
        return "redirect:/admin/race-scorings";
    }
}
