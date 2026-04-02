package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.PowerRankingsGraphicService;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/tools/power-rankings")
@RequiredArgsConstructor
public class PowerRankingsController {

    private final PowerRankingsGraphicService powerRankingsGraphicService;
    private final SeasonRepository seasonRepository;

    @GetMapping
    public String index(@RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer number,
                        Model model) {
        // Build season group options
        List<Season> allSeasons = seasonRepository.findAll();
        var seasonGroups = allSeasons.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getYear() + "|" + s.getNumber(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var seasons = entry.getValue();
                    var first = seasons.getFirst();
                    int teamCount = seasons.stream()
                            .mapToInt(s -> s.getSeasonTeams().size())
                            .sum();
                    return new SeasonGroupOption(
                            first.getYear(),
                            first.getNumber(),
                            "Season " + first.getNumber() + " (" + first.getYear() + ") — " + teamCount + " Teams",
                            teamCount
                    );
                })
                .sorted(Comparator.comparingInt(SeasonGroupOption::year).reversed()
                        .thenComparingInt(SeasonGroupOption::number).reversed())
                .toList();

        model.addAttribute("seasonGroups", seasonGroups);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedNumber", number);

        // Load teams if season selected
        if (year != null && number != null) {
            var teams = powerRankingsGraphicService.loadTeamsForSeasonGroup(year, number);
            model.addAttribute("teams", teams);
        }

        return "admin/power-rankings";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam int year,
                                           @RequestParam int number,
                                           @RequestParam(defaultValue = "") String subtitle,
                                           @RequestParam("teamIds") List<UUID> teamIds) {
        try {
            byte[] png = powerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Power Rankings.png\"")
                    .body(png);
        } catch (Exception e) {
            log.error("Failed to generate power rankings graphic", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record SeasonGroupOption(int year, int number, String label, int teamCount) {}
}
