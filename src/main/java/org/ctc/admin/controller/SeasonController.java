package org.ctc.admin.controller;

import org.ctc.admin.dto.SeasonForm;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
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

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final PlayoffRepository playoffRepository;
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final ScoringService scoringService;
    private final SwissPairingService swissPairingService;
    private final SeasonManagementService seasonManagementService;

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var season = seasonRepository.findById(id).orElseThrow();
        var playoff = playoffRepository.findBySeasonId(id).orElse(null);
        model.addAttribute("season", season);
        model.addAttribute("playoff", playoff);
        model.addAttribute("isSwiss", season.getFormat() == SeasonFormat.SWISS);
        return "admin/season-detail";
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("seasons", seasonRepository.findAll());
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
        var season = seasonRepository.findById(id).orElseThrow();
        var form = new SeasonForm();
        form.setId(season.getId());
        form.setName(season.getName());
        form.setStartDate(season.getStartDate());
        form.setEndDate(season.getEndDate());
        form.setActive(season.isActive());
        form.setFormat(season.getFormat());
        form.setTotalRounds(season.getTotalRounds());
        form.setLegs(season.getLegs());
        model.addAttribute("seasonForm", form);
        model.addAttribute("season", season);
        model.addAttribute("allTeams", teamRepository.findAll());
        model.addAttribute("allCars", carRepository.findAllByOrderByManufacturerAscNameAsc());
        model.addAttribute("allTracks", trackRepository.findAllByOrderByNameAsc());
        addScoringLists(model);
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
        if (form.getId() != null) {
            var existing = seasonRepository.findById(form.getId()).orElseThrow();
            existing.setName(form.getName());
            existing.setStartDate(form.getStartDate());
            existing.setEndDate(form.getEndDate());
            existing.setActive(form.isActive());
            existing.setFormat(form.getFormat());
            existing.setTotalRounds(form.getFormat() == SeasonFormat.SWISS ? form.getTotalRounds() : null);
            existing.setLegs(form.getLegs());
            existing.setRaceScoring(raceScoringRepository.findById(raceScoring).orElseThrow());
            existing.setMatchScoring(matchScoringRepository.findById(matchScoring).orElseThrow());
            seasonRepository.save(existing);
            log.info("Updated season: {}", existing.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + existing.getName());
        } else {
            var season = new Season();
            season.setName(form.getName());
            season.setStartDate(form.getStartDate());
            season.setEndDate(form.getEndDate());
            season.setActive(form.isActive());
            season.setFormat(form.getFormat());
            if (form.getFormat() == SeasonFormat.LEAGUE) {
                season.setTotalRounds(null);
            } else {
                season.setTotalRounds(form.getTotalRounds());
            }
            season.setLegs(form.getLegs());
            season.setRaceScoring(raceScoringRepository.findById(raceScoring).orElseThrow());
            season.setMatchScoring(matchScoringRepository.findById(matchScoring).orElseThrow());
            seasonRepository.save(season);
            log.info("Created season: {}", season.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
        }
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
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
        var season = seasonRepository.findById(id).orElseThrow();
        seasonRepository.delete(season);
        log.info("Deleted season: {}", season.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Season deleted: " + season.getName());
        return "redirect:/admin/seasons";
    }

    @GetMapping("/{id}/swiss")
    public String swissRounds(@PathVariable UUID id, Model model) {
        var season = seasonRepository.findById(id).orElseThrow();

        // Calculate race scores for display
        Map<UUID, int[]> raceScores = new HashMap<>();
        for (var md : season.getMatchdays()) {
            for (var race : md.getRaces()) {
                if (race.isBye()) continue;
                if (race.getHomeScore() != null && race.getAwayScore() != null) {
                    raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
                } else if (!race.getResults().isEmpty()) {
                    int homeTotal = race.getResults().stream()
                            .filter(r -> isHomeTeamDriver(r, race))
                            .mapToInt(RaceResult::getPointsTotal).sum();
                    int awayTotal = race.getResults().stream()
                            .filter(r -> !isHomeTeamDriver(r, race))
                            .mapToInt(RaceResult::getPointsTotal).sum();
                    raceScores.put(race.getId(), new int[]{homeTotal, awayTotal});
                }
            }
        }

        model.addAttribute("season", season);
        model.addAttribute("raceScores", raceScores);
        model.addAttribute("currentRound", swissPairingService.getCurrentRound(id));
        model.addAttribute("canGenerateNext",
                swissPairingService.isCurrentRoundComplete(id)
                && (season.getTotalRounds() == null || season.getMatchdays().size() < season.getTotalRounds()));
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

    private boolean isHomeTeamDriver(RaceResult result, Race race) {
        return scoringService.isDriverInTeam(result, race.getId(), race.getHomeTeam().getId());
    }

    private void addScoringLists(Model model) {
        model.addAttribute("allRaceScorings", raceScoringRepository.findAll());
        model.addAttribute("allMatchScorings", matchScoringRepository.findAll());
    }
}
