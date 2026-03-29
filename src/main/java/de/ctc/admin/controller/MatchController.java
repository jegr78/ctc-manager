package de.ctc.admin.controller;

import de.ctc.domain.model.Match;
import de.ctc.domain.model.Race;
import de.ctc.domain.repository.MatchRepository;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final RaceRepository raceRepository;

    @GetMapping("/new")
    public String create(@RequestParam UUID matchdayId, Model model) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        model.addAttribute("matchday", matchday);
        model.addAttribute("teams", matchday.getSeason().getTeams());
        return "admin/match-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam UUID matchdayId,
                       @RequestParam UUID homeTeamId,
                       @RequestParam(required = false) UUID awayTeamId,
                       @RequestParam(defaultValue = "false") boolean bye,
                       RedirectAttributes redirectAttributes) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        var homeTeam = teamRepository.findById(homeTeamId).orElseThrow();
        var awayTeam = bye ? null : (awayTeamId != null ? teamRepository.findById(awayTeamId).orElse(null) : null);

        var match = new Match(matchday, homeTeam, awayTeam);
        match.setBye(bye);
        match = matchRepository.save(match);

        // Auto-create first leg (Race) for the match
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);

        log.info("Created match: {} {} {} on {}",
                homeTeam.getShortName(),
                bye ? "bye" : "vs " + (awayTeam != null ? awayTeam.getShortName() : "?"),
                "", matchday.getLabel());
        redirectAttributes.addFlashAttribute("successMessage",
                "Match created: " + homeTeam.getShortName() + (bye ? " (Bye)" : " vs " + (awayTeam != null ? awayTeam.getShortName() : "?")));
        return "redirect:/admin/matchdays/" + matchdayId;
    }

    @PostMapping("/{id}/add-leg")
    public String addLeg(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var match = matchRepository.findById(id).orElseThrow();
        var matchday = match.getMatchday();
        int maxLegs = matchday.getSeason().getLegs();

        if (match.getRaces().size() >= maxLegs) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Maximum Legs erreicht (" + maxLegs + ")");
            return "redirect:/admin/matchdays/" + matchday.getId();
        }

        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        match.getRaces().add(race);
        raceRepository.save(race);

        log.info("Added leg {} for match {} vs {}",
                match.getRaces().size(), match.getHomeTeam().getShortName(),
                match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");
        redirectAttributes.addFlashAttribute("successMessage", "Leg hinzugefuegt");
        return "redirect:/admin/matchdays/" + matchday.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var match = matchRepository.findById(id).orElseThrow();
        var matchdayId = match.getMatchday().getId();
        matchRepository.delete(match);
        log.info("Deleted match: {} vs {}", match.getHomeTeam().getShortName(),
                match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");
        redirectAttributes.addFlashAttribute("successMessage", "Match geloescht");
        return "redirect:/admin/matchdays/" + matchdayId;
    }
}
