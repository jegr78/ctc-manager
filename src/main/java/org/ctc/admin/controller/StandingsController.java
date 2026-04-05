package org.ctc.admin.controller;

import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.StandingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/standings")
@RequiredArgsConstructor
public class StandingsController {

    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final SeasonManagementService seasonManagementService;

    @GetMapping
    public String standings(@RequestParam(required = false) String seasonId, Model model) {
        boolean isAlltime = "alltime".equals(seasonId);

        if (isAlltime) {
            // TODO: Alltime-Standings muessen cross-season MatchScoring-Aggregation unterstuetzen
            model.addAttribute("standings", java.util.List.of());
            model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
        } else {
            UUID parsedId = null;
            if (seasonId != null && !seasonId.isBlank()) {
                try { parsedId = UUID.fromString(seasonId); }
                catch (IllegalArgumentException e) { log.debug("Invalid season ID: {}", seasonId); }
            }
            var season = parsedId != null
                    ? seasonManagementService.findByIdOptional(parsedId).orElse(null)
                    : seasonManagementService.findActiveSeason().orElse(null);

            if (season != null) {
                if (season.getFormat() == SeasonFormat.SWISS) {
                    model.addAttribute("standings", standingsService.calculateStandingsWithBuchholz(season.getId()));
                } else {
                    model.addAttribute("standings", standingsService.calculateStandings(season.getId()));
                }
                model.addAttribute("driverRanking", driverRankingService.calculateRanking(season.getId()));
                model.addAttribute("selectedSeason", season);
            }
        }

        model.addAttribute("isAlltime", isAlltime);
        model.addAttribute("seasons", seasonManagementService.findAll());
        model.addAttribute("selectedSeasonId", seasonId);
        return "admin/standings";
    }
}
