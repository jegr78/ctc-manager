package org.ctc.admin.controller;

import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.SwissPairingService;
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
    private final SeasonRepository seasonRepository;
    private final SwissPairingService swissPairingService;

    @GetMapping
    public String standings(@RequestParam(required = false) String seasonId, Model model) {
        boolean isAlltime = "alltime".equals(seasonId);

        if (isAlltime) {
            model.addAttribute("standings", standingsService.calculateAlltimeStandings());
            model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
        } else {
            UUID parsedId = null;
            if (seasonId != null && !seasonId.isBlank()) {
                try { parsedId = UUID.fromString(seasonId); }
                catch (IllegalArgumentException e) { log.debug("Invalid season ID: {}", seasonId); }
            }
            var season = parsedId != null
                    ? seasonRepository.findById(parsedId).orElse(null)
                    : seasonRepository.findByActiveTrue().orElse(null);

            if (season != null) {
                if (season.getFormat() == SeasonFormat.SWISS) {
                    var buchholzMap = swissPairingService.calculateBuchholz(season.getId());
                    var standingsList = standingsService.calculateStandings(season.getId());
                    standingsList.forEach(s -> s.setBuchholz(buchholzMap.getOrDefault(s.getTeam().getId(), 0)));
                    standingsList.sort(java.util.Comparator
                            .<StandingsService.TeamStanding, Integer>comparing(StandingsService.TeamStanding::getPoints, java.util.Comparator.reverseOrder())
                            .thenComparing(StandingsService.TeamStanding::getBuchholz, java.util.Comparator.reverseOrder())
                            .thenComparing(StandingsService.TeamStanding::getPointDifference, java.util.Comparator.reverseOrder())
                            .thenComparing(StandingsService.TeamStanding::getPointsFor, java.util.Comparator.reverseOrder()));
                    model.addAttribute("standings", standingsList);
                } else {
                    model.addAttribute("standings", standingsService.calculateStandings(season.getId()));
                }
                model.addAttribute("driverRanking", driverRankingService.calculateRanking(season.getId()));
                model.addAttribute("selectedSeason", season);
            }
        }

        model.addAttribute("isAlltime", isAlltime);
        model.addAttribute("seasons", seasonRepository.findAll());
        model.addAttribute("selectedSeasonId", seasonId);
        return "admin/standings";
    }
}
