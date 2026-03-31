package de.ctc.admin.controller;

import de.ctc.admin.dto.PlayoffForm;
import de.ctc.admin.dto.SeedForm;
import de.ctc.domain.service.PlayoffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/playoffs")
@RequiredArgsConstructor
public class PlayoffController {

    private final PlayoffService playoffService;

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
    public String save(@ModelAttribute PlayoffForm form, RedirectAttributes redirectAttributes) {
        try {
            var playoff = playoffService.createPlayoff(
                    form.getSeasonId(), form.getName(), form.getNumberOfTeams(),
                    form.getStartDate(), form.getEndDate());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Playoff created: " + playoff.getName());
            return "redirect:/admin/playoffs?seasonId=" + form.getSeasonId();
        } catch (Exception e) {
            log.error("Error creating playoff", e);
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
        var data = playoffService.getSeedingData(id);
        var form = new SeedForm();
        form.setPlayoffId(id);

        model.addAttribute("seedForm", form);
        model.addAttribute("playoff", data.playoff());
        model.addAttribute("bracket", data.bracketView());
        model.addAttribute("firstRound", data.firstRound());
        model.addAttribute("teams", data.teams());
        model.addAttribute("seededTeamIds", data.seededTeamIds());
        return "admin/playoff-seed";
    }

    @PostMapping("/{id}/seed")
    public String saveSeed(@PathVariable UUID id, @ModelAttribute SeedForm form,
                           RedirectAttributes redirectAttributes) {
        playoffService.saveSeed(id, form);
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/admin/playoffs/matchup/" + matchupId;
        }
    }
}
