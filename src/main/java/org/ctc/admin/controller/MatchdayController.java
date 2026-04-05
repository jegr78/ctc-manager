package org.ctc.admin.controller;

import org.ctc.admin.dto.CreateMatchdayRequest;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.service.MatchService;
import org.ctc.domain.service.MatchdayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final MatchdayOverviewGraphicService overviewGraphicService;
    private final MatchdayScheduleGraphicService scheduleGraphicService;
    private final MatchdayResultsGraphicService resultsGraphicService;
    private final MatchResultsGraphicService matchResultsGraphicService;
    private final MatchService matchService;

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
        var matchday = data.matchday();
        model.addAttribute("matchday", matchday);
        model.addAttribute("lineupsByTeam", data.lineupsByTeam());

        // Graphics button status
        var nonByeMatches = matchday.getMatches().stream().filter(m -> !m.isBye()).toList();
        model.addAttribute("hasMatches", !nonByeMatches.isEmpty());
        model.addAttribute("hasSchedule", nonByeMatches.stream()
                .anyMatch(m -> m.getRaces().stream().anyMatch(r -> r.getDateTime() != null)));
        long matchesWithDateTime = nonByeMatches.stream()
                .filter(m -> m.getRaces().stream().anyMatch(r -> r.getDateTime() != null)).count();
        model.addAttribute("scheduleMissingCount", nonByeMatches.size() - matchesWithDateTime);
        model.addAttribute("hasResults", nonByeMatches.stream()
                .anyMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null));
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

    // --- Graphic download endpoints ---

    @PostMapping("/{id}/download-overview")
    public ResponseEntity<byte[]> downloadOverview(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = overviewGraphicService.generateOverview(matchday);
            return buildPngResponse(png, matchday.getLabel(), "overview");
        } catch (Exception e) {
            log.error("Failed to generate overview graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/download-schedule")
    public ResponseEntity<byte[]> downloadSchedule(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = scheduleGraphicService.generateSchedule(matchday);
            return buildPngResponse(png, matchday.getLabel(), "schedule");
        } catch (Exception e) {
            log.error("Failed to generate schedule graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/download-results")
    public ResponseEntity<byte[]> downloadResults(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = resultsGraphicService.generateResults(matchday);
            return buildPngResponse(png, matchday.getLabel(), "results");
        } catch (Exception e) {
            log.error("Failed to generate results graphic for matchday {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{matchdayId}/matches/{matchId}/download-match-results")
    public ResponseEntity<byte[]> downloadMatchResults(@PathVariable UUID matchdayId, @PathVariable UUID matchId) {
        try {
            var match = matchService.getMatch(matchId);
            byte[] png = matchResultsGraphicService.generateMatchResults(match);
            String label = match.getHomeTeam().getShortName() + "-vs-" + match.getAwayTeam().getShortName();
            return buildPngResponse(png, label, "match-results");
        } catch (Exception e) {
            log.error("Failed to generate match results graphic for match {}", matchId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<byte[]> buildPngResponse(byte[] png, String matchdayLabel, String type) {
        String filename = matchdayLabel.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + type + ".png";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(png);
    }

    // --- JSON API endpoints ---

    @GetMapping("/by-season")
    @ResponseBody
    public List<MatchdayService.MatchdayData> matchdaysBySeason(@RequestParam UUID seasonId) {
        return matchdayService.getMatchdaysBySeason(seasonId);
    }

    @PostMapping("/create-inline")
    @ResponseBody
    public ResponseEntity<MatchdayService.MatchdayData> createInline(@Valid @RequestBody CreateMatchdayRequest request) {
        var dto = matchdayService.createInline(request.seasonId(), request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
