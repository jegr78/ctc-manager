package org.ctc.admin.controller;

import org.ctc.admin.dto.CreateMatchdayRequest;
import org.ctc.admin.dto.MatchdayForm;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
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

import java.io.IOException;
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

        // Graphics button status — computed by service
        model.addAttribute("hasMatches", data.hasMatches());
        model.addAttribute("hasSchedule", data.hasSchedule());
        model.addAttribute("scheduleMissingCount", data.scheduleMissingCount());
        model.addAttribute("hasResults", data.hasResults());
        model.addAttribute("pageTitle", "Matchday: " + matchday.getLabel());
        return "admin/matchday-detail";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var form = new MatchdayForm();
        if (seasonId != null) {
            form.setSeasonId(seasonId);
            model.addAttribute("season", matchdayService.findSeasonById(seasonId));
        }
        model.addAttribute("form", form);
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var matchday = matchdayService.getMatchdayDetail(id).matchday();
        var form = new MatchdayForm();
        form.setId(matchday.getId());
        form.setLabel(matchday.getLabel());
        form.setSortIndex(matchday.getSortIndex());
        form.setSeasonId(matchday.getSeason().getId());
        model.addAttribute("form", form);
        model.addAttribute("season", matchday.getSeason());
        model.addAttribute("seasons", matchdayService.getAllSeasons());
        return "admin/matchday-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seasons", matchdayService.getAllSeasons());
            if (form.getSeasonId() != null) {
                model.addAttribute("season", matchdayService.findSeasonById(form.getSeasonId()));
            }
            return "admin/matchday-form";
        }
        var saved = matchdayService.saveMatchday(
                form.getLabel(), form.getSortIndex(), form.getSeasonId(), form.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Matchday saved: " + saved.getLabel());
        String redirectUrl = "/admin/matchdays";
        if (form.getSeasonId() != null) {
            redirectUrl += "?seasonId=" + form.getSeasonId();
        }
        return "redirect:" + redirectUrl;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var seasonId = matchdayService.deleteMatchday(id);
        redirectAttributes.addFlashAttribute("successMessage", "Matchday deleted");
        return "redirect:/admin/matchdays?seasonId=" + seasonId;
    }

    @PostMapping("/{id}/download-overview")
    public ResponseEntity<byte[]> downloadOverview(@PathVariable UUID id) {
        try {
            var matchday = matchdayService.getMatchdayDetail(id).matchday();
            byte[] png = overviewGraphicService.generateOverview(matchday);
            return buildPngResponse(png, matchday.getLabel(), "overview");
        } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
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
