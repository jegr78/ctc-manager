package de.ctc.admin.controller;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.SwissPairingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
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
    private final SwissPairingService swissPairingService;

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
        model.addAttribute("season", new Season());
        addScoringLists(model);
        return "admin/season-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var season = seasonRepository.findById(id).orElseThrow();
        model.addAttribute("season", season);
        model.addAttribute("allTeams", teamRepository.findAll());
        model.addAttribute("allCars", carRepository.findAllByOrderByManufacturerAscNameAsc());
        model.addAttribute("allTracks", trackRepository.findAllByOrderByNameAsc());
        addScoringLists(model);
        return "admin/season-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Season season, BindingResult result,
                       @RequestParam UUID raceScoring,
                       @RequestParam UUID matchScoring,
                       RedirectAttributes redirectAttributes, Model model) {
        if (result.hasErrors()) {
            addScoringLists(model);
            return "admin/season-form";
        }
        if (season.getId() != null) {
            var existing = seasonRepository.findById(season.getId()).orElseThrow();
            existing.setName(season.getName());
            existing.setStartDate(season.getStartDate());
            existing.setEndDate(season.getEndDate());
            existing.setActive(season.isActive());
            existing.setFormat(season.getFormat());
            existing.setTotalRounds(season.getFormat() == SeasonFormat.SWISS ? season.getTotalRounds() : null);
            existing.setLegs(season.getLegs());
            existing.setRaceScoring(raceScoringRepository.findById(raceScoring).orElseThrow());
            existing.setMatchScoring(matchScoringRepository.findById(matchScoring).orElseThrow());
            seasonRepository.save(existing);
            log.info("Updated season: {}", existing.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + existing.getName());
        } else {
            if (season.getFormat() == SeasonFormat.LEAGUE) {
                season.setTotalRounds(null);
            }
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
        var season = seasonRepository.findById(id).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();
        if (!season.getTeams().contains(team)) {
            // Auto-add parent team when adding a sub-team
            if (team.isSubTeam() && !season.getTeams().contains(team.getParentTeam())) {
                season.getTeams().add(team.getParentTeam());
                log.info("Auto-added parent team {} to season {}", team.getParentTeam().getShortName(), season.getName());
            }
            season.getTeams().add(team);
            seasonRepository.save(season);
            log.info("Added team {} to season {}", team.getShortName(), season.getName());
        }
        redirectAttributes.addFlashAttribute("successMessage", "Team added: " + team.getShortName());
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/remove-team")
    public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
                             RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();

        // Prevent removing parent team while sub-teams still exist in season
        if (!team.isSubTeam()) {
            boolean hasSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(team.getId()));
            if (hasSubs) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Cannot remove parent team " + team.getShortName() + " — remove its sub-teams first");
                return "redirect:/admin/seasons/" + id + "/edit";
            }
        }

        season.getTeams().removeIf(t -> t.getId().equals(teamId));

        // Auto-remove parent team if no more sub-teams in season
        if (team.isSubTeam()) {
            var parent = team.getParentTeam();
            boolean hasOtherSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()));
            if (!hasOtherSubs) {
                season.getTeams().removeIf(t -> t.getId().equals(parent.getId()));
                log.info("Auto-removed parent team {} from season {} (no sub-teams left)",
                        parent.getShortName(), season.getName());
            }
        }

        seasonRepository.save(season);
        log.info("Removed team {} from season {}", team.getShortName(), season.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Team removed");
        return "redirect:/admin/seasons/" + id + "/edit";
    }

    @PostMapping("/{id}/cars/add")
    public String addCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
                          RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        int added = 0;
        for (UUID carId : carIds) {
            var car = carRepository.findById(carId).orElse(null);
            if (car != null && !season.getCars().contains(car)) {
                season.getCars().add(car);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        redirectAttributes.addFlashAttribute("successMessage", added + " car(s) added to pool");
        return "redirect:/admin/seasons/" + id + "/edit#carPool";
    }

    @PostMapping("/{id}/cars/remove")
    public String removeCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
                             RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        season.getCars().removeIf(c -> carIds.contains(c.getId()));
        seasonRepository.save(season);
        redirectAttributes.addFlashAttribute("successMessage", carIds.size() + " car(s) removed from pool");
        return "redirect:/admin/seasons/" + id + "/edit#carPool";
    }

    @PostMapping("/{id}/tracks/add")
    public String addTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
                            RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        int added = 0;
        for (UUID trackId : trackIds) {
            var track = trackRepository.findById(trackId).orElse(null);
            if (track != null && !season.getTracks().contains(track)) {
                season.getTracks().add(track);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        redirectAttributes.addFlashAttribute("successMessage", added + " track(s) added to pool");
        return "redirect:/admin/seasons/" + id + "/edit#trackPool";
    }

    @PostMapping("/{id}/tracks/remove")
    public String removeTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
                               RedirectAttributes redirectAttributes) {
        var season = seasonRepository.findById(id).orElseThrow();
        season.getTracks().removeIf(t -> trackIds.contains(t.getId()));
        seasonRepository.save(season);
        redirectAttributes.addFlashAttribute("successMessage", trackIds.size() + " track(s) removed from pool");
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
        UUID seasonId = race.getMatchday().getSeason().getId();
        return result.getDriver().getSeasonDrivers().stream()
                .anyMatch(sd -> sd.getSeason().getId().equals(seasonId)
                        && (sd.getTeam().getId().equals(race.getHomeTeam().getId())
                            || sd.getTeam().getId().equals(race.getHomeTeam().getParentOrSelf().getId())));
    }

    private void addScoringLists(Model model) {
        model.addAttribute("allRaceScorings", raceScoringRepository.findAll());
        model.addAttribute("allMatchScorings", matchScoringRepository.findAll());
    }
}
