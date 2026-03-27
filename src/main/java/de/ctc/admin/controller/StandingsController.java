package de.ctc.admin.controller;

import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.service.DriverRankingService;
import de.ctc.domain.service.StandingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/admin/standings")
@RequiredArgsConstructor
public class StandingsController {

    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final SeasonRepository seasonRepository;

    @GetMapping
    public String standings(@RequestParam(required = false) UUID seasonId, Model model) {
        var season = seasonId != null
                ? seasonRepository.findById(seasonId).orElse(null)
                : seasonRepository.findByActiveTrue().orElse(null);

        if (season != null) {
            model.addAttribute("standings", standingsService.calculateStandings(season.getId()));
            model.addAttribute("driverRanking", driverRankingService.calculateRanking(season.getId()));
            model.addAttribute("selectedSeason", season);
        }

        model.addAttribute("seasons", seasonRepository.findAll());
        return "admin/standings";
    }
}
