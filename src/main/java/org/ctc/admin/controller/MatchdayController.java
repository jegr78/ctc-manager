package org.ctc.admin.controller;

import org.ctc.admin.dto.CreateMatchdayRequest;
import org.ctc.admin.dto.MatchdayDto;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.service.MatchdayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/matchdays")
@RequiredArgsConstructor
public class MatchdayController {

    private final MatchdayService matchdayService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        var data = matchdayService.getMatchdayList(seasonId);
        model.addAttribute("matchdays", data.matchdays());
        if (data.selectedSeasonId() != null) {
            model.addAttribute("selectedSeasonId", data.selectedSeasonId());
        }
        model.addAttribute("seasons", data.seasons());
        return "admin/matchdays";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var data = matchdayService.getMatchdayDetail(id);
        model.addAttribute("matchday", data.matchday());
        model.addAttribute("lineupsByTeam", data.lineupsByTeam());
        return "admin/matchday-detail";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var matchday = new Matchday();
        if (seasonId != null) {
            matchday.setSeason(matchdayService.findSeasonById(seasonId));
        }
        model.addAttribute("matchday", matchday);
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var data = matchdayService.getMatchdayDetail(id);
        model.addAttribute("matchday", data.matchday());
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Matchday matchday,
                       BindingResult result,
                       @RequestParam UUID seasonId,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seasons", matchdayService.getAllSeasons());
            return "admin/matchday-form";
        }
        var saved = matchdayService.saveMatchday(matchday.getLabel(), matchday.getSortIndex(), seasonId, matchday.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + saved.getLabel());
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var seasonId = matchdayService.deleteMatchday(id);
        redirectAttributes.addFlashAttribute("successMessage", "Matchday deleted");
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }

    // --- JSON API endpoints ---

    @GetMapping("/by-season")
    @ResponseBody
    public List<MatchdayDto> matchdaysBySeason(@RequestParam String seasonName) {
        return matchdayService.getMatchdaysBySeason(seasonName);
    }

    @PostMapping("/create-inline")
    @ResponseBody
    public ResponseEntity<MatchdayDto> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
        var dto = matchdayService.createInline(request.seasonName(), request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
