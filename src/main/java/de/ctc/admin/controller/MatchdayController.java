package de.ctc.admin.controller;

import de.ctc.domain.model.Matchday;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.SeasonRepository;
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
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayController {

    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        if (seasonId != null) {
            model.addAttribute("matchdays", matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId));
            model.addAttribute("selectedSeasonId", seasonId);
        } else {
            var activeSeason = seasonRepository.findByActiveTrue();
            if (activeSeason.isPresent()) {
                model.addAttribute("matchdays", matchdayRepository.findBySeasonIdOrderBySortIndexAsc(activeSeason.get().getId()));
                model.addAttribute("selectedSeasonId", activeSeason.get().getId());
            } else {
                model.addAttribute("matchdays", matchdayRepository.findAll());
            }
        }
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/matchdays";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var matchday = new Matchday();
        if (seasonId != null) {
            matchday.setSeason(seasonRepository.findById(seasonId).orElse(null));
        }
        model.addAttribute("matchday", matchday);
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/matchday-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("matchday", matchdayRepository.findById(id).orElseThrow());
        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/matchday-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Matchday matchday,
                       BindingResult result,
                       @RequestParam UUID seasonId,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seasons", seasonRepository.findAll());
            return "admin/matchday-form";
        }
        matchday.setSeason(seasonRepository.findById(seasonId).orElseThrow());
        matchdayRepository.save(matchday);
        log.info("Saved matchday: {} (season {})", matchday.getLabel(), matchday.getSeason().getName());
        redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + matchday.getLabel());
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var matchday = matchdayRepository.findById(id).orElseThrow();
        var seasonId = matchday.getSeason().getId();
        matchdayRepository.delete(matchday);
        log.info("Deleted matchday: {}", matchday.getLabel());
        redirectAttributes.addFlashAttribute("successMessage", "Matchday deleted: " + matchday.getLabel());
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }
}
