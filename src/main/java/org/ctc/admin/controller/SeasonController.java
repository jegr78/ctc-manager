package org.ctc.admin.controller;

import org.ctc.admin.dto.MatchdayGeneratorForm;
import org.ctc.admin.dto.SeasonForm;
import org.ctc.domain.service.MatchdayGeneratorService;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SwissPairingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonManagementService seasonManagementService;
    private final SwissPairingService swissPairingService;
    private final MatchdayGeneratorService matchdayGeneratorService;

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var data = seasonManagementService.getDetailData(id);
        model.addAttribute("season", data.season());
        model.addAttribute("playoff", data.playoff());
        model.addAttribute("isSwiss", data.isSwiss());
        model.addAttribute("canGenerate", data.canGenerate());
        model.addAttribute("availableTeams", seasonManagementService.getAvailableTeamsForReplacement(id));
        return "admin/season-detail";
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("seasons", seasonManagementService.findAll());
        return "admin/seasons";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("seasonForm", new SeasonForm());
        addScoringLists(model);
        return "admin/season-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var data = seasonManagementService.getEditFormData(id);
        var season = data.season();
        var form = new SeasonForm();
        form.setId(season.getId());
        form.setName(season.getName());
        form.setYear(season.getYear());
        form.setNumber(season.getNumber());
        form.setDescription(season.getDescription());
        form.setStartDate(season.getStartDate());
        form.setEndDate(season.getEndDate());
        form.setActive(season.isActive());
        form.setFormat(season.getFormat());
        form.setTotalRounds(season.getTotalRounds());
        form.setLegs(season.getLegs());
        form.setEventDurationMinutes(season.getEventDurationMinutes());
        model.addAttribute("seasonForm", form);
        model.addAttribute("season", season);
        model.addAttribute("allTeams", data.allTeams());
        model.addAttribute("allCars", data.allCars());
        model.addAttribute("allTracks", data.allTracks());
        model.addAttribute("allRaceScorings", data.allRaceScorings());
        model.addAttribute("allMatchScorings", data.allMatchScorings());
        return "admin/season-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result,
                       @RequestParam UUID raceScoring,
                       @RequestParam UUID matchScoring,
                       RedirectAttributes redirectAttributes, Model model) {
        if (result.hasErrors()) {
            addScoringLists(model);
            return "admin/season-form";
        }
        var season = seasonManagementService.save(form.getId(), form.getName(), form.getYear(),
                form.getNumber(), form.getDescription(), form.getStartDate(), form.getEndDate(),
                form.isActive(), form.getFormat(), form.getTotalRounds(), form.getLegs(),
                form.getEventDurationMinutes(), raceScoring, matchScoring);
        redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
        return "redirect:/admin/seasons";
    }

    @PostMapping("/{id}/add-team")
    public String addTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                          RedirectAttributes redirectAttributes) {
        String teamName = seasonManagementService.addTeamToSeason(id, teamId);
        redirectAttributes.addFlashAttribute("successMessage", "Team added: " + teamName);
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/remove-team")
    public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                             RedirectAttributes redirectAttributes) {
        try {
            seasonManagementService.removeTeamFromSeason(id, teamId);
            redirectAttributes.addFlashAttribute("successMessage", "Team removed");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/update-season-team")
    public String updateSeasonTeam(@PathVariable UUID id,
                                   @RequestParam UUID seasonTeamId,
                                   @RequestParam(required = false) Integer rating,
                                   @RequestParam(required = false) String primaryColor,
                                   @RequestParam(required = false) String secondaryColor,
                                   @RequestParam(required = false) String accentColor,
                                   @RequestParam(required = false) MultipartFile logoOverride,
                                   RedirectAttributes redirectAttributes) {
        try {
            String teamName = seasonManagementService.updateSeasonTeam(
                    seasonTeamId, rating, primaryColor, secondaryColor, accentColor, logoOverride);
            redirectAttributes.addFlashAttribute("successMessage", "Updated: " + teamName);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
        }
        return "redirect:/admin/seasons/" + id;
    }

    @PostMapping("/{id}/replace-team")
    public String replaceTeam(@PathVariable UUID id,
                              @RequestParam UUID predecessorTeamId,
                              @RequestParam UUID successorTeamId,
                              @RequestParam LocalDate replacedAt,
                              RedirectAttributes redirectAttributes) {
        try {
            String result = seasonManagementService.replaceTeam(id, predecessorTeamId, successorTeamId, replacedAt);
            redirectAttributes.addFlashAttribute("successMessage", "Team replaced: " + result);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/seasons/" + id;
    }

    @PostMapping("/{id}/cars/add")
    public String addCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
                          RedirectAttributes redirectAttributes) {
        int added = seasonManagementService.addCarsToSeason(id, carIds);
        redirectAttributes.addFlashAttribute("successMessage", added + " car(s) added to pool");
        return "redirect:/admin/seasons/" + id + "/edit#carPool";
    }

    @PostMapping("/{id}/cars/remove")
    public String removeCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
                             RedirectAttributes redirectAttributes) {
        int removed = seasonManagementService.removeCarsFromSeason(id, carIds);
        redirectAttributes.addFlashAttribute("successMessage", removed + " car(s) removed from pool");
        return "redirect:/admin/seasons/" + id + "/edit#carPool";
    }

    @PostMapping("/{id}/tracks/add")
    public String addTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
                            RedirectAttributes redirectAttributes) {
        int added = seasonManagementService.addTracksToSeason(id, trackIds);
        redirectAttributes.addFlashAttribute("successMessage", added + " track(s) added to pool");
        return "redirect:/admin/seasons/" + id + "/edit#trackPool";
    }

    @PostMapping("/{id}/tracks/remove")
    public String removeTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
                               RedirectAttributes redirectAttributes) {
        int removed = seasonManagementService.removeTracksFromSeason(id, trackIds);
        redirectAttributes.addFlashAttribute("successMessage", removed + " track(s) removed from pool");
        return "redirect:/admin/seasons/" + id + "/edit#trackPool";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        String name = seasonManagementService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Season deleted: " + name);
        return "redirect:/admin/seasons";
    }

    @GetMapping("/{id}/swiss")
    public String swissRounds(@PathVariable UUID id, Model model) {
        var data = seasonManagementService.getSwissRoundData(id);
        model.addAttribute("season", data.season());
        model.addAttribute("raceScores", data.raceScores());
        model.addAttribute("currentRound", swissPairingService.getCurrentRound(id));
        model.addAttribute("canGenerateNext",
                swissPairingService.isCurrentRoundComplete(id)
                && (data.season().getTotalRounds() == null || data.season().getMatchdays().size() < data.season().getTotalRounds()));
        return "admin/swiss-rounds";
    }

    @PostMapping("/{id}/swiss/generate")
    public String generateSwissRound(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            swissPairingService.generateNextRound(id);
            redirectAttributes.addFlashAttribute("successMessage", "Next round generated successfully");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/seasons/" + id + "/swiss";
    }

    @GetMapping("/{id}/generate")
    public String generateForm(@PathVariable UUID id, Model model) {
        var formData = matchdayGeneratorService.getFormData(id);
        var season = formData.season();
        var form = new MatchdayGeneratorForm();
        form.setNumberOfRounds(season.getTotalRounds() != null ? season.getTotalRounds() : formData.optimalRounds());
        model.addAttribute("season", season);
        model.addAttribute("generatorForm", form);
        model.addAttribute("teamCount", formData.teamCount());
        model.addAttribute("optimalRounds", formData.optimalRounds());
        return "admin/matchday-generator";
    }

    @PostMapping("/{id}/generate")
    public String generate(@PathVariable UUID id,
                           @Valid @ModelAttribute MatchdayGeneratorForm form,
                           BindingResult result,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid input: number of rounds must be at least 1");
            return "redirect:/admin/seasons/" + id + "/generate";
        }
        try {
            matchdayGeneratorService.generate(id, form.getNumberOfRounds(), form.isHomeAndAway());
            redirectAttributes.addFlashAttribute("successMessage", "Matchdays generated successfully");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/seasons/" + id + "/generate";
        }
        return "redirect:/admin/seasons/" + id;
    }

    private void addScoringLists(Model model) {
        model.addAttribute("allRaceScorings", seasonManagementService.getAllRaceScorings());
        model.addAttribute("allMatchScorings", seasonManagementService.getAllMatchScorings());
    }
}
