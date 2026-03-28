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
    public String standings(@RequestParam(required = false) String seasonId, Model model) {
        boolean isAlltime = "alltime".equals(seasonId);

        if (isAlltime) {
            model.addAttribute("standings", standingsService.calculateAlltimeStandings());
            model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
            model.addAttribute("isAlltime", true);
        } else {
            UUID parsedId = seasonId != null && !seasonId.isBlank() ? UUID.fromString(seasonId) : null;
            var season = parsedId != null
                    ? seasonRepository.findById(parsedId).orElse(null)
                    : seasonRepository.findByActiveTrue().orElse(null);

            if (season != null) {
                model.addAttribute("standings", standingsService.calculateStandings(season.getId()));
                model.addAttribute("driverRanking", driverRankingService.calculateRanking(season.getId()));
                model.addAttribute("selectedSeason", season);
            }
        }

        model.addAttribute("seasons", seasonRepository.findAll());
        model.addAttribute("selectedSeasonId", seasonId);
        return "admin/standings";
    }
}
