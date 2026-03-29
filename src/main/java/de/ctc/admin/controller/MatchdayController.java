package de.ctc.admin.controller;

import de.ctc.admin.dto.CreateMatchdayRequest;
import de.ctc.admin.dto.MatchdayDto;
import de.ctc.domain.model.Matchday;
import de.ctc.domain.repository.MatchdayLineupRepository;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.SeasonRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayController {

    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;
    private final MatchdayLineupRepository matchdayLineupRepository;

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

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var matchday = matchdayRepository.findById(id).orElseThrow();
        var lineups = matchdayLineupRepository.findByMatchdayId(id);
        model.addAttribute("matchday", matchday);
        model.addAttribute("lineups", lineups);
        return "admin/matchday-detail";
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
        matchday.setSeason(seasonRepository.findById(seasonId).orElse(null));
        if (result.hasErrors()) {
            model.addAttribute("seasons", seasonRepository.findAll());
            return "admin/matchday-form";
        }
        if (matchday.getId() != null) {
            var existing = matchdayRepository.findById(matchday.getId()).orElseThrow();
            existing.setLabel(matchday.getLabel());
            existing.setSortIndex(matchday.getSortIndex());
            existing.setSeason(seasonRepository.findById(seasonId).orElseThrow());
            matchdayRepository.save(existing);
        } else {
            matchday.setSeason(seasonRepository.findById(seasonId).orElseThrow());
            matchdayRepository.save(matchday);
        }
        log.info("Saved matchday: {} (season {})", matchday.getLabel(), seasonId);
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

    // --- JSON API endpoints ---

    @GetMapping("/by-season")
    @ResponseBody
    public List<MatchdayDto> matchdaysBySeason(@RequestParam String seasonName) {
        return seasonRepository.findByName(seasonName)
                .map(season -> matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId()).stream()
                        .map(md -> new MatchdayDto(md.getId(), md.getLabel(), md.getSortIndex()))
                        .toList())
                .orElse(List.of());
    }

    @PostMapping("/create-inline")
    @ResponseBody
    public ResponseEntity<MatchdayDto> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
        var season = seasonRepository.findByName(request.seasonName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Season not found: " + request.seasonName()));

        var existingMatchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        boolean duplicateLabel = existingMatchdays.stream()
                .anyMatch(md -> md.getLabel().equals(request.label()));
        if (duplicateLabel) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Matchday label already exists in this season: " + request.label());
        }

        int nextSortIndex = existingMatchdays.stream()
                .mapToInt(Matchday::getSortIndex)
                .max()
                .orElse(0) + 1;

        var matchday = matchdayRepository.save(new Matchday(season, request.label(), nextSortIndex));
        log.info("Created matchday inline: {} (season {})", matchday.getLabel(), season.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MatchdayDto(matchday.getId(), matchday.getLabel(), matchday.getSortIndex()));
    }
}
