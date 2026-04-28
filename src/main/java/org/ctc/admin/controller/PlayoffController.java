package org.ctc.admin.controller;

import org.ctc.admin.dto.PlayoffForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.admin.dto.SeedForm;
import org.ctc.admin.service.PlayoffRoundOverviewGraphicService;
import org.ctc.admin.service.PlayoffRoundResultsGraphicService;
import org.ctc.admin.service.PlayoffRoundScheduleGraphicService;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.PlayoffSeedingService;
import org.ctc.domain.service.PlayoffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/playoffs")
@RequiredArgsConstructor
public class PlayoffController {

    private final PlayoffService playoffService;
    private final PlayoffBracketViewService playoffBracketViewService;
    private final PlayoffSeedingService playoffSeedingService;
    private final PlayoffRoundOverviewGraphicService roundOverviewGraphicService;
    private final PlayoffRoundScheduleGraphicService roundScheduleGraphicService;
    private final PlayoffRoundResultsGraphicService roundResultsGraphicService;

    @GetMapping
    public String list(@RequestParam(required = false) UUID seasonId, Model model) {
        var data = playoffService.getPlayoffListData(seasonId);
        model.addAttribute("seasons", data.allSeasons());
        if (data.selectedSeasonId() != null) {
            model.addAttribute("selectedSeasonId", data.selectedSeasonId());
        }
        if (data.playoff() != null) {
            model.addAttribute("playoff", data.playoff());
            model.addAttribute("bracket", data.bracketView());
        }
        return "admin/playoff-bracket";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) UUID seasonId, Model model) {
        var form = new PlayoffForm();
        if (seasonId != null) {
            form.setSeasonId(seasonId);
        }
        var data = playoffService.getPlayoffListData(null);
        model.addAttribute("playoffForm", form);
        model.addAttribute("seasons", data.allSeasons());
        return "admin/playoff-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("playoffForm") PlayoffForm form, BindingResult bindingResult,
                       Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            var data = playoffService.getPlayoffListData(null);
            model.addAttribute("seasons", data.allSeasons());
            return "admin/playoff-form";
        }
        try {
            var playoff = playoffService.createPlayoff(
                    form.getSeasonId(), form.getName(), form.getNumberOfTeams(),
                    form.getStartDate(), form.getEndDate(), form.getEventDurationMinutes());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Playoff created: " + playoff.getName());
            return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
        } catch (IllegalArgumentException | IllegalStateException | BusinessRuleException e) {
            // BusinessRuleException added Phase 58 D-19: PlayoffService.createPlayoff now throws
            // BusinessRuleException for the duplicate-playoff case (replacing IllegalArgumentException)
            // for D-03 consistency. Caught here to preserve the redirect-with-flash UX.
            log.warn("Error creating playoff: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/new?seasonId=" + form.getSeasonId();
        }
    }

    @PostMapping("/round/{roundId}/set-legs")
    public String setRoundLegs(@PathVariable UUID roundId, @RequestParam int bestOfLegs,
                               RedirectAttributes redirectAttributes) {
        var round = playoffService.setRoundLegs(roundId, bestOfLegs);
        redirectAttributes.addFlashAttribute("successMessage",
                round.getLabel() + ": " + bestOfLegs + " Leg(s)");
        return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForRound(roundId);
    }

    @PostMapping("/{id}/add-season")
    public String addSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                            RedirectAttributes redirectAttributes) {
        playoffService.addSeasonToPlayoff(id, seasonId);
        redirectAttributes.addFlashAttribute("successMessage", "Season linked");
        return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
    }

    @PostMapping("/{id}/remove-season")
    public String removeSeason(@PathVariable UUID id, @RequestParam UUID seasonId,
                               RedirectAttributes redirectAttributes) {
        playoffService.removeSeasonFromPlayoff(id, seasonId);
        redirectAttributes.addFlashAttribute("successMessage", "Season removed");
        return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
    }

    @GetMapping("/{id}/seed")
    public String seed(@PathVariable UUID id, Model model) {
        var data = playoffSeedingService.getSeedingData(id);
        var form = new SeedForm();
        form.setPlayoffId(id);

        model.addAttribute("seedForm", form);
        model.addAttribute("playoff", data.playoff());
        model.addAttribute("bracket", data.bracketView());
        model.addAttribute("firstRound", data.firstRound());
        model.addAttribute("teams", data.teams());
        model.addAttribute("seededTeamIds", data.seededTeamIds());
        model.addAttribute("seedNumbers", data.seedNumbers());
        return "admin/playoff-seed";
    }

    @PostMapping("/{id}/auto-seed")
    public String autoSeed(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            playoffSeedingService.autoSeedBracket(id);
            redirectAttributes.addFlashAttribute("successMessage", "Bracket auto-seeded");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/playoffs/" + id + "/seed";
    }

    @PostMapping("/{id}/seed")
    public String saveSeed(@PathVariable UUID id, @ModelAttribute SeedForm form,
                           RedirectAttributes redirectAttributes) {
        var seeds = form.getSeeds().stream()
                .map(e -> new PlayoffSeedingService.SeedEntry(e.getMatchupId(), e.getSlot(), e.getTeamId(), e.getSeedNumber()))
                .toList();
        playoffSeedingService.saveSeed(id, seeds);
        redirectAttributes.addFlashAttribute("successMessage", "Seeding saved");
        return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForPlayoff(id);
    }

    @GetMapping("/matchup/{matchupId}")
    public String matchupDetail(@PathVariable UUID matchupId, Model model) {
        var data = playoffService.getMatchupDetail(matchupId);
        model.addAttribute("matchup", data.matchup());
        model.addAttribute("legs", data.legs());
        model.addAttribute("playoff", data.playoff());
        return "admin/playoff-matchup";
    }

    @PostMapping("/matchup/{matchupId}/add-race")
    public String addRace(@PathVariable UUID matchupId, RedirectAttributes redirectAttributes) {
        try {
            playoffService.addRaceToMatchup(matchupId, null, null, null);
            redirectAttributes.addFlashAttribute("successMessage", "Leg added");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/playoffs/matchup/" + matchupId;
    }

    @PostMapping("/matchup/{matchupId}/determine-winner")
    public String determineWinner(@PathVariable UUID matchupId, RedirectAttributes redirectAttributes) {
        try {
            playoffService.determineWinner(matchupId);
            var data = playoffService.getMatchupDetail(matchupId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Winner: " + data.matchup().getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForMatchup(matchupId);
        } catch (IllegalStateException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }

    @PostMapping("/matchup/{matchupId}/set-winner")
    public String setWinnerManually(@PathVariable UUID matchupId,
                                    @RequestParam UUID winnerTeamId,
                                    RedirectAttributes redirectAttributes) {
        try {
            playoffService.setWinnerManually(matchupId, winnerTeamId);
            var data = playoffService.getMatchupDetail(matchupId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Winner set manually: " + data.matchup().getWinner().getShortName());
            return "redirect:/admin/playoffs?seasonId=" + playoffService.getSeasonIdForMatchup(matchupId);
        } catch (EntityNotFoundException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }

    // --- Round graphic download endpoints ---

    @PostMapping("/round/{roundId}/download-overview")
    public ResponseEntity<byte[]> downloadRoundOverview(@PathVariable UUID roundId) {
        try {
            PlayoffRound round = playoffService.findRoundById(roundId);
            byte[] png = roundOverviewGraphicService.generateOverview(round);
            return buildPngResponse(png, round.getLabel(), "overview");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate overview graphic for playoff round {}", roundId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/round/{roundId}/download-schedule")
    public ResponseEntity<byte[]> downloadRoundSchedule(@PathVariable UUID roundId) {
        try {
            PlayoffRound round = playoffService.findRoundById(roundId);
            byte[] png = roundScheduleGraphicService.generateSchedule(round);
            return buildPngResponse(png, round.getLabel(), "schedule");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate schedule graphic for playoff round {}", roundId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/round/{roundId}/download-results")
    public ResponseEntity<byte[]> downloadRoundResults(@PathVariable UUID roundId) {
        try {
            PlayoffRound round = playoffService.findRoundById(roundId);
            byte[] png = roundResultsGraphicService.generateResults(round);
            return buildPngResponse(png, round.getLabel(), "results");
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate results graphic for playoff round {}", roundId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<byte[]> buildPngResponse(byte[] png, String label, String type) {
        String filename = label.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + type + ".png";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(png);
    }
}
