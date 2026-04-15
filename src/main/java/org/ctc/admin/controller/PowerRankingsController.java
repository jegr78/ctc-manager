package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.PowerRankingsGraphicService;
import org.ctc.domain.service.SeasonManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/tools/power-rankings")
@RequiredArgsConstructor
public class PowerRankingsController {

    private final PowerRankingsGraphicService powerRankingsGraphicService;
    private final SeasonManagementService seasonManagementService;

    @GetMapping
    public String index(@RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer number,
                        Model model) {
        model.addAttribute("seasonGroups", seasonManagementService.getSeasonGroupOptions());
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
        } catch (IOException | RuntimeException e) {
            log.error("Failed to generate power rankings graphic", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
